package com.test.automation.sdk.tools.pageobject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class JavaIdentifierTest {
    @Test void protectsKeywordsAndLiterals() {
        assertEquals("classElement", JavaIdentifier.toFieldName("class"));
        assertEquals("trueElement", JavaIdentifier.toFieldName("true"));
    }
    @Test void prefixesNamesThatStartWithDigits() {
        assertEquals("element123Submit", JavaIdentifier.toFieldName("123 submit"));
    }
    @Test void rejectsReservedTypeNames() {
        assertThrows(IllegalArgumentException.class,
                () -> JavaIdentifier.requireTypeName("class", "crawler.className"));
    }
}
