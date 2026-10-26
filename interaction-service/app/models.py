"""
ORM mappings for the tables this service touches.

The schema is owned by the Spring Boot backend's Flyway migrations
(backend/src/main/resources/db/migration), so these models map only the
columns this service needs and are never used to create or alter tables
in Postgres.
"""
import enum
import uuid
from datetime import datetime, timezone

from sqlalchemy import DateTime, Enum, String, Uuid
from sqlalchemy.orm import Mapped, mapped_column

from app.db import Base


def utc_now() -> datetime:
    # The `timestamp` columns are TIMESTAMP WITHOUT TIME ZONE holding UTC values.
    return datetime.now(timezone.utc).replace(tzinfo=None)


class InteractionType(str, enum.Enum):
    VIEW = "VIEW"
    CLICK = "CLICK"
    LIKE = "LIKE"
    SHARE = "SHARE"
    CART = "CART"
    PURCHASE = "PURCHASE"


class ApiKey(Base):
    __tablename__ = "api_key"

    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True, default=uuid.uuid4)
    tenant_id: Mapped[uuid.UUID] = mapped_column(Uuid, nullable=False)
    # SHA-256 hex digest of the raw key (see CryptoUtil.hashAPIKey in the backend).
    key: Mapped[str] = mapped_column(String(255), nullable=False, unique=True)


class Item(Base):
    __tablename__ = "items"

    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True, default=uuid.uuid4)
    tenant_id: Mapped[uuid.UUID] = mapped_column(Uuid, nullable=False)
    external_item_id: Mapped[str] = mapped_column(String(255), nullable=False)
    status: Mapped[str] = mapped_column(String(20), nullable=False, default="ACTIVE")


class Interaction(Base):
    __tablename__ = "interactions"

    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True, default=uuid.uuid4)
    tenant_id: Mapped[uuid.UUID] = mapped_column(Uuid, nullable=False)
    external_user_id: Mapped[str] = mapped_column(String(255), nullable=False)
    external_item_id: Mapped[str] = mapped_column(String(255), nullable=False)
    interaction_type: Mapped[InteractionType] = mapped_column(
        Enum(InteractionType, native_enum=False, length=20), nullable=False
    )
    timestamp: Mapped[datetime] = mapped_column(DateTime, nullable=False, default=utc_now)
