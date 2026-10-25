package br.ufabc.tcc.rag.app;

import br.ufabc.tcc.rag.core.domain.GoldQuery;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public final class GoldenLoader {

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public List<GoldQuery> load(Path resolvedFile) {
        try {
            return List.of(mapper.readValue(resolvedFile.toFile(), GoldQuery[].class));
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler as consultas resolvidas em " + resolvedFile, e);
        }
    }
}
