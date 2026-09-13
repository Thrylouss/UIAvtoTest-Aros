from __future__ import annotations

from playwright.sync_api import Page

from pages.base_page import BasePage
from pages.login_form import LoginForm


class HomePage(BasePage):
    def __init__(self, page: Page) -> None:
        super().__init__(page)
        self.sign_in_button = page.get_by_role("button", name="Sign in", exact=True)

    def open(self) -> "HomePage":
        self.page.goto("/")
        return self

    def open_sign_in_form(self) -> LoginForm:
        self.sign_in_button.first.click()
        form = LoginForm(self.page)
        form.wait_until_visible()
        return form
