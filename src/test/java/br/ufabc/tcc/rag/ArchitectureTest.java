package br.ufabc.tcc.rag;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class ArchitectureTest {

    private static final Path CORE_DIR = Paths.get("src/main/java/br/ufabc/tcc/rag/core");

    private static final List<String> FORBIDDEN_PREFIXES = List.of(
            "dev.langchain4j",
            "org.postgresql",
            "org.apache.pdfbox",
            "com.fasterxml",
            "org.apache.commons",
            "java.sql");

    @Test
    void coreDoesNotImportForbiddenLibraries() throws IOException {
        List<Path> javaFiles;
        try (Stream<Path> paths = Files.walk(CORE_DIR)) {
            javaFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
        }

        if (javaFiles.isEmpty()) {
            fail("Nenhum arquivo .java encontrado em " + CORE_DIR);
        }

        List<String> violations = new ArrayList<>();
        for (Path file : javaFiles) {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (String line : lines) {
                String stripped = line.strip();
                if (!stripped.startsWith("import ")) {
                    continue;
                }
                String imported = extractImportedName(stripped);
                if (isForbidden(imported)) {
                    violations.add(file + " -> import " + imported);
                }
            }
        }

        if (!violations.isEmpty()) {
            fail("O core importa bibliotecas proibidas (" + violations.size() + "):\n"
                    + String.join("\n", violations));
        }
    }

    private static String extractImportedName(String importLine) {
        String name = importLine.substring("import ".length()).strip();
        if (name.startsWith("static ")) {
            name = name.substring("static ".length()).strip();
        }
        int semicolon = name.indexOf(';');
        if (semicolon >= 0) {
            name = name.substring(0, semicolon);
        }
        return name.strip();
    }

    private static boolean isForbidden(String importedName) {
        for (String prefix : FORBIDDEN_PREFIXES) {
            if (importedName.equals(prefix) || importedName.startsWith(prefix + ".")) {
                return true;
            }
        }
        return false;
    }
}
