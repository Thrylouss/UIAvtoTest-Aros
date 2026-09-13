from __future__ import annotations

import pytest
from playwright.sync_api import expect

from utils.toast import Toast

pytestmark = pytest.mark.auth


@pytest.mark.smoke
def test_successful_login_with_valid_credentials(home_page, valid_credentials):
    form = home_page.open_sign_in_form()

    form.login_with_password(valid_credentials["phone"], valid_credentials["password"])

    Toast(home_page.page).expect_success(text="Successful")


@pytest.mark.regression
def test_login_fails_with_wrong_password(home_page, valid_credentials, wrong_password):
    form = home_page.open_sign_in_form()

    form.login_with_password(valid_credentials["phone"], wrong_password)

    Toast(home_page.page).expect_no_success()
    expect(form.password_input).to_be_visible()


@pytest.mark.regression
@pytest.mark.parametrize(
    "invalid_phone",
    [
        "+998",  # только код страны, без номера
        "+998123",  # слишком короткий номер
        "+998999999999999999",  # слишком длинный номер
        "abc998987882",  # буквы вместо цифр
    ],
)
def test_login_fails_with_invalid_phone_format(home_page, valid_credentials, invalid_phone):
    form = home_page.open_sign_in_form()

    form.login_with_password(invalid_phone, valid_credentials["password"])

    Toast(home_page.page).expect_no_success()


@pytest.mark.regression
def test_login_fails_with_empty_password(home_page, valid_credentials):
    form = home_page.open_sign_in_form()
    form.switch_to_password_login()
    form.fill_phone(valid_credentials["phone"])

    form.submit()

    Toast(home_page.page).expect_no_success()
    expect(form.password_input).to_be_visible()


@pytest.mark.regression
def test_login_fails_with_empty_phone_number(home_page, valid_credentials):
    form = home_page.open_sign_in_form()
    form.switch_to_password_login()
    form.fill_phone("")
    form.fill_password(valid_credentials["password"])

    form.submit()

    Toast(home_page.page).expect_no_success()


@pytest.mark.smoke
def test_password_is_masked_by_default_and_toggle_reveals_it(home_page):
    form = home_page.open_sign_in_form()
    form.switch_to_password_login()
    form.fill_password("Any12345")

    expect(form.password_input).to_have_attribute("type", "password")

    form.toggle_password_visibility()
    expect(form.password_input).to_have_attribute("type", "text")

    form.toggle_password_visibility()
    expect(form.password_input).to_have_attribute("type", "password")


@pytest.mark.smoke
def test_switching_between_phone_and_password_login_modes(home_page):
    form = home_page.open_sign_in_form()
    expect(form.password_input).to_have_count(0)

    form.switch_to_password_login()
    expect(form.password_input).to_be_visible()
    expect(form.forgot_password_button).to_be_visible()

    form.switch_to_phone_login()
    expect(form.password_input).to_have_count(0)
