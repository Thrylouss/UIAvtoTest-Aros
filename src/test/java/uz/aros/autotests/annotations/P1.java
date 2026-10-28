package uz.aros.autotests.annotations;

import org.junit.jupiter.api.Tag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Приоритет тест-кейса из Excel (P1). «Составная» аннотация: вместо {@code @Tag("P1")} достаточно
 * писать {@code @P1}. Теги нужны, чтобы запускать наборы: {@code mvn test -Dgroups=P1} или команда бота {@code /p1}.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Tag("P1")
public @interface P1 {
}
