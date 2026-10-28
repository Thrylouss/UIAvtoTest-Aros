package uz.aros.autotests.annotations;

import org.junit.jupiter.api.Tag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Тест создаёт РЕАЛЬНЫЙ заказ на staging (меняет данные, влияет на остатки, баллы, долг).
 * Такие тесты исключаются из безопасного набора: {@code /safe} в боте = {@code -Dgroups='!creates-order'}.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Tag("creates-order")
public @interface CreatesOrder {
}
