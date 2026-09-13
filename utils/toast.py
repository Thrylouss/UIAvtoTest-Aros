from __future__ import annotations

from playwright.sync_api import Locator, Page, expect


class Toast:
    """Wraps the react-toastify notifications shown after auth actions.

    Toastify's own `Toastify__toast--success` / `--error` classes are library
    defaults (not MUI's hashed classes), so they are stable to select on.
    """

    SUCCESS_SELECTOR = ".Toastify__toast--success"
    ERROR_SELECTOR = ".Toastify__toast--error"

    def __init__(self, page: Page) -> None:
        self.page = page

    def expect_success(self, text: str | None = None, timeout: int = 8000) -> Locator:
        toast = self.page.locator(self.SUCCESS_SELECTOR).first
        expect(toast).to_be_visible(timeout=timeout)
        if text:
            expect(toast).to_contain_text(text)
        return toast

    def expect_error(self, text: str | None = None, timeout: int = 8000) -> Locator:
        toast = self.page.locator(self.ERROR_SELECTOR).first
        expect(toast).to_be_visible(timeout=timeout)
        if text:
            expect(toast).to_contain_text(text)
        return toast

    def expect_no_success(self, timeout: int = 5000) -> None:
        expect(self.page.locator(self.SUCCESS_SELECTOR)).to_have_count(0, timeout=timeout)
