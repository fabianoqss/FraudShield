import logging
import pickle
from pathlib import Path
from time import perf_counter_ns

import sklearn

from ml.features import FEATURE_NAMES, FEATURE_VERSION, to_features
from app.schemas import PredictRequest, PredictResponse

logger = logging.getLogger(__name__)


class PredictionService:
    def __init__(self, path: Path):
        # Only load trusted deployment artifacts: pickle can execute code.
        with path.open("rb") as source:
            artifact = pickle.load(source)
        if (artifact["sklearnVersion"] != sklearn.__version__
                or artifact["featureVersion"] != FEATURE_VERSION
                or artifact["featureNames"] != list(FEATURE_NAMES)):
            raise ValueError("Incompatible model artifact; retrain with the pinned dependencies")
        self.model = artifact["model"]
        self.version = artifact["modelVersion"]
        if list(self.model.classes_) != [0, 1] or self.model.n_features_in_ != len(FEATURE_NAMES):
            raise ValueError("Invalid model classes or feature count")
        logger.info("Loaded fraud model %s", self.version)

    def predict(self, request: PredictRequest) -> PredictResponse:
        started = perf_counter_ns()
        features = to_features(request.model_dump())
        score = float(self.model.predict_proba([features])[0][1])
        elapsed_ms = (perf_counter_ns() - started) // 1_000_000
        return PredictResponse(fraudScore=score, modelVersion=self.version, inferenceTimeMs=elapsed_ms)
