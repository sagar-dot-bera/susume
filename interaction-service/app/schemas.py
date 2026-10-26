import uuid
from datetime import datetime
from typing import Any, Generic, TypeVar

from pydantic import BaseModel, ConfigDict, Field, field_validator
from pydantic.alias_generators import to_camel

from app.models import InteractionType

T = TypeVar("T")


class CamelModel(BaseModel):
    """Serialises as camelCase to match the JSON contract of the Java backend."""

    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, from_attributes=True)


class RecordInteractionRequest(CamelModel):
    external_user_id: str = Field(min_length=1, max_length=255)
    external_item_id: str = Field(min_length=1, max_length=255)
    interaction_type: InteractionType

    @field_validator("interaction_type", mode="before")
    @classmethod
    def normalise_type(cls, value: Any) -> Any:
        # Accept "view", "View", ... like InteractionType.fromString in the backend.
        return value.upper() if isinstance(value, str) else value


class InteractionResponse(CamelModel):
    id: uuid.UUID
    external_user_id: str
    external_item_id: str
    interaction_type: InteractionType
    timestamp: datetime


class InteractionHistoryItem(CamelModel):
    id: uuid.UUID
    external_item_id: str
    interaction_type: InteractionType
    timestamp: datetime


class Page(CamelModel, Generic[T]):
    data: list[T]
    page: int
    limit: int
    total_elements: int
    total_pages: int


class ErrorResponse(BaseModel):
    code: str
    message: str
