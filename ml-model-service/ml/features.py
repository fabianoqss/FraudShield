"""Shared deterministic feature engineering for training and serving."""

from decimal import Decimal

FEATURE_VERSION = 1
PAYMENT_TYPES = ("PIX", "CREDIT", "DEBIT")
AMOUNT_THRESHOLDS = tuple(map(Decimal, (
    "10", "50", "100", "250", "500", "1000", "2500", "5000",
    "10000", "50000", "100000",
)))
FEATURE_NAMES = (
    "amountBand", "hourOfDay", "dayOfWeek", "isPIX", "isCREDIT", "isDEBIT",
    "isNewDevice", "isForeignIp", "transactionsLastHour",
    "transactionsLast24Hours", "averageBand", "amountToAverageHundredths",
    "hasNoHistory",
)


def to_features(payload: dict) -> list[int]:
    # Monetary values remain Decimal. The model receives bounded integers only,
    # exactly representable even in RandomForest's internal float32 arrays.
    amount = Decimal(payload["amount"])
    average = Decimal(payload["avgAmountLast30Days"])
    ratio = int(min(amount * 100 / average, Decimal(100000))) if average else 0
    return [
        sum(amount >= threshold for threshold in AMOUNT_THRESHOLDS),
        payload["hourOfDay"], payload["dayOfWeek"],
        *(int(payload["transactionType"] == kind) for kind in PAYMENT_TYPES),
        int(payload["isNewDevice"]), int(payload["isForeignIp"]),
        min(payload["transactionsLastHour"], 100000),
        min(payload["transactionsLast24Hours"], 100000),
        sum(average >= threshold for threshold in AMOUNT_THRESHOLDS),
        ratio, int(average == 0),
    ]
