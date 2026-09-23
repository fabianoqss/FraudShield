import json
from decimal import Decimal
from unittest.mock import patch

import pytest
from fastapi.testclient import TestClient

from app.main import create_app
from ml.features import to_features


@pytest.fixture
def payload():
    return dict(amount=1500.00, hourOfDay=3, dayOfWeek=1, transactionType="PIX",
                isNewDevice=True, isForeignIp=False, transactionsLastHour=5,
                transactionsLast24Hours=12, avgAmountLast30Days=250.00)


@pytest.fixture
def client():
    with TestClient(create_app()) as client:
        yield client


@pytest.mark.parametrize("kind", ["PIX", "CREDIT", "DEBIT"])
def test_predict(client, payload, kind):
    payload["transactionType"] = kind
    response = client.post("/predict", json=payload)
    assert response.status_code == 200
    result = response.json()
    assert set(result) == {"fraudScore", "modelVersion", "inferenceTimeMs"}
    assert 0 <= result["fraudScore"] <= 1
    assert result["modelVersion"] == "v1.0.0"
    assert type(result["inferenceTimeMs"]) is int
    assert result["inferenceTimeMs"] >= 0


@pytest.mark.parametrize("field,value", [
    ("amount", -1), ("amount", 0), ("amount", "NaN"), ("amount", "Infinity"),
    ("amount", "1.00001"), ("amount", "1000000000000000.0000"), ("amount", True),
    ("hourOfDay", 24), ("hourOfDay", 1.5), ("dayOfWeek", 0), ("dayOfWeek", 8),
    ("transactionType", "TRANSFER"), ("isNewDevice", "true"), ("isForeignIp", 1),
    ("transactionsLastHour", -1), ("transactionsLast24Hours", "12"),
    ("avgAmountLast30Days", -1), ("amount", None), ("unexpected", 1),
])
def test_invalid_payload(client, payload, field, value):
    payload[field] = value
    assert client.post("/predict", json=payload).status_code == 422


@pytest.mark.parametrize("body", ["{", "[]", "null", "{}"])
def test_malformed_or_missing(client, body):
    assert client.post("/predict", content=body, headers={"Content-Type": "application/json"}).status_code == 422


@pytest.mark.parametrize("number", ["NaN", "Infinity", "-Infinity", "1e999"])
def test_nonfinite_json_number(client, payload, number):
    body = json.dumps(payload).replace("1500.0", number)
    assert client.post("/predict", content=body, headers={"Content-Type": "application/json"}).status_code == 422


def test_inference_time_covers_feature_extraction_and_model(client, payload):
    events = []
    predictor = client.app.state.predictor
    original = predictor.model.predict_proba

    def clock():
        events.append("clock")
        return 1_000_000_000 if len(events) == 1 else 1_012_900_000

    def features(data):
        events.append("features")
        return to_features(data)

    def model(data):
        events.append("model")
        return original(data)

    with patch("app.prediction.perf_counter_ns", side_effect=clock), \
            patch("app.prediction.to_features", side_effect=features), \
            patch.object(predictor.model, "predict_proba", side_effect=model):
        result = client.post("/predict", json=payload)
    assert result.status_code == 200
    assert result.json()["inferenceTimeMs"] == 12
    assert events == ["clock", "features", "model", "clock"]


def test_money_json_is_parsed_without_float_roundtrip(client, payload):
    body = json.dumps(payload).replace("1500.0", "999999999999999.9999")
    with patch("app.prediction.to_features", wraps=to_features) as features:
        assert client.post("/predict", content=body, headers={"Content-Type": "application/json"}).status_code == 200
    assert features.call_args.args[0]["amount"] == Decimal("999999999999999.9999")


def test_decimal_boundaries(payload):
    payload["avgAmountLast30Days"] = Decimal("250")
    payload["amount"] = Decimal("999.9999")
    below = to_features(payload)
    payload["amount"] = Decimal("1000.0000")
    at = to_features(payload)
    assert at[0] == below[0] + 1
    assert below[-2] == 399
    assert at[-2] == 400


def test_zero_history(client, payload):
    payload["avgAmountLast30Days"] = 0
    payload["dayOfWeek"] = 7
    assert client.post("/predict", json=payload).status_code == 200


def test_health_and_metrics(client, payload):
    assert client.get("/health").json() == {"status": "UP"}
    client.post("/predict", json=payload)
    client.post("/predict", json={})
    client.get("/some/random/path")
    response = client.get("/metrics")
    assert response.status_code == 200
    assert "text/plain" in response.headers["content-type"]
    assert 'http_server_requests_total{method="POST",status="200",uri="/predict"} 1.0' in response.text
    assert 'http_server_requests_total{method="POST",status="422",uri="/predict"} 1.0' in response.text
    assert "http_server_requests_seconds_bucket" in response.text
    assert 'uri="unmatched"' in response.text
    assert "/some/random/path" not in response.text
    assert 'uri="/health"' not in response.text
    assert 'uri="/metrics"' not in response.text


def test_missing_model_fails_startup(tmp_path):
    with pytest.raises(FileNotFoundError):
        with TestClient(create_app(tmp_path / "missing.pkl")):
            pass


def test_model_path_environment(monkeypatch, tmp_path):
    monkeypatch.setenv("MODEL_PATH", str(tmp_path / "absent.pkl"))
    with pytest.raises(FileNotFoundError):
        with TestClient(create_app()):
            pass
