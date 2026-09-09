# Build context: repository root (shared features and trained artifact live in ml/).
FROM python:3.11-slim AS build
WORKDIR /build
COPY services/ml-model-service/requirements.txt ./
RUN pip wheel --no-cache-dir --wheel-dir /wheels -r requirements.txt

FROM python:3.11-slim AS runtime
WORKDIR /app
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    MODEL_PATH=/app/ml/models/fraud-v1.0.0.pkl
COPY --from=build /wheels /wheels
COPY services/ml-model-service/requirements.txt ./
RUN pip install --no-cache-dir --no-index --find-links=/wheels -r requirements.txt \
    && rm -rf /wheels \
    && groupadd --system ml && useradd --system --gid ml ml
COPY services/ml-model-service/app ./app
COPY ml/__init__.py ml/features.py ./ml/
COPY ml/models/fraud-v1.0.0.pkl ./ml/models/fraud-v1.0.0.pkl
USER ml
EXPOSE 8085
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=5 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8085/health', timeout=2)"
ENTRYPOINT ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8085"]
