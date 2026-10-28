package uz.aros.autotests.annotations;

import org.junit.jupiter.api.Tag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Дымовой тест (смоук): самые важные проверки, их прогоняют первыми — команда бота {@code /smoke}. */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Tag("smoke")
public @interface Smoke {
}
