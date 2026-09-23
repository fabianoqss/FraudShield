import json
import logging
import os
from contextlib import asynccontextmanager
from decimal import Decimal
from pathlib import Path
from time import perf_counter

from fastapi import FastAPI, Request, Response
from fastapi.routing import APIRoute
from prometheus_client import CollectorRegistry, Counter, Histogram, CONTENT_TYPE_LATEST, generate_latest

from app.prediction import PredictionService
from app.schemas import PredictRequest, PredictResponse

logger = logging.getLogger(__name__)
DEFAULT_MODEL_PATH = (Path(__file__).resolve().parents[1] / "ml/models/fraud-v1.0.0.pkl").resolve()


class DecimalRequest(Request):
    async def json(self):
        if not hasattr(self, "_json"):
            self._json = json.loads(await self.body(), parse_float=Decimal, parse_constant=str)
        return self._json


class DecimalRoute(APIRoute):
    def get_route_handler(self):
        handler = super().get_route_handler()

        async def decimal_handler(request: Request):
            return await handler(DecimalRequest(request.scope, request.receive))

        return decimal_handler


def create_app(model_path: Path | None = None) -> FastAPI:
    @asynccontextmanager
    async def lifespan(app: FastAPI):
        try:
            app.state.predictor = PredictionService(
                model_path if model_path is not None else Path(os.getenv("MODEL_PATH", str(DEFAULT_MODEL_PATH)))
            )
        except Exception:
            logger.exception("Failed to load fraud model")
            raise
        yield
        app.state.predictor = None

    app = FastAPI(title="FraudShield ML Model Service", version="1.0.0", lifespan=lifespan)
    app.router.route_class = DecimalRoute
    registry = CollectorRegistry()
    requests = Counter("http_server_requests_total", "HTTP requests", ["method", "uri", "status"], registry=registry)
    latency = Histogram("http_server_requests_seconds", "HTTP request duration", ["method", "uri", "status"], registry=registry)

    @app.middleware("http")
    async def observe(request: Request, call_next):
        started = perf_counter()
        status = 500
        try:
            response = await call_next(request)
            status = response.status_code
            return response
        except Exception:
            logger.exception("HTTP request failed")
            raise
        finally:
            route = request.scope.get("route")
            uri = route.path if route else "unmatched"
            if uri not in ("/health", "/metrics"):
                labels = (request.method if request.method in ("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS") else "OTHER", uri, str(status))
                requests.labels(*labels).inc()
                latency.labels(*labels).observe(perf_counter() - started)

    @app.post("/predict", response_model=PredictResponse)
    def predict(payload: PredictRequest, request: Request):
        return request.app.state.predictor.predict(payload)

    @app.get("/health")
    def health():
        ready = getattr(app.state, "predictor", None) is not None
        return Response(content=json.dumps({"status": "UP" if ready else "DOWN"}),
                        status_code=200 if ready else 503, media_type="application/json")

    @app.get("/metrics", include_in_schema=False)
    def metrics():
        return Response(generate_latest(registry), headers={"Content-Type": CONTENT_TYPE_LATEST})

    return app


app = create_app()
