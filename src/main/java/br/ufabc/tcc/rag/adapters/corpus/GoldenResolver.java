package br.ufabc.tcc.rag.adapters.corpus;

import br.ufabc.tcc.rag.core.domain.GoldQuery;
import br.ufabc.tcc.rag.core.domain.Stratum;
import br.ufabc.tcc.rag.core.domain.Unit;
import br.ufabc.tcc.rag.core.domain.UnitType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class GoldenResolver {

    private static final int EXPECTED_PER_STRATUM = 8;

    private final ObjectMapper mapper = new ObjectMapper();

    public List<GoldQuery> resolve(Path queriesPath, Path unitsPath, Path outputPath, PrintStream out) {
        List<Unit> units = readUnits(unitsPath);
        JsonNode queries = readQueries(queriesPath);
        List<String> normalizedUnits = new ArrayList<>();
        for (Unit unit : units) {
            normalizedUnits.add(normalize(unit.text()));
        }

        List<String> errors = new ArrayList<>();
        List<GoldQuery> resolved = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        for (JsonNode node : queries) {
            String id = node.path("id").asText("");
            Stratum stratum = parseStratum(node.path("stratum").asText(""), id, errors);
            JsonNode evidence = node.path("evidence");
            if (!evidence.isArray() || evidence.isEmpty()) {
                errors.add(id + ": a consulta não tem nenhum trecho em 'evidence'");
                continue;
            }
            Set<String> unitIds = new LinkedHashSet<>();
            for (JsonNode snippet : evidence) {
                String unitId = resolveSnippet(id, snippet.asText(""), units, normalizedUnits, errors);
                if (unitId != null) {
                    unitIds.add(unitId);
                }
            }
            if (stratum != null) {
                resolved.add(new GoldQuery(id, stratum, node.path("question").asText(""), List.copyOf(unitIds)));
                notes.add(node.path("note").asText(""));
            }
        }
        if (!errors.isEmpty()) {
            throw new GoldenValidationException(errors);
        }
        write(outputPath, resolved, notes);
        printSummary(resolved, out);
        return resolved;
    }

    static String normalize(String text) {
        String result = text.toLowerCase(Locale.ROOT);
        result = result.replace(' ', ' ');
        result = result.replaceAll("[“”„«»]", "\"");
        result = result.replaceAll("[‘’‚]", "'");
        result = result.replaceAll("[‐‑‒–—―−]", "-");
        return result.replaceAll("\\s+", " ").trim();
    }

    private String resolveSnippet(String queryId, String snippet, List<Unit> units, List<String> normalizedUnits,
            List<String> errors) {
        String needle = normalize(snippet);
        if (needle.isEmpty()) {
            errors.add(queryId + ": trecho de evidência vazio");
            return null;
        }
        List<String> provisions = new ArrayList<>();
        List<String> headings = new ArrayList<>();
        for (int i = 0; i < units.size(); i++) {
            if (normalizedUnits.get(i).contains(needle)) {
                Unit unit = units.get(i);
                (unit.type() == UnitType.PROVISION ? provisions : headings).add(unit.id());
            }
        }
        if (provisions.size() == 1) {
            return provisions.get(0);
        }
        if (provisions.size() > 1) {
            errors.add(queryId + ": o trecho aparece em mais de uma unidade " + provisions
                    + "; alongue o trecho. Trecho: \"" + snippet + "\"");
        } else if (!headings.isEmpty()) {
            errors.add(queryId + ": o trecho só aparece em título " + headings
                    + "; títulos não são evidência. Trecho: \"" + snippet + "\"");
        } else {
            errors.add(queryId + ": o trecho não foi encontrado em nenhuma unidade. Trecho: \"" + snippet + "\"");
        }
        return null;
    }

    private Stratum parseStratum(String value, String queryId, List<String> errors) {
        try {
            return Stratum.valueOf(value);
        } catch (IllegalArgumentException e) {
            errors.add(queryId + ": estrato desconhecido \"" + value
                    + "\"; use FACTUAL, TERMINOLOGICAL ou COMPREHENSIVE");
            return null;
        }
    }

    private void write(Path outputPath, List<GoldQuery> queries, List<String> notes) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < queries.size(); i++) {
            GoldQuery query = queries.get(i);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", query.id());
            row.put("stratum", query.stratum().name());
            row.put("question", query.question());
            row.put("evidenceUnitIds", query.evidenceUnitIds());
            row.put("note", notes.get(i));
            rows.add(row);
        }
        try {
            Path parent = outputPath.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(outputPath,
                    mapper.writerWithDefaultPrettyPrinter().writeValueAsString(rows) + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gravar " + outputPath, e);
        }
    }

    private void printSummary(List<GoldQuery> queries, PrintStream out) {
        out.println("Consultas resolvidas: " + queries.size());
        for (Stratum stratum : Stratum.values()) {
            List<GoldQuery> group = queries.stream().filter(q -> q.stratum() == stratum).toList();
            int evidences = group.stream().mapToInt(q -> q.evidenceUnitIds().size()).sum();
            double average = group.isEmpty() ? 0.0 : (double) evidences / group.size();
            out.println(String.format(Locale.ROOT, "  %-15s consultas: %d | média de evidências: %.2f",
                    stratum, group.size(), average));
            if (group.size() != EXPECTED_PER_STRATUM) {
                out.println("  AVISO: esperadas " + EXPECTED_PER_STRATUM + " consultas em " + stratum
                        + ", encontradas " + group.size());
            }
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

    private JsonNode readQueries(Path path) {
        try {
            JsonNode root = mapper.readTree(Files.readString(path, StandardCharsets.UTF_8));
            if (!root.isArray()) {
                throw new IllegalStateException(path + " deve conter uma lista JSON de consultas");
            }
            return root;
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler as consultas em " + path, e);
        }
    }
}
