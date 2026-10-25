package br.ufabc.tcc.rag.adapters.chunking;

import br.ufabc.tcc.rag.core.domain.Chunk;
import br.ufabc.tcc.rag.core.domain.Corpus;
import br.ufabc.tcc.rag.core.domain.Unit;
import br.ufabc.tcc.rag.core.port.Chunker;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import java.util.ArrayList;
import java.util.List;

public final class FixedChunker implements Chunker {

    private static final String NAME = "fixed";
    private static final int CHUNK_SIZE_CHARS = 1000;
    private static final int OVERLAP_CHARS = 100;
    private static final double MIN_UNIT_COVERAGE = 0.5;
    private static final int PREVIEW_LENGTH = 80;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<Chunk> chunk(Corpus corpus) {
        String text = corpus.canonicalText();
        List<TextSegment> segments = DocumentSplitters.recursive(CHUNK_SIZE_CHARS, OVERLAP_CHARS)
                .split(Document.from(text));
        Normalized normalizedText = Normalized.of(text);
        List<Chunk> chunks = new ArrayList<>();
        int searchFrom = 0;
        for (int i = 0; i < segments.size(); i++) {
            String segment = segments.get(i).text();
            int[] range = locate(text, normalizedText, segment, searchFrom, i + 1);
            searchFrom = range[0] + 1;
            String id = String.format("%s_c%04d", NAME, i + 1);
            chunks.add(new Chunk(id, NAME, segment, unitsOf(corpus.units(), range[0], range[1])));
        }
        return chunks;
    }

    private static int[] locate(String text, Normalized normalizedText, String segment, int from, int number) {
        int exact = text.indexOf(segment, from);
        if (exact >= 0) {
            return new int[] {exact, exact + segment.length()};
        }
        String normalizedSegment = Normalized.of(segment.strip()).text();
        int normalizedFrom = 0;
        while (normalizedFrom < normalizedText.origin().length && normalizedText.origin()[normalizedFrom] < from) {
            normalizedFrom++;
        }
        int found = normalizedFrom < normalizedText.origin().length && !normalizedSegment.isEmpty()
                ? normalizedText.text().indexOf(normalizedSegment, normalizedFrom)
                : -1;
        if (found < 0) {
            String preview = segment.length() <= PREVIEW_LENGTH ? segment : segment.substring(0, PREVIEW_LENGTH) + "…";
            throw new IllegalStateException("Não foi possível localizar o segmento " + number
                    + " no texto canônico, nem com espaços normalizados, a partir da posição " + from
                    + ". Início do segmento: \"" + preview + "\"");
        }
        int start = normalizedText.origin()[found];
        int end = normalizedText.origin()[found + normalizedSegment.length() - 1] + 1;
        return new int[] {start, end};
    }

    private static List<String> unitsOf(List<Unit> units, int chunkStart, int chunkEnd) {
        List<String> ids = new ArrayList<>();
        for (Unit unit : units) {
            int inside = Math.min(unit.end(), chunkEnd) - Math.max(unit.start(), chunkStart);
            int length = unit.end() - unit.start();
            if (inside > 0 && inside >= MIN_UNIT_COVERAGE * length) {
                ids.add(unit.id());
            }
        }
        return ids;
    }

    private record Normalized(String text, int[] origin) {

        static Normalized of(String source) {
            StringBuilder builder = new StringBuilder(source.length());
            int[] origin = new int[source.length()];
            for (int i = 0; i < source.length(); i++) {
                char c = source.charAt(i);
                if (Character.isWhitespace(c)) {
                    if (builder.length() > 0 && builder.charAt(builder.length() - 1) != ' ') {
                        origin[builder.length()] = i;
                        builder.append(' ');
                    }
                } else {
                    origin[builder.length()] = i;
                    builder.append(c);
                }
            }
            int[] trimmed = new int[builder.length()];
            System.arraycopy(origin, 0, trimmed, 0, builder.length());
            return new Normalized(builder.toString(), trimmed);
        }
    }
}
