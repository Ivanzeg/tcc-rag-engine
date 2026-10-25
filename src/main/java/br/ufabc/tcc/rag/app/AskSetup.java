package br.ufabc.tcc.rag.app;

import br.ufabc.tcc.rag.adapters.Sha256;
import br.ufabc.tcc.rag.adapters.vector.PgVectorIndex;
import br.ufabc.tcc.rag.core.domain.Corpus;
import br.ufabc.tcc.rag.core.domain.GoldQuery;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalInt;

public final class AskSetup {

    private static final int EMBEDDING_DIMENSION = 1536;
    private static final Path OUTPUT_DIR = Path.of("results", "agnosticism");

    private AskSetup() {
    }

    public static AskCommand create(Config config) {
        String strategy = config.askStrategy();
        Corpus corpus = new CorpusLoader().load(ExperimentServices.CORPUS_DIR);
        List<GoldQuery> queries = new GoldenLoader().load(ExperimentServices.RESOLVED_QUERIES_FILE);
        QueryEmbeddingStore vectors = new QueryEmbeddingStore(ExperimentServices.QUERY_EMBEDDINGS_FILE,
                config.openAiEmbeddingModel());
        PgVectorIndex index = new PgVectorIndex(config.dbUrl(), config.dbUser(), config.dbPassword(),
                EMBEDDING_DIMENSION,
                new PgVectorIndex.Settings(ExperimentServices.DOCUMENT_ID, ExperimentServices.DOCUMENT_VERSION,
                        Sha256.hex(corpus.canonicalText()), config.openAiEmbeddingModel()),
                ExperimentServices.RESULTS_DIR.resolve("environment.txt"));
        index.attach(strategy);
        boolean ollama = config.chatProvider().equalsIgnoreCase("ollama");
        OptionalInt contextLimit = ollama ? OptionalInt.of(config.ollamaNumCtx()) : OptionalInt.empty();
        return new AskCommand(corpus, queries, vectors::read, index,
                new ChunkLoader().load(ExperimentServices.CHUNKS_DIR, strategy), ChatProviderFactory.create(config),
                new AskCommand.Settings(strategy, config.experimentK(), ChatProviderFactory.configLine(config),
                        contextLimit, OUTPUT_DIR));
    }
}
