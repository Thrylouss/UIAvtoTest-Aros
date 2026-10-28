package uz.aros.autotests.annotations;

import org.junit.jupiter.api.Tag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Приоритет тест-кейса из Excel (P3). «Составная» аннотация: вместо {@code @Tag("P3")} достаточно
 * писать {@code @P3}. Теги нужны, чтобы запускать наборы: {@code mvn test -Dgroups=P3} или команда бота {@code /p3}.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Tag("P3")
public @interface P3 {
}
