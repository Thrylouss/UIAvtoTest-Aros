package uz.aros.autotests.annotations;

import org.junit.jupiter.api.Tag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Приоритет тест-кейса из Excel (P2). «Составная» аннотация: вместо {@code @Tag("P2")} достаточно
 * писать {@code @P2}. Теги нужны, чтобы запускать наборы: {@code mvn test -Dgroups=P2} или команда бота {@code /p2}.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Tag("P2")
public @interface P2 {
}
