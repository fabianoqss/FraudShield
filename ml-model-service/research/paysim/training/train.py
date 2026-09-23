import json
from pathlib import Path

import joblib
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import pandas as pd
import seaborn as sns
from sklearn.compose import ColumnTransformer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (
    average_precision_score,
    confusion_matrix,
    f1_score,
    precision_recall_curve,
    precision_score,
    recall_score,
    roc_auc_score,
    roc_curve,
)
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder, StandardScaler


RANDOM_STATE = 42
TEST_SIZE = 0.20
MODEL_VERSION = "baseline_v1"

PROJECT_DIR = Path(__file__).resolve().parents[1]
DATASET_PATH = PROJECT_DIR / "data" / "paysim.csv"
MODELS_DIR = PROJECT_DIR / "models"
REPORTS_DIR = PROJECT_DIR / "reports" / MODEL_VERSION

MODEL_PATH = MODELS_DIR / f"fraud_pipeline_{MODEL_VERSION}.joblib"
METRICS_PATH = REPORTS_DIR / "metrics.json"

NUMERIC_FEATURES = [
    "step",
    "amount",
    "oldbalanceOrg",
    "newbalanceOrig",
    "oldbalanceDest",
    "newbalanceDest",
]

CATEGORICAL_FEATURES = ["type"]
FEATURES = NUMERIC_FEATURES + CATEGORICAL_FEATURES
TARGET = "isFraud"


def load_data() -> pd.DataFrame:
    """Carrega todas as linhas e somente as colunas usadas pelo baseline."""
    if not DATASET_PATH.exists():
        raise FileNotFoundError(f"Dataset não encontrado: {DATASET_PATH}")

    print(f"Carregando dataset completo: {DATASET_PATH}")

    data_types = {
        "step": "int16",
        "amount": "float32",
        "oldbalanceOrg": "float32",
        "newbalanceOrig": "float32",
        "oldbalanceDest": "float32",
        "newbalanceDest": "float32",
        "type": "category",
        "isFraud": "int8",
    }

    dataframe = pd.read_csv(
        DATASET_PATH,
        usecols=FEATURES + [TARGET],
        dtype=data_types,
    )

    fraud_count = int(dataframe[TARGET].sum())
    fraud_rate = float(dataframe[TARGET].mean() * 100)

    print(f"Dataset carregado: {len(dataframe):,} transações")
    print(f"Fraudes: {fraud_count:,} ({fraud_rate:.6f}%)")

    return dataframe


def build_pipeline() -> Pipeline:
    """Monta o pré-processamento e o modelo como uma única unidade."""
    preprocessor = ColumnTransformer(
        transformers=[
            ("numeric", StandardScaler(), NUMERIC_FEATURES),
            (
                "categorical",
                OneHotEncoder(handle_unknown="ignore"),
                CATEGORICAL_FEATURES,
            ),
        ]
    )

    model = LogisticRegression(
        max_iter=1000,
        random_state=RANDOM_STATE,
    )

    return Pipeline(
        steps=[
            ("preprocessor", preprocessor),
            ("model", model),
        ]
    )


def save_confusion_matrix(y_test: pd.Series, predictions) -> list[list[int]]:
    matrix = confusion_matrix(y_test, predictions)

    plt.figure(figsize=(7, 5))
    sns.heatmap(
        matrix,
        annot=True,
        fmt="d",
        cmap="Blues",
        xticklabels=["Normal", "Fraude"],
        yticklabels=["Normal", "Fraude"],
    )
    plt.title("Matriz de confusão — baseline v1")
    plt.xlabel("Previsão")
    plt.ylabel("Valor real")
    plt.tight_layout()
    plt.savefig(REPORTS_DIR / "confusion_matrix.png", dpi=150)
    plt.close()

    return matrix.tolist()


def save_precision_recall_curve(y_test: pd.Series, probabilities) -> None:
    precision, recall, _ = precision_recall_curve(y_test, probabilities)

    plt.figure(figsize=(7, 5))
    plt.plot(recall, precision)
    plt.title("Curva Precision-Recall — baseline v1")
    plt.xlabel("Recall")
    plt.ylabel("Precision")
    plt.grid(alpha=0.3)
    plt.tight_layout()
    plt.savefig(REPORTS_DIR / "precision_recall_curve.png", dpi=150)
    plt.close()


def save_roc_curve(y_test: pd.Series, probabilities) -> None:
    false_positive_rate, true_positive_rate, _ = roc_curve(y_test, probabilities)

    plt.figure(figsize=(7, 5))
    plt.plot(false_positive_rate, true_positive_rate, label="Modelo")
    plt.plot([0, 1], [0, 1], linestyle="--", label="Aleatório")
    plt.title("Curva ROC — baseline v1")
    plt.xlabel("Taxa de falsos positivos")
    plt.ylabel("Taxa de verdadeiros positivos")
    plt.legend()
    plt.grid(alpha=0.3)
    plt.tight_layout()
    plt.savefig(REPORTS_DIR / "roc_curve.png", dpi=150)
    plt.close()


def evaluate_model(
    pipeline: Pipeline,
    x_test: pd.DataFrame,
    y_test: pd.Series,
) -> dict:
    """Calcula métricas adequadas para um dataset desbalanceado."""
    predictions = pipeline.predict(x_test)
    probabilities = pipeline.predict_proba(x_test)[:, 1]

    matrix = save_confusion_matrix(y_test, predictions)
    save_precision_recall_curve(y_test, probabilities)
    save_roc_curve(y_test, probabilities)

    return {
        "model_version": MODEL_VERSION,
        "decision_threshold": 0.5,
        "precision": float(precision_score(y_test, predictions, zero_division=0)),
        "recall": float(recall_score(y_test, predictions, zero_division=0)),
        "f1_score": float(f1_score(y_test, predictions, zero_division=0)),
        "average_precision": float(
            average_precision_score(y_test, probabilities)
        ),
        "roc_auc": float(roc_auc_score(y_test, probabilities)),
        "confusion_matrix": matrix,
    }


def main() -> None:
    MODELS_DIR.mkdir(parents=True, exist_ok=True)
    REPORTS_DIR.mkdir(parents=True, exist_ok=True)

    dataframe = load_data()
    x = dataframe[FEATURES]
    y = dataframe[TARGET]

    print("Separando os conjuntos de treino e teste...")
    x_train, x_test, y_train, y_test = train_test_split(
        x,
        y,
        test_size=TEST_SIZE,
        random_state=RANDOM_STATE,
        stratify=y,
    )

    print(f"Treino: {len(x_train):,} transações")
    print(f"Teste: {len(x_test):,} transações")

    pipeline = build_pipeline()

    print("Treinando LogisticRegression...")
    pipeline.fit(x_train, y_train)
    print("Treinamento concluído.")

    print("Avaliando o baseline...")
    metrics = evaluate_model(pipeline, x_test, y_test)
    metrics["training_rows"] = len(x_train)
    metrics["test_rows"] = len(x_test)

    joblib.dump(pipeline, MODEL_PATH)

    with METRICS_PATH.open("w", encoding="utf-8") as metrics_file:
        json.dump(metrics, metrics_file, indent=2, ensure_ascii=False)

    print("\nMétricas do baseline:")
    print(json.dumps(metrics, indent=2, ensure_ascii=False))
    print(f"\nPipeline salvo em: {MODEL_PATH}")
    print(f"Relatórios salvos em: {REPORTS_DIR}")


if __name__ == "__main__":
    main()
