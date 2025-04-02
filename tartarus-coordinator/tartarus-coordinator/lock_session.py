from dataclasses import dataclass
from datetime import datetime, timezone
import uuid

from sqlalchemy import Column, DateTime, String
from .base import Base

@dataclass
class LockSession(Base):
    __tablename__ = "lock_sessions"

    id: str = Column(String, primary_key=True, default=lambda: str(uuid.uuid4()))
    public_key: str = Column(String, nullable=False)
    session: str = Column(String, nullable=False)
    last_heard_from = Column(DateTime, default=lambda: datetime.now(timezone.utc))
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))

    def to_dict(self):
        return {
            "id": self.id,
            # "name": self.name,
            "public_key" : self.public_key,
            "session" : self.session,
            "created_at": self.created_at.isoformat() if self.created_at else None,
            "last_heard_from": self.last_heard_from.isoformat() if self.last_heard_from else None,
        }