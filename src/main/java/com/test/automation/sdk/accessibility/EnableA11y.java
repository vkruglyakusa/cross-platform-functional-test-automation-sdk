package com.test.automation.sdk.accessibility;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Convenience composed annotation for JUnit 5 tests.
 * Equivalent to {@code @ExtendWith(A11yExtension.class)}.
 *
 * <p>This SDK is primarily TestNG-based (see {@link A11yTestNGListener}, which
 * is auto-registered via {@code META-INF/services} for every TestNG suite).
 * JUnit 5 support is deliberately <b>opt-in only</b> — the SDK does
 * <b>not</b> ship a {@code META-INF/services/org.junit.jupiter.api.extension.Extension}
 * entry or a bundled {@code junit-platform.properties} enabling
 * autodetection. Doing so would silently activate scanning for every JUnit 5
 * consumer project on the classpath and could conflict with a consumer's own
 * {@code junit-platform.properties} (the JUnit Platform only reads one). Put
 * this annotation on the classes where you actually want scanning.</p>
 *
 * <h3>Minimal usage — one annotation, zero per-test code</h3>
 * <pre>
 * {@literal @}EnableA11y
 * class CheckoutFlowTest {
 *
 *     {@literal @}A11yDriver
 *     static WebDriver driver;
 *
 *     {@literal @}BeforeAll
 *     static void setup() { driver = new ChromeDriver(); }
 *
 *     {@literal @}Test
 *     void cartPageIsAccessible() {
 *         driver.get("https://example.com/cart");
 *         // ← scan happens automatically after this test, no extra line needed
 *     }
 * }
 * </pre>
 *
 * <p>Enable or disable scanning entirely via flag — no code change needed:</p>
 * <pre>
 * # accessibility.properties
 * accessibility.checking.enabled=true
 * accessibility.session.noise.threshold=SERIOUS
 * </pre>
 *
 * <p>Inheritable — put it on a base test class to activate for all subclasses.</p>
 *
 * @see A11yExtension
 * @see A11yDriver
 * @see A11ySessionManager
 */
@Documented
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(A11yExtension.class)
public @interface EnableA11y {
}
