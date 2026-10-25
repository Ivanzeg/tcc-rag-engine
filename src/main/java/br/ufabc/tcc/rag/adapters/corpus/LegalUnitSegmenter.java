package br.ufabc.tcc.rag.adapters.corpus;

import br.ufabc.tcc.rag.core.domain.Corpus;
import br.ufabc.tcc.rag.core.domain.Unit;
import br.ufabc.tcc.rag.core.domain.UnitType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LegalUnitSegmenter {

    static final Pattern ARTICLE = Pattern.compile("^(Art\\.\\s*(\\d+)([ºo°])?(?:-([A-Z]))?)(?![\\p{L}\\d])");

    private static final int MIN_UPPERCASE_LETTERS = 3;

    private static final Pattern CHAPTER = Pattern.compile("^CAP[ÍI]TULO\\s+[IVXLCDM]+\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern SECTION = Pattern.compile("^SE[ÇC][ÃA]O\\s+[IVXLCDM]+(?:-[A-Z])?\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern PARAGRAPH = Pattern.compile("^§\\s*\\d+[ºo°]?(?![\\p{L}\\d])");
    private static final Pattern SINGLE_PARAGRAPH = Pattern.compile("^Parágrafo\\s+único\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern ITEM = Pattern.compile("^[IVXLCDM]+(?:-[A-Z])?\\s*-\\s");
    private static final Pattern SUBITEM = Pattern.compile("^[a-z]\\)\\s");

    private static final Pattern ANNOTATION = Pattern.compile(
            "\\s*\\((?:Redação dada|Incluíd[oa]|Vide|Vigência|Revogad[oa]|Promulgação|Produção de efeito)[^)]*\\)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern LABEL = Pattern.compile(
            "^(?:(?iu:CAP[ÍI]TULO|SE[ÇC][ÃA]O)\\s+[IVXLCDM]+(?:-[A-Z])?\\s*-?"
                    + "|Art\\.\\s*\\d+[ºo°]?(?:-[A-Z])?"
                    + "|§\\s*\\d+[ºo°]?(?:-[A-Z])?"
                    + "|(?iu:Parágrafo\\s+único)"
                    + "|[IVXLCDM]+(?:-[A-Z])?\\s*-"
                    + "|[a-z]\\))[\\s.]*");
    private static final Pattern VETOED = Pattern.compile("^\\((?iu:vetad[oa])\\)\\s*[.;:,]*$");
    private static final Pattern ONLY_PUNCTUATION = Pattern.compile("^[\\p{Punct}\\s]*$");

    private enum Kind { CHAPTER, SECTION, UPPERCASE, ARTICLE, OTHER }

    private static final class RawUnit {
        final Kind kind;
        final String articleLabel;
        final StringBuilder text;

        RawUnit(Kind kind, String articleLabel, String firstLine) {
            this.kind = kind;
            this.articleLabel = articleLabel;
            this.text = new StringBuilder(firstLine);
        }
    }

    public Corpus segment(List<String> lines, CleaningCounters counters, CleaningReport report) {
        List<RawUnit> raw = recompose(lines);
        List<Unit> units = new ArrayList<>();
        StringBuilder canonical = new StringBuilder();
        String chapter = "";
        String section = null;
        String article = "";
        for (RawUnit rawUnit : raw) {
            List<String> removed = new ArrayList<>();
            String text = stripAnnotations(rawUnit.text.toString(), counters, removed);
            UnitType type = rawUnit.kind == Kind.ARTICLE || rawUnit.kind == Kind.OTHER
                    ? UnitType.PROVISION : UnitType.HEADING;
            if (isVetoed(text)) {
                counters.vetoed++;
                report.vetoedDiscarded.add(article.isEmpty() ? text : article + " | " + text);
                removed.forEach(a -> report.annotationsRemoved.add(a + " | unidade descartada como vetada: " + text));
                continue;
            }
            if (type == UnitType.PROVISION && isEmptyContent(text)) {
                counters.emptiedByAnnotation++;
                report.emptiedDiscarded.add(article.isEmpty() ? text : article + " | " + text);
                removed.forEach(a -> report.annotationsRemoved.add(a + " | unidade descartada por ficar vazia: " + text));
                continue;
            }
            String unitId = String.format("u%04d", units.size() + 1);
            removed.forEach(a -> report.annotationsRemoved.add(a + " | " + unitId));
            String unitArticle = "";
            if (rawUnit.kind == Kind.CHAPTER) {
                chapter = text;
                section = null;
            } else if (rawUnit.kind == Kind.SECTION) {
                section = text;
            } else if (rawUnit.kind == Kind.ARTICLE) {
                article = rawUnit.articleLabel;
            }
            if (type == UnitType.PROVISION) {
                unitArticle = article;
            }
            String unitChapter = section == null ? chapter : chapter + " / " + section;
            if (!units.isEmpty()) {
                canonical.append("\n\n");
            }
            int start = canonical.length();
            canonical.append(text);
            int end = canonical.length();
            int index = units.size();
            units.add(new Unit(unitId, index, unitChapter, unitArticle, type,
                    start, end, text));
        }
        return new Corpus(units, canonical.toString());
    }

    private List<RawUnit> recompose(List<String> lines) {
        List<RawUnit> raw = new ArrayList<>();
        for (String line : lines) {
            Matcher article = ARTICLE.matcher(line);
            Kind kind = classify(line);
            if (kind != null) {
                String label = article.find() ? article.group(1) : "";
                raw.add(new RawUnit(kind, label, line));
            } else if (raw.isEmpty()) {
                throw new IllegalStateException("Linha de continuação sem unidade corrente: " + line);
            } else {
                raw.get(raw.size() - 1).text.append(' ').append(line);
            }
        }
        return raw;
    }

    private Kind classify(String line) {
        if (CHAPTER.matcher(line).find()) {
            return Kind.CHAPTER;
        }
        if (SECTION.matcher(line).find()) {
            return Kind.SECTION;
        }
        if (ARTICLE.matcher(line).find()) {
            return Kind.ARTICLE;
        }
        if (PARAGRAPH.matcher(line).find() || SINGLE_PARAGRAPH.matcher(line).find()
                || ITEM.matcher(line).find() || SUBITEM.matcher(line).find()) {
            return Kind.OTHER;
        }
        if (isUppercaseHeading(line)) {
            return Kind.UPPERCASE;
        }
        return null;
    }

    private boolean isUppercaseHeading(String line) {
        long letters = line.chars().filter(Character::isLetter).count();
        return letters >= MIN_UPPERCASE_LETTERS
                && !line.contains("Art.")
                && line.equals(line.toUpperCase(Locale.ROOT));
    }

    private String stripAnnotations(String text, CleaningCounters counters, List<String> removed) {
        Matcher matcher = ANNOTATION.matcher(text);
        while (matcher.find()) {
            counters.annotations++;
            removed.add(matcher.group().trim());
        }
        String stripped = ANNOTATION.matcher(text).replaceAll("");
        return stripped.replaceAll("\\s+", " ").trim();
    }

    private boolean isEmptyContent(String text) {
        String content = LABEL.matcher(text).replaceFirst("").trim();
        return ONLY_PUNCTUATION.matcher(content).matches();
    }

    private boolean isVetoed(String text) {
        String content = LABEL.matcher(text).replaceFirst("").trim();
        return VETOED.matcher(content).matches();
    }
}
