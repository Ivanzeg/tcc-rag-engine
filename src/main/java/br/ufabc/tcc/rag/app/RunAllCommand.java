package br.ufabc.tcc.rag.app;

import br.ufabc.tcc.rag.core.domain.GoldQuery;
import br.ufabc.tcc.rag.core.domain.QueryResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RunAllCommand {

    private final ExperimentServices services;

    public RunAllCommand(ExperimentServices services) {
        this.services = services;
    }

    public void run() {
        List<String> strategies = services.config().experimentStrategies();
        if (strategies.isEmpty()) {
            throw new IllegalStateException("experiment.strategies está vazio em config.properties");
        }
        for (String strategy : strategies) {
            services.chunkerFor(strategy, services.embeddings());
        }
        System.out.println("run-all: estratégias " + String.join(", ", strategies));

        List<GoldQuery> queries = services.queries();
        Map<String, float[]> queryVectors = services.queryVectors(queries);
        EvaluateCommand evaluate = new EvaluateCommand(services);

        Map<String, List<QueryResult>> resultsByStrategy = new LinkedHashMap<>();
        for (String strategy : strategies) {
            System.out.println();
            System.out.println("=== Estratégia " + strategy + ": reset, chunk, embed, index, evaluate ===");
            resultsByStrategy.put(strategy, evaluate.evaluate(strategy, queries, queryVectors));
        }

        System.out.println();
        System.out.println("=== Resumo do run-all ===");
        resultsByStrategy.forEach(EvaluateCommand::printSummary);
    }
}
