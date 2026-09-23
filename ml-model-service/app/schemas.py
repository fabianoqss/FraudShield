from decimal import Decimal
from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field, StrictBool

Money = Annotated[Decimal, Field(ge=0, max_digits=19, decimal_places=4, allow_inf_nan=False)]
Count = Annotated[int, Field(strict=True, ge=0, le=9223372036854775807)]


class PredictRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    amount: Money = Field(gt=0)
    hourOfDay: Annotated[int, Field(strict=True, ge=0, le=23)]
    dayOfWeek: Annotated[int, Field(strict=True, ge=1, le=7)]
    transactionType: Literal["PIX", "CREDIT", "DEBIT"]
    isNewDevice: StrictBool
    isForeignIp: StrictBool
    transactionsLastHour: Count
    transactionsLast24Hours: Count
    avgAmountLast30Days: Money


class PredictResponse(BaseModel):
    fraudScore: float = Field(ge=0, le=1, allow_inf_nan=False)
    modelVersion: str
    inferenceTimeMs: int = Field(ge=0)
