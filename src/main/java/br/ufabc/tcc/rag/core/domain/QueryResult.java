package br.ufabc.tcc.rag.core.domain;

import java.util.List;

public record QueryResult(GoldQuery query, List<Hit> hits, List<Boolean> relevance, QueryMetrics metrics) {
}
