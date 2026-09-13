from __future__ import annotations

from playwright.sync_api import Page

from pages.base_page import BasePage


class LoginForm(BasePage):
    """Represents the sign-in form (phone / password login and mode switching).

    Locators are scoped to the <form> containing the "Sign in" heading so they
    never collide with the header button that opens this form, and rely only on
    role/name/type/name-attribute selectors — MUI's generated `mui-style-*`
    classes are unstable across builds and must not be used as selectors.
    """

    def __init__(self, page: Page) -> None:
        super().__init__(page)
        self.root = page.locator("form").filter(
            has=page.get_by_role("heading", name="Sign in")
        ).first

        self.heading = self.root.get_by_role("heading", name="Sign in")
        self.phone_input = self.root.locator('input[type="text"]').first
        self.password_input = self.root.locator('input[name="password"]')
        self.password_visibility_toggle = self.root.get_by_role(
            "button", name="toggle password visibility"
        )
        self.switch_to_password_button = self.root.get_by_role(
            "button", name="Sign in with password"
        )
        self.switch_to_phone_button = self.root.get_by_role(
            "button", name="Entry by phone number"
        )
        self.forgot_password_button = self.root.get_by_role(
            "button", name="Forgot password?"
        )
        self.sign_up_button = self.root.get_by_role("button", name="Sign up")
        self.submit_button = self.root.get_by_role("button", name="Sign in", exact=True)

    def wait_until_visible(self) -> None:
        self.heading.wait_for(state="visible")

    def is_password_mode(self) -> bool:
        return self.password_input.count() > 0 and self.password_input.is_visible()

    def switch_to_password_login(self) -> "LoginForm":
        if not self.is_password_mode():
            self.switch_to_password_button.click()
            self.password_input.wait_for(state="visible")
        return self

    def switch_to_phone_login(self) -> "LoginForm":
        if self.is_password_mode():
            self.switch_to_phone_button.click()
            self.password_input.wait_for(state="hidden")
        return self

    def fill_phone(self, phone: str) -> "LoginForm":
        self.phone_input.click()
        self.phone_input.press("Control+A")
        self.phone_input.press("Delete")
        if phone:
            self.phone_input.type(phone)
        return self

    def fill_password(self, password: str) -> "LoginForm":
        self.password_input.fill(password)
        return self

    def toggle_password_visibility(self) -> "LoginForm":
        self.password_visibility_toggle.click()
        return self

    def submit(self) -> None:
        self.submit_button.click()

    def login_with_password(self, phone: str, password: str) -> None:
        self.switch_to_password_login()
        self.fill_phone(phone)
        self.fill_password(password)
        self.submit()
