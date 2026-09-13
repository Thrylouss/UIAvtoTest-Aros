from __future__ import annotations

import pytest

from config.settings import get_settings
from pages.home_page import HomePage


@pytest.fixture(scope="session")
def settings():
    return get_settings()


@pytest.fixture(scope="session")
def base_url(settings) -> str:
    return settings.base_url


@pytest.fixture(scope="session")
def browser_type_launch_args(browser_type_launch_args, settings):
    return {**browser_type_launch_args, "headless": settings.headless}


@pytest.fixture
def valid_credentials(settings) -> dict[str, str]:
    if not settings.test_user_phone or not settings.test_user_password:
        pytest.skip(
            "TEST_USER_PHONE / TEST_USER_PASSWORD are not set — "
            "add them to your .env to run credential-based tests"
        )
    return {"phone": settings.test_user_phone, "password": settings.test_user_password}


@pytest.fixture
def wrong_password(settings) -> str:
    return settings.test_user_wrong_password


@pytest.fixture
def home_page(page) -> HomePage:
    return HomePage(page).open()
