from __future__ import annotations

import os
from dataclasses import dataclass

from dotenv import load_dotenv

load_dotenv()


@dataclass(frozen=True)
class Settings:
    base_url: str
    headless: bool
    test_user_phone: str | None
    test_user_password: str | None
    test_user_wrong_password: str


def get_settings() -> Settings:
    return Settings(
        base_url=os.getenv("BASE_URL", "https://staging.aros.uz/en"),
        headless=os.getenv("HEADLESS", "true").lower() != "false",
        test_user_phone=os.getenv("TEST_USER_PHONE"),
        test_user_password=os.getenv("TEST_USER_PASSWORD"),
        test_user_wrong_password=os.getenv("TEST_USER_WRONG_PASSWORD", "WrongPass123"),
    )
