"""Reproducible synthetic training: python -m ml.training.train (from ml-model-service/)."""

import argparse
import json
import logging
import pickle
from decimal import Decimal
from pathlib import Path

import numpy as np
import sklearn
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import average_precision_score, roc_auc_score
from sklearn.model_selection import train_test_split

from ml.features import FEATURE_NAMES, FEATURE_VERSION, PAYMENT_TYPES, to_features

logger = logging.getLogger(__name__)


def generate_dataset(samples: int, seed: int):
    rng = np.random.default_rng(seed)
    features, labels = [], []
    for _ in range(samples):
        average = Decimal(int(rng.integers(100, 100001))) / 100
        if rng.random() < 0.1:
            average = Decimal(0)
        amount = Decimal(int(rng.integers(1, 2000001))) / 100
        hour = int(rng.integers(0, 24))
        count = int(rng.integers(0, 21))
        new, foreign = bool(rng.random() < 0.25), bool(rng.random() < 0.15)
        kind = str(rng.choice(PAYMENT_TYPES))
        payload = dict(amount=amount, hourOfDay=hour, dayOfWeek=int(rng.integers(1, 8)),
                       transactionType=kind, isNewDevice=new, isForeignIp=foreign,
                       transactionsLastHour=count, transactionsLast24Hours=count + int(rng.integers(0, 60)),
                       avgAmountLast30Days=average)
        # No real fraud labels: a noisy synthetic mechanism, never a production claim.
        risk = (-5.5 + 1.5 * new + 2.0 * foreign + 1.0 * (hour < 6)
                + 1.5 * (count >= 8) + 1.0 * (amount >= Decimal(5000))
                + 1.5 * (average > 0 and amount >= 5 * average)
                + 0.4 * (kind == "PIX") + 0.6 * (new and foreign))
        labels.append(int(rng.random() < 1 / (1 + np.exp(-risk))))
        features.append(to_features(payload))
    return np.asarray(features), np.asarray(labels)


def train(output: Path, samples: int = 20000, seed: int = 42):
    if samples < 1000:
        raise ValueError("Use at least 1000 samples")
    x, y = generate_dataset(samples, seed)
    x_train, x_test, y_train, y_test = train_test_split(x, y, test_size=0.2, random_state=seed, stratify=y)
    model = RandomForestClassifier(n_estimators=100, max_depth=10, min_samples_leaf=15,
                                   random_state=seed, n_jobs=1)
    model.fit(x_train, y_train)
    probabilities = model.predict_proba(x_test)[:, 1]
    metadata = dict(modelVersion="v1.0.0", sklearnVersion=sklearn.__version__,
                    featureVersion=FEATURE_VERSION, featureNames=list(FEATURE_NAMES),
                    seed=seed, samples=samples, testSamples=len(y_test),
                    syntheticFraudRate=float(y.mean()),
                    rocAuc=float(roc_auc_score(y_test, probabilities)),
                    averagePrecision=float(average_precision_score(y_test, probabilities)))
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("wb") as target:
        pickle.dump(dict(metadata, model=model), target, protocol=5)
    output.with_suffix(".json").write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    logger.info("Training complete: %s", metadata)
    return metadata


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parents[1] / "models/fraud-v1.0.0.pkl")
    parser.add_argument("--samples", type=int, default=20000)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()
    train(args.output, args.samples, args.seed)
