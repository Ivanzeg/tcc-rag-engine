package br.ufabc.tcc.rag.app;

import br.ufabc.tcc.rag.adapters.Sha256;
import br.ufabc.tcc.rag.adapters.chunking.AdaptiveChunker;
import br.ufabc.tcc.rag.adapters.chunking.FixedChunker;
import br.ufabc.tcc.rag.adapters.chunking.SemanticChunker;
import br.ufabc.tcc.rag.adapters.embedding.OpenAiEmbeddingProvider;
import br.ufabc.tcc.rag.adapters.vector.PgVectorIndex;
import br.ufabc.tcc.rag.core.domain.Corpus;
import br.ufabc.tcc.rag.core.domain.GoldQuery;
import br.ufabc.tcc.rag.core.port.Chunker;
import br.ufabc.tcc.rag.core.port.EmbeddingProvider;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class ExperimentServices {

    static final Path CORPUS_DIR = Path.of("data", "corpus");
    static final Path CHUNKS_DIR = Path.of("data", "chunks");
    static final Path EMBEDDING_CACHE_DIR = Path.of("data", "cache", "embeddings");
    static final Path QUERY_EMBEDDINGS_FILE = Path.of("data", "cache", "query_embeddings.json");
    static final Path RESOLVED_QUERIES_FILE = Path.of("data", "golden", "queries_resolved.json");
    static final Path CALIBRATION_DIR = Path.of("data", "calibration");
    static final Path ADAPTIVE_CHOICE_FILE = CALIBRATION_DIR.resolve("adaptive_choice.json");
    static final Path RESULTS_DIR = Path.of("results");

    private static final String DOCUMENT_ID = "lgpd-13709-2018";
    private static final String DOCUMENT_VERSION = "v1";

    private final Config config;
    private final Corpus corpus;
    private final OpenAiEmbeddingProvider embeddings;
    private PgVectorIndex index;

    public ExperimentServices(Config config) {
        this.config = config;
        this.corpus = new CorpusLoader().load(CORPUS_DIR);
        this.embeddings = new OpenAiEmbeddingProvider(
                config.openAiApiKey(), config.openAiEmbeddingModel(), EMBEDDING_CACHE_DIR);
    }

    public Config config() {
        return config;
    }

    public Corpus corpus() {
        return corpus;
    }

    public OpenAiEmbeddingProvider embeddings() {
        return embeddings;
    }

    public PgVectorIndex index() {
        if (index == null) {
            index = new PgVectorIndex(
                    config.dbUrl(), config.dbUser(), config.dbPassword(), embeddings.dimension(),
                    new PgVectorIndex.Settings(DOCUMENT_ID, DOCUMENT_VERSION, Sha256.hex(corpus.canonicalText()),
                            config.openAiEmbeddingModel()),
                    RESULTS_DIR.resolve("environment.txt"));
        }
        return index;
    }

    public Chunker chunkerFor(String strategy, EmbeddingProvider provider) {
        return switch (strategy) {
            case "fixed" -> new FixedChunker();
            case "semantic" -> new SemanticChunker(config.semanticK(), config.semanticMaxWords());
            case "adaptive" -> adaptiveChunker(provider);
            default -> throw new IllegalStateException("Estratégia desconhecida: " + strategy
                    + ". As estratégias do experimento são fixed, semantic e adaptive.");
        };
    }

    public List<GoldQuery> queries() {
        return new GoldenLoader().load(RESOLVED_QUERIES_FILE);
    }

    public Map<String, float[]> queryVectors(List<GoldQuery> queries) {
        return new QueryEmbeddingStore(QUERY_EMBEDDINGS_FILE, config.openAiEmbeddingModel(), embeddings)
                .load(queries);
    }

    private Chunker adaptiveChunker(EmbeddingProvider provider) {
        if (config.adaptiveMicroHeaders()) {
            throw new IllegalStateException("adaptive.microHeaders=true não está implementado."
                    + " A rodada oficial usa adaptive.microHeaders=false.");
        }
        AdaptiveChoice choice = AdaptiveChoice.read(ADAPTIVE_CHOICE_FILE);
        String corpusSha256 = Sha256.hex(corpus.canonicalText());
        if (!corpusSha256.equals(choice.corpusSha256())) {
            throw new IllegalStateException("A calibração em " + ADAPTIVE_CHOICE_FILE
                    + " foi feita sobre outro corpus (corpusSha256 " + choice.corpusSha256()
                    + ", atual " + corpusSha256 + "). Rode calibrate-adaptive de novo.");
        }
        if (choice.maxWords() != config.adaptiveMaxWords()) {
            throw new IllegalStateException("A calibração em " + ADAPTIVE_CHOICE_FILE + " usou teto de "
                    + choice.maxWords() + " palavras, mas adaptive.maxWords é " + config.adaptiveMaxWords()
                    + ". Rode calibrate-adaptive de novo.");
        }
        if (!config.openAiEmbeddingModel().equals(choice.embeddingModel())) {
            throw new IllegalStateException("A calibração em " + ADAPTIVE_CHOICE_FILE + " usou o modelo "
                    + choice.embeddingModel() + ", mas openai.embedding.model é " + config.openAiEmbeddingModel()
                    + ". Rode calibrate-adaptive de novo.");
        }
        return new AdaptiveChunker(provider, choice.tau(), config.adaptiveMaxWords());
    }
}
