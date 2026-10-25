package br.ufabc.tcc.rag.core.domain;

public record QueryMetrics(String queryId, Stratum stratum, int nEvidence, double precision, double recall,
        double f1, double reciprocalRank, double latencyMs) {
}
