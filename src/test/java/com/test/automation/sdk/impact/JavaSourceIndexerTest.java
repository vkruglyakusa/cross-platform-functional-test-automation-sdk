package com.test.automation.sdk.impact;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaSourceIndexerTest {

    @Test
    void indexesClassNameFromPackageAndFileName(@TempDir Path root) throws IOException {
        Path file = writeJava(root, "com/example/app", "Widget.java",
            "package com.example.app;\n" +
            "public class Widget {\n" +
            "}\n");

        JavaSourceIndexer indexer = new JavaSourceIndexer();
        indexer.indexSourceRoot(root);

        assertEquals("com.example.app.Widget", indexer.classNameForFile(file));
        assertTrue(indexer.isIndexed("com.example.app.Widget"));
    }

    @Test
    void detectsReferenceBySimpleNameToken(@TempDir Path root) throws IOException {
        writeJava(root, "com/example/app", "Widget.java",
            "package com.example.app;\n" +
            "public class Widget {\n" +
            "}\n");
        writeJava(root, "com/example/app", "WidgetFactory.java",
            "package com.example.app;\n" +
            "public class WidgetFactory {\n" +
            "    Widget create() { return new Widget(); }\n" +
            "}\n");

        JavaSourceIndexer indexer = new JavaSourceIndexer();
        indexer.indexSourceRoot(root);
        indexer.computeReferences();

        Set<String> references = indexer.referencesOf("com.example.app.WidgetFactory");
        assertTrue(references.contains("com.example.app.Widget"));
    }

    @Test
    void unrelatedClassesDoNotReferenceEachOther(@TempDir Path root) throws IOException {
        writeJava(root, "com/example/app", "Widget.java",
            "package com.example.app;\n" +
            "public class Widget {\n" +
            "}\n");
        writeJava(root, "com/example/app", "Gadget.java",
            "package com.example.app;\n" +
            "public class Gadget {\n" +
            "}\n");

        JavaSourceIndexer indexer = new JavaSourceIndexer();
        indexer.indexSourceRoot(root);
        indexer.computeReferences();

        assertTrue(indexer.referencesOf("com.example.app.Gadget").isEmpty());
    }

    @Test
    void classNameForFileReturnsNullWhenNotIndexed(@TempDir Path root) {
        JavaSourceIndexer indexer = new JavaSourceIndexer();
        assertNull(indexer.classNameForFile(root.resolve("NotIndexed.java")));
    }

    @Test
    void fileForReturnsBackingFile(@TempDir Path root) throws IOException {
        Path file = writeJava(root, "com/example/app", "Widget.java",
            "package com.example.app;\n" +
            "public class Widget {\n" +
            "}\n");

        JavaSourceIndexer indexer = new JavaSourceIndexer();
        indexer.indexSourceRoot(root);

        assertNotNull(indexer.fileFor("com.example.app.Widget"));
        assertEquals(file.toAbsolutePath().normalize(), indexer.fileFor("com.example.app.Widget"));
    }

    private static Path writeJava(Path root, String packagePath, String fileName, String content) throws IOException {
        Path dir = root.resolve(packagePath);
        Files.createDirectories(dir);
        Path file = dir.resolve(fileName);
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        return file;
    }
}
