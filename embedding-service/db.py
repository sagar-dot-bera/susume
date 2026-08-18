import os
import uuid
import logging
from datetime import datetime
from typing import List, Optional, Dict, Any
from sqlalchemy import create_engine, select, update, String, DateTime, ARRAY, Float
from sqlalchemy.dialects.postgresql import UUID, JSONB
from sqlalchemy.orm import sessionmaker, DeclarativeBase, Mapped, mapped_column

logger = logging.getLogger(__name__)


class Base(DeclarativeBase):
    pass


class Item(Base):
    __tablename__ = "items"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    tenant_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), nullable=False)
    external_item_id: Mapped[str] = mapped_column(String(255), nullable=False)
    metadata_: Mapped[Dict[str, Any]] = mapped_column("metadata", JSONB, nullable=False)
    embedding: Mapped[Optional[List[float]]] = mapped_column(ARRAY(Float), nullable=True)
    status: Mapped[str] = mapped_column(String(20), default="ACTIVE", nullable=False)
    embedding_status: Mapped[str] = mapped_column(String(20), default="PENDING", nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow, nullable=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow, nullable=False)


def get_db_url() -> str:
    db_name = os.getenv("POSTGRES_DB", "susume")
    db_user = os.getenv("POSTGRES_USER", "postgres")
    db_pass = os.getenv("POSTGRES_PASSWORD", "postgres")
    db_host = os.getenv("POSTGRES_HOST", "localhost")
    db_port = os.getenv("POSTGRES_PORT", "5433")
    return f"postgresql://{db_user}:{db_pass}@{db_host}:{db_port}/{db_name}"


def fetch_pending_embedding_items(limit: int = 50) -> List[Item]:
    """Fetch catalog items with PENDING embedding status via SQLAlchemy."""
    engine = create_engine(get_db_url(), pool_pre_ping=True)
    SessionLocal = sessionmaker(bind=engine)
    with SessionLocal() as session:
        stmt = select(Item).where(Item.embedding_status == "PENDING").limit(limit)
        items = session.scalars(stmt).all()
        return list(items)


def update_item_embedding(item_id: uuid.UUID, embedding_vector: List[float]) -> bool:
    """Update item embedding vector and status via SQLAlchemy."""
    engine = create_engine(get_db_url(), pool_pre_ping=True)
    SessionLocal = sessionmaker(bind=engine)
    with SessionLocal() as session:
        stmt = (
            update(Item)
            .where(Item.id == item_id)
            .values(
                embedding=embedding_vector,
                embedding_status="COMPLETED",
                updated_at=datetime.utcnow()
            )
        )
        session.execute(stmt)
        session.commit()
        return True
