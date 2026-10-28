package uz.aros.autotests.i18n;

import uz.aros.autotests.config.EnvConfig;

/**
 * Тексты интерфейса сайта на разных языках — «словарь» для локаторов.
 *
 * <p><b>Зачем это нужно.</b> Сайт мультиязычный (/ru, /uz, /en), а Playwright-локаторы ищут элементы по
 * ВИДИМОМУ ТЕКСТУ ({@code getByRole(BUTTON, name = "Вход")}). Если зашить текст прямо в Page Object,
 * тесты будут работать только на одном языке. Поэтому все тексты собраны здесь, а в Page Object пишется
 * {@code Text.SIGN_IN.get()} — и локатор сам подстраивается под язык, выбранный в {@code BASE_URL}
 * (или переменной {@code LOCALE}).
 *
 * <p><b>Как добавить новый текст.</b> Допишите константу: {@code NAME("по-русски", "in English")}.
 * Если английский вариант неизвестен — передайте {@code null}: тогда на /en будет использован русский
 * текст (тест на /en в этом месте упадёт, и вы сразу увидите, какой перевод нужно дописать).
 *
 * <p>Узбекский (/uz) пока не поддержан: для него берётся русский текст. Когда дойдёте до прогона на /uz —
 * добавьте третий параметр (см. {@link #get()}).
 */
public enum Text {

    // --- Форма входа ---
    SIGN_IN("Вход", "Sign in"),
    SIGN_IN_WITH_PASSWORD("Вход с паролем", "Sign in with password"),
    ENTRY_BY_PHONE("Вход по номеру телефона", "Entry by phone number"),
    FORGOT_PASSWORD("Забыли пароль?", "Forgot password?"),
    SIGN_UP("Регистрация", "Sign up"),

    // --- Шапка / навигация ---
    CATALOG("Каталог", "Catalog"),
    MODEL("Модель", "Model"),
    HOME_BREADCRUMB("Главная страница", "Home"),
    SHOW_MORE("Показать ещё", null),

    // --- Разделы главной ---
    SECTION_ON_SALE("Товары со скидкой", "Discounted products"),
    SECTION_NEW("Новинки", "New Products"),
    SECTION_POPULAR("Популярное", "Popular Products"),
    SECTION_ALL("Все", "All"),

    // --- Каталог ---
    ONLY_IN_STOCK("Только в наличии", null),
    SORT("Сортировка", null),
    // ВНИМАНИЕ: на сайте в меню сортировки опечатка — «Сначало» вместо «Сначала» (см. тест CAT-X02).
    // Локаторы написаны под ФАКТИЧЕСКИЙ текст; когда опечатку исправят, поправьте здесь четыре строки.
    SORT_CHEAP_FIRST("Сначало дешевые", null),
    SORT_EXPENSIVE_FIRST("Сначало дорогие", null),
    SORT_OLD_FIRST("Сначало старые", null),
    SORT_NEW_FIRST("Сначало новые", null),
    CLEAR_FILTERS("Очистить все фильтры", null),
    CATALOG_EMPTY("Сейчас нет товаров в наличии", "No available products right now"),
    SHOW_ALL("Показать все", null),

    // --- Карточка товара ---
    QUICK_BUY("Быстрая покупка", null),
    QUICK_VIEW("Быстрый просмотр", null),

    // --- Корзина ---
    CART_EMPTY("Корзина пуста", "Your cart is empty"),
    CART_ITEMS_COUNT("Количество товаров", "Products count"),
    CART_ITEMS_UNIT("шт", "Item"),
    CART_GOODS_PRICE("Цена товаров", null),
    CART_DISCOUNT("Скидка", null),
    CART_TOTAL("Итого:", "Total:"),
    CART_DELETE("Удалить", "Delete"),
    CART_SELECT_ALL("Выбрать все", null),
    CART_CHECKOUT("Оформить", null),
    CART_CONTINUE("Продолжать", null),
    CART_CANCEL("Отменить", null),

    // --- Оформление заказа ---
    CONFIRMATION_TITLE("Подтверждение", null),
    PLACE_ORDER("Оформить заказ", null),
    PAYMENT_METHOD("Способ оплаты", null),
    DELIVERY_METHOD("Способ доставки", null),
    ORDER_COMMENT("Комментарий к заказу", null),

    // --- Профиль ---
    PROFILE("Профиль", null),
    MY_ORDERS("Мои заказы", null),
    MY_REQUESTS("Мои запросы", null),
    POINTS("Баллы", null),
    MY_WALLET("Мой кошелек", null),
    NOTIFICATIONS("Уведомления", null),
    SPECIAL_ORDER("Спецзаказ", null),
    LOGOUT("Выйти из системы", null),
    DELETE_ACCOUNT("Удалить аккаунт", null);

    private final String ru;
    private final String en;

    Text(String ru, String en) {
        this.ru = ru;
        this.en = en;
    }

    /** Текст для текущего языка тестов (см. {@link EnvConfig#locale()}); если перевода нет — русский. */
    public String get() {
        if ("en".equals(EnvConfig.locale()) && en != null) {
            return en;
        }
        return ru;
    }

    @Override
    public String toString() {
        return get();
    }
}
