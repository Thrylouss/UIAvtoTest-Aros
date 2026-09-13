# aros-ui — UI автотесты (Python + Playwright)

Автоматизированные UI-тесты для https://staging.aros.uz, цель которых —
покрывать регрессией ключевые пользовательские сценарии, чтобы баги
находились до релиза, а не клиентами.

## Стек

- Python 3.11+
- `pytest` + `pytest-playwright`
- Page Object Model
- `python-dotenv` для конфигурации окружения

## Архитектура проекта

```
aros-ui/
├── config/
│   └── settings.py        # чтение конфигурации из .env (BASE_URL, креды, headless)
├── pages/                  # Page Object'ы — вся работа с локаторами только здесь
│   ├── base_page.py
│   ├── home_page.py        # главная страница, открытие формы входа
│   └── login_form.py       # форма Sign in: телефон/пароль, переключение режимов
├── utils/
│   └── toast.py            # хелпер для react-toastify уведомлений (успех/ошибка)
├── tests/
│   ├── conftest.py          # фикстуры: settings, base_url, home_page, креды
│   └── auth/
│       └── test_login.py    # тесты авторизации
├── .env.example             # шаблон переменных окружения (без секретов)
├── pytest.ini
├── requirements.txt
└── .github/workflows/playwright.yml  # заготовка CI
```

**Принцип локаторов**: сайт построен на MUI, и большая часть CSS-классов —
это хэши вида `mui-style-5t6wuj`, которые пересобираются при каждом билде
фронтенда. Использовать их как селекторы нельзя — тесты будут ломаться при
любом деплое без изменений в UI. Поэтому везде используются:
- `get_by_role(...)` по роли и видимому тексту кнопки/заголовка,
- стабильные HTML-атрибуты: `input[name="password"]`, `input[type="text"]`,
  `aria-label="toggle password visibility"`,
- CSS-классы сторонних стабильных библиотек (`Toastify__toast--success`),
  но не классы, сгенерированные MUI.

## Установка

```bash
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
playwright install --with-deps chromium

cp .env.example .env
# заполнить BASE_URL / TEST_USER_PHONE / TEST_USER_PASSWORD в .env
```

`.env` в `.gitignore` — реальные учётные данные никогда не попадают в git.

## Запуск тестов

```bash
pytest                              # все тесты
pytest tests/auth                   # только авторизация
pytest -m smoke                     # быстрый набор для каждого коммита
pytest -m regression                # полный набор перед релизом
pytest --headed --slowmo 300        # смотреть браузер вживую при отладке
```

## Тесты авторизации (`tests/auth/test_login.py`)

Реализован вход по паролю (сценарий: кнопка «Sign in» → «Sign in with
password» → телефон + пароль → «Sign in»):

| Тест | Что проверяет |
|---|---|
| `test_successful_login_with_valid_credentials` | happy path: валидные данные → toast «Successful» |
| `test_login_fails_with_wrong_password` | неверный пароль → нет toast успеха, форма остаётся видна |
| `test_login_fails_with_invalid_phone_format` | параметризованный: короткий/длинный/нечисловой номер |
| `test_login_fails_with_empty_password` | пустой пароль не даёт войти |
| `test_login_fails_with_empty_phone_number` | пустой телефон не даёт войти |
| `test_password_is_masked_by_default_and_toggle_reveals_it` | UI: маскирование пароля и переключатель видимости |
| `test_switching_between_phone_and_password_login_modes` | UI: переключение «по паролю» / «по телефону» |

Пока намеренно не покрыто (не входило в первый этап): восстановление
пароля, регистрация (Sign up), вход по одноразовому коду через телефон.
Добавим следующим этапом — `LoginForm` уже содержит локаторы
`forgot_password_button` и `sign_up_button` для этого.

## Важное ограничение текущей версии

Тесты написаны по реальной DOM-разметке, которую вы прислали (кнопка
входа → форма с телефоном → переключение на пароль), и логика Page
Object'ов проверена локально на HTML-заглушке, воспроизводящей эту
структуру — переключение режимов, маскирование пароля, toast успеха/ошибки
отрабатывают корректно.

Но **в этой рабочей среде нет сетевого доступа к `staging.aros.uz`**
(доступ заблокирован политикой организации на уровне egress-прокси — ни
`curl`, ни headless-браузер отсюда до сайта не достучатся), поэтому тесты
ни разу не были запущены против настоящего staging. Перед тем как считать
их готовыми, нужно прогнать `pytest tests/auth` из окружения, где сайт
доступен (ваша машина или CI), и поправить при необходимости:

- точный текст/класс toast с ошибкой (сейчас `test_login_fails_with_*`
  проверяют только *отсутствие* toast об успехе — этого достаточно для
  надёжного теста, но если у ошибки есть свой `Toastify__toast--error`,
  стоит проверять и его через `Toast.expect_error()`, который уже готов);
- поведение поля телефона при полной очистке — если это маска ввода,
  она может не давать очистить `+998` полностью, тогда кейсы с пустым
  телефоном нужно будет адаптировать под реальное поведение маски.

## CI

`.github/workflows/playwright.yml` — заготовка: запускает `tests/auth` на
`workflow_dispatch`, PR и push в `main`. Чтобы она заработала, добавьте в
Settings → Secrets репозитория: `BASE_URL`, `TEST_USER_PHONE`,
`TEST_USER_PASSWORD`.
