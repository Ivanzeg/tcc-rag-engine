package br.ufabc.tcc.rag.adapters.corpus;

import br.ufabc.tcc.rag.core.domain.Corpus;
import java.nio.file.Path;
import java.util.List;

public final class CorpusPipeline {

    private final PdfTextExtractor extractor = new PdfTextExtractor();
    private final LineCleaner cleaner = new LineCleaner();
    private final LegalUnitSegmenter segmenter = new LegalUnitSegmenter();

    public CorpusBuild build(Path pdf, int startPage, int endPage, double headerFooterThreshold) {
        List<List<String>> pages = extractor.extract(pdf, startPage, endPage);
        CleaningCounters counters = new CleaningCounters();
        CleaningReport report = new CleaningReport();
        List<String> lines = cleaner.clean(pages, startPage, headerFooterThreshold, counters, report);
        Corpus corpus = segmenter.segment(lines, counters, report);
        return new CorpusBuild(corpus, counters, report);
    }
}
