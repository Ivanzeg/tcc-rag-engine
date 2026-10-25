package br.ufabc.tcc.rag.adapters.corpus;

import br.ufabc.tcc.rag.core.domain.Corpus;

public record CorpusBuild(Corpus corpus, CleaningCounters counters, CleaningReport report) {
}
