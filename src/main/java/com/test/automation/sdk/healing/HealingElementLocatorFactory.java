package com.test.automation.sdk.healing;

import java.lang.reflect.Field;

import org.openqa.selenium.SearchContext;
import org.openqa.selenium.support.pagefactory.Annotations;
import org.openqa.selenium.support.pagefactory.ElementLocator;
import org.openqa.selenium.support.pagefactory.ElementLocatorFactory;

/**
 * {@link ElementLocatorFactory} that produces a {@link HealingElementLocator} for
 * every {@code @FindBy}-annotated (or default id/name) page-object field, instead
 * of Selenium's stock {@code DefaultElementLocator}.
 */
public class HealingElementLocatorFactory implements ElementLocatorFactory {

    private final SearchContext context;

    public HealingElementLocatorFactory(SearchContext context) {
        this.context = context;
    }

    @Override
    public ElementLocator createLocator(Field field) {
        Annotations annotations = new Annotations(field);
        String description = field.getDeclaringClass().getSimpleName() + "." + field.getName();
        return new HealingElementLocator(context, annotations.buildBy(), description);
    }
}
