from __future__ import annotations

from playwright.sync_api import Page


class BasePage:
    def __init__(self, page: Page) -> None:
        self.page = page
