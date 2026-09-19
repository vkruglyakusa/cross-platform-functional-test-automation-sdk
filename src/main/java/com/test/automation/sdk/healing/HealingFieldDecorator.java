package com.test.automation.sdk.healing;

import org.openqa.selenium.SearchContext;
import org.openqa.selenium.support.pagefactory.DefaultFieldDecorator;

/**
 * Drop-in replacement for Selenium's {@code DefaultFieldDecorator} that wires every
 * page-object field to a {@link HealingElementLocator} instead of the stock
 * {@code DefaultElementLocator}.
 *
 * <p>Used via {@code TestBase.initElements(driver, this)} in a page object's
 * constructor, in place of {@code PageFactory.initElements(driver, this)}. Fully
 * opt-in -- existing page objects that still call
 * {@code PageFactory.initElements(driver, this)} directly are completely
 * unaffected and keep their current (non-healing) behavior.
 */
public class HealingFieldDecorator extends DefaultFieldDecorator {

    public HealingFieldDecorator(SearchContext context) {
        super(new HealingElementLocatorFactory(context));
    }
}
