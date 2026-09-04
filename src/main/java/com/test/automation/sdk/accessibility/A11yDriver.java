package com.test.automation.sdk.accessibility;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the {@link org.openqa.selenium.WebDriver} field that
 * {@link A11yExtension} (JUnit 5) and {@link A11yTestNGListener} (TestNG)
 * should use for automatic accessibility scanning.
 *
 * <p>Place on any instance or static field of type {@code WebDriver}:</p>
 * <pre>
 * {@literal @}A11yDriver
 * private WebDriver driver;
 * </pre>
 *
 * <p>If no field carries this annotation, the extension falls back to the
 * first {@code WebDriver}-typed field it finds via reflection.</p>
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface A11yDriver {
}

