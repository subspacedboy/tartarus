from dataclasses import dataclass
from datetime import datetime, timezone
import uuid

from sqlalchemy import Column, DateTime, String
from .base import Base

@dataclass
class KeyRecord(Base):
    __tablename__ = "key_records"

    id: str = Column(String, primary_key=True, default=lambda: str(uuid.uuid4()))
    public_key: str = Column(String, nullable=False)
    created_at = Column(DateTime, default=datetime.now(timezone.utc))