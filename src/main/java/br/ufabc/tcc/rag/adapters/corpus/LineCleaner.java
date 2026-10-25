package br.ufabc.tcc.rag.adapters.corpus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class LineCleaner {

    private static final int MAX_HYPHEN_SAMPLES = 20;

    private static final Pattern EDITOR_NOTE = Pattern.compile("^\\d+[\\s\\u00A0]+Nota do Editor");
    private static final Pattern FOOTNOTE_CANDIDATE = Pattern.compile("^\\d+[\\s\\u00A0]+\\S.*");
    private static final Pattern SEPARATOR_LINE = Pattern.compile("^[\\uFFFD\\u0008\\s]+$");
    private static final Pattern STANDALONE_NUMBER = Pattern.compile("^[\\d\\s\\u00A0]+$");
    private static final Pattern FIRST_CHAPTER = Pattern.compile("^CAP[ÍI]TULO\\s+I\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern CLOSING = Pattern.compile("^Brasília,");
    private static final Pattern ENDS_WITH_LETTER_HYPHEN = Pattern.compile(".*\\p{L}-$");
    private static final Pattern STARTS_HYPHEN_LETTER = Pattern.compile("^-\\p{L}.*");
    private static final Pattern STARTS_LOWERCASE = Pattern.compile("^\\p{Ll}.*");
    private static final Pattern DIGIT_RUN = Pattern.compile("\\d+");
    private static final Pattern WHITESPACE_RUN = Pattern.compile("[\\s\\u00A0]+");

    public List<String> clean(List<List<String>> pages, int startPage, double threshold,
            CleaningCounters counters, CleaningReport report) {
        report.pageCount = pages.size();
        pages = spaceNormalized(pages);
        collectFootnoteCandidates(pages, startPage, report);
        List<List<String>> withoutNote = removeEditorNote(pages, counters);
        List<String> flat = removeHeadersAndFooters(withoutNote, startPage, threshold, counters, report);
        List<String> normalized = new ArrayList<>();
        for (String line : flat) {
            if (SEPARATOR_LINE.matcher(line).matches()) {
                counters.separatorLines++;
                continue;
            }
            if (STANDALONE_NUMBER.matcher(line).matches()) {
                counters.standaloneNumberLines++;
                continue;
            }
            String clean = normalize(line);
            if (!clean.isEmpty()) {
                normalized.add(clean);
            }
        }
        List<String> cut = cutPreambleAndClosing(normalized, counters);
        return joinHyphenation(cut, counters, report);
    }

    private static List<List<String>> spaceNormalized(List<List<String>> pages) {
        List<List<String>> result = new ArrayList<>();
        for (List<String> page : pages) {
            List<String> lines = new ArrayList<>();
            for (String line : page) {
                String clean = line.replaceAll("\\p{Zs}", " ").replaceAll("[ \\t]+", " ").trim();
                if (!clean.isEmpty()) {
                    lines.add(clean);
                }
            }
            result.add(lines);
        }
        return result;
    }

    public static String headerFooterForm(String line) {
        String collapsed = WHITESPACE_RUN.matcher(line.trim()).replaceAll(" ");
        return DIGIT_RUN.matcher(collapsed).replaceAll("#");
    }

    public static String normalize(String line) {
        String text = line.replace("\r", "");
        text = text.replaceAll("\\p{Zs}", " ");
        text = text.replaceAll("[\\u201C\\u201D\\u201E]", "\"");
        text = text.replaceAll("[\\u2018\\u2019\\u201A]", "'");
        text = text.replaceAll("[\\u2010\\u2011\\u2012\\u2013\\u2014\\u2015\\u2212]", "-");
        text = text.replaceAll("[ \\t]+", " ");
        return text.trim();
    }

    private void collectFootnoteCandidates(List<List<String>> pages, int startPage, CleaningReport report) {
        for (int i = 0; i < pages.size(); i++) {
            for (String line : pages.get(i)) {
                if (FOOTNOTE_CANDIDATE.matcher(line.trim()).matches()) {
                    report.footnoteCandidates.add("pág. " + (startPage + i) + ": " + line.trim());
                }
            }
        }
    }

    private List<List<String>> removeEditorNote(List<List<String>> pages, CleaningCounters counters) {
        List<List<String>> result = new ArrayList<>();
        for (List<String> page : pages) {
            int noteStart = -1;
            for (int i = 0; i < page.size(); i++) {
                if (EDITOR_NOTE.matcher(page.get(i).trim()).find()) {
                    noteStart = i;
                    break;
                }
            }
            if (noteStart < 0) {
                result.add(new ArrayList<>(page));
            } else {
                counters.editorNoteLines += page.size() - noteStart;
                result.add(new ArrayList<>(page.subList(0, noteStart)));
            }
        }
        return result;
    }

    private List<String> removeHeadersAndFooters(List<List<String>> pages, int startPage, double threshold,
            CleaningCounters counters, CleaningReport report) {
        Map<String, Integer> pageCounts = new LinkedHashMap<>();
        for (List<String> page : pages) {
            if (page.isEmpty()) {
                continue;
            }
            Set<String> forms = new LinkedHashSet<>();
            forms.add(headerFooterForm(page.get(0)));
            forms.add(headerFooterForm(page.get(page.size() - 1)));
            for (String form : forms) {
                pageCounts.merge(form, 1, Integer::sum);
            }
        }
        Set<String> detected = new LinkedHashSet<>();
        for (Map.Entry<String, Integer> entry : pageCounts.entrySet()) {
            if (entry.getValue() / (double) pages.size() >= threshold) {
                detected.add(entry.getKey());
                report.headerFooterForms.put(entry.getKey(), entry.getValue());
            }
        }
        List<String> flat = new ArrayList<>();
        for (int i = 0; i < pages.size(); i++) {
            List<String> page = new ArrayList<>(pages.get(i));
            int pageNumber = startPage + i;
            if (!page.isEmpty() && detected.contains(headerFooterForm(page.get(0)))) {
                registerRemoved(pageNumber, page.remove(0), counters, report);
            }
            if (!page.isEmpty() && detected.contains(headerFooterForm(page.get(page.size() - 1)))) {
                registerRemoved(pageNumber, page.remove(page.size() - 1), counters, report);
            }
            flat.addAll(page);
        }
        return flat;
    }

    private void registerRemoved(int pageNumber, String line, CleaningCounters counters, CleaningReport report) {
        counters.headerFooterLines++;
        report.headerFooterRemoved.add("pág. " + pageNumber + ": " + line.trim());
    }

    private List<String> cutPreambleAndClosing(List<String> lines, CleaningCounters counters) {
        int chapterStart = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (FIRST_CHAPTER.matcher(lines.get(i)).find()) {
                chapterStart = i;
                break;
            }
        }
        if (chapterStart < 0) {
            throw new IllegalStateException("Linha 'CAPÍTULO I' não encontrada: não foi possível cortar o preâmbulo.");
        }
        counters.preambleLines = chapterStart;
        List<String> body = lines.subList(chapterStart, lines.size());
        int lastArticle = -1;
        for (int i = 0; i < body.size(); i++) {
            if (LegalUnitSegmenter.ARTICLE.matcher(body.get(i)).find()) {
                lastArticle = i;
            }
        }
        if (lastArticle < 0) {
            throw new IllegalStateException("Nenhuma abertura 'Art.' encontrada depois do preâmbulo.");
        }
        int closingStart = -1;
        for (int i = lastArticle + 1; i < body.size(); i++) {
            if (CLOSING.matcher(body.get(i)).find()) {
                closingStart = i;
                break;
            }
        }
        if (closingStart < 0) {
            throw new IllegalStateException("Linha 'Brasília,' não encontrada depois do último artigo.");
        }
        counters.closingLines = body.size() - closingStart;
        return new ArrayList<>(body.subList(0, closingStart));
    }

    private List<String> joinHyphenation(List<String> lines, CleaningCounters counters, CleaningReport report) {
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            int last = result.size() - 1;
            if (last >= 0 && ENDS_WITH_LETTER_HYPHEN.matcher(result.get(last)).matches()
                    && STARTS_HYPHEN_LETTER.matcher(line).matches()) {
                String previous = result.get(last);
                String joined = previous + line.substring(1);
                counters.hyphenRepeatJoins++;
                report.hyphenRepeatSamples.add("antes: [" + previous + "] + [" + line + "]  =>  depois: [" + joined + "]");
                result.set(last, joined);
            } else if (last >= 0 && ENDS_WITH_LETTER_HYPHEN.matcher(result.get(last)).matches()
                    && STARTS_LOWERCASE.matcher(line).matches()) {
                String previous = result.get(last);
                String joined = previous.substring(0, previous.length() - 1) + line;
                counters.hyphenJoins++;
                if (report.hyphenJoinSamples.size() < MAX_HYPHEN_SAMPLES) {
                    report.hyphenJoinSamples.add("antes: [" + previous + "] + [" + line + "]  =>  depois: [" + joined + "]");
                }
                result.set(last, joined);
            } else {
                result.add(line);
            }
        }
        return result;
    }
}
