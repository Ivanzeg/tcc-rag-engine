package br.ufabc.tcc.rag.app;

import br.ufabc.tcc.rag.adapters.Sha256;
import br.ufabc.tcc.rag.core.domain.Corpus;
import br.ufabc.tcc.rag.core.domain.Unit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class CorpusLoader {

    private final ObjectMapper mapper = new ObjectMapper();

    public Corpus load(Path corpusDir) {
        String text = read(corpusDir.resolve("lgpd.txt"));
        verifySeal(corpusDir.resolve("stats.json"), text);
        List<Unit> units = readUnits(corpusDir.resolve("units.jsonl"));
        for (Unit unit : units) {
            if (!text.substring(unit.start(), unit.end()).equals(unit.text())) {
                throw new IllegalStateException("A unidade " + unit.id()
                        + " não corresponde ao trecho [" + unit.start() + ", " + unit.end() + ") de lgpd.txt");
            }
        }
        return new Corpus(units, text);
    }

    private void verifySeal(Path statsFile, String text) {
        try {
            JsonNode stats = mapper.readTree(read(statsFile));
            JsonNode sealNode = stats.get("corpusSha256");
            if (sealNode == null) {
                throw new IllegalStateException("O campo corpusSha256 não consta em " + statsFile);
            }
            String expected = sealNode.asText();
            String actual = Sha256.hex(text);
            if (!expected.equals(actual)) {
                throw new IllegalStateException("O sha256 de lgpd.txt diverge do selo em stats.json."
                        + " corpusSha256 em stats.json: " + expected + " | sha256 calculado de lgpd.txt: " + actual);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler " + statsFile, e);
        }
    }

    private List<Unit> readUnits(Path path) {
        try {
            List<Unit> units = new ArrayList<>();
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                if (!line.isBlank()) {
                    units.add(mapper.readValue(line, Unit.class));
                }
            }
            return units;
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler as unidades em " + path, e);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler " + path, e);
        }
    }
}
