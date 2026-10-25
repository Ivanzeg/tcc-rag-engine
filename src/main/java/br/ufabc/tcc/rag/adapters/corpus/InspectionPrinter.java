package br.ufabc.tcc.rag.adapters.corpus;

import br.ufabc.tcc.rag.core.domain.Corpus;
import br.ufabc.tcc.rag.core.domain.Unit;
import br.ufabc.tcc.rag.core.domain.UnitType;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;

public final class InspectionPrinter {

    private static final int SAMPLE_SIZE = 20;
    private static final int HYPHEN_JOIN_WARNING = 300;
    private static final int MAX_LINE_DISPLAY = 220;

    private final PrintStream out;

    public InspectionPrinter(PrintStream out) {
        this.out = out;
    }

    public void print(CorpusBuild build) {
        printHeadersAndFooters(build.report(), build.counters());
        out.println("Linhas de separador (somente \\uFFFD, \\b e espaços) removidas: " + build.counters().separatorLines);
        printHyphenJoins(build.report(), build.counters());
        printShortestAndLongest(build.corpus());
        printFootnoteCandidates(build.report());
        printHeadings(build.corpus());
        printArticleSequence(build.corpus(), build.report());
        printAnnotations(build.report(), build.counters());
    }

    private void printHeadersAndFooters(CleaningReport report, CleaningCounters counters) {
        section("(a) Cabeçalhos e rodapés removidos: " + counters.headerFooterLines + " linhas");
        for (Map.Entry<String, Integer> entry : report.headerFooterForms.entrySet()) {
            out.println("forma [" + entry.getKey() + "]: " + entry.getValue() + " de " + report.pageCount + " páginas");
        }
        report.headerFooterRemoved.forEach(out::println);
    }

    private void printHyphenJoins(CleaningReport report, CleaningCounters counters) {
        section("(b) Junções de hifenização: " + counters.hyphenJoins + " no total, primeiras " + SAMPLE_SIZE);
        report.hyphenJoinSamples.forEach(out::println);
        out.println("Junções de hífen repetido: " + counters.hyphenRepeatJoins);
        report.hyphenRepeatSamples.forEach(out::println);
        if (counters.hyphenJoins > HYPHEN_JOIN_WARNING) {
            out.println("AVISO: mais de " + HYPHEN_JOIN_WARNING + " junções; a regra pode estar agressiva demais.");
        }
    }

    private void printShortestAndLongest(Corpus corpus) {
        List<String> lines = new ArrayList<>();
        for (String line : corpus.canonicalText().split("\n")) {
            if (!line.isBlank()) {
                lines.add(line);
            }
        }
        section("(c) " + SAMPLE_SIZE + " linhas mais curtas do texto limpo");
        lines.stream().sorted(Comparator.comparingInt(String::length)).limit(SAMPLE_SIZE)
                .forEach(line -> out.println("[" + line.length() + "] " + line));
        section("(c) " + SAMPLE_SIZE + " linhas mais longas do texto limpo");
        lines.stream().sorted(Comparator.comparingInt(String::length).reversed()).limit(SAMPLE_SIZE)
                .forEach(line -> out.println("[" + line.length() + "] " + abbreviate(line)));
    }

    private void printFootnoteCandidates(CleaningReport report) {
        section("(d) Linhas brutas que começam com dígitos e espaço (candidatas a nota de rodapé): "
                + report.footnoteCandidates.size());
        report.footnoteCandidates.forEach(line -> out.println(abbreviate(line)));
    }

    private void printHeadings(Corpus corpus) {
        section("(e) HEADING detectados");
        for (Unit unit : corpus.units()) {
            if (unit.type() == UnitType.HEADING) {
                out.println(unit.id() + " " + unit.text());
            }
        }
    }

    private void printArticleSequence(Corpus corpus, CleaningReport report) {
        section("(f) Sequência de artigos");
        TreeSet<Integer> present = new TreeSet<>();
        List<String> flags = new ArrayList<>();
        int previous = 0;
        String previousLabel = "(início)";
        for (Unit unit : corpus.units()) {
            if (unit.type() != UnitType.PROVISION) {
                continue;
            }
            Matcher matcher = LegalUnitSegmenter.ARTICLE.matcher(unit.text());
            if (!matcher.find()) {
                continue;
            }
            int number = Integer.parseInt(matcher.group(2));
            boolean hasSuffix = matcher.group(4) != null;
            boolean sequential = number == previous + 1 || (number == previous && hasSuffix);
            if (!sequential) {
                flags.add(unit.id() + " " + matcher.group(1) + " depois de " + previousLabel);
            }
            present.add(number);
            previous = number;
            previousLabel = matcher.group(1);
        }
        List<Integer> missing = new ArrayList<>();
        if (!present.isEmpty()) {
            for (int n = 1; n <= present.last(); n++) {
                if (!present.contains(n)) {
                    missing.add(n);
                }
            }
        }
        out.println("Artigos distintos (parte numérica): " + present.size() + ", maior número: "
                + (present.isEmpty() ? 0 : present.last()));
        out.println("Números ausentes na sequência: " + missing);
        out.println("Dispositivos descartados como vetados (" + report.vetoedDiscarded.size() + "):");
        report.vetoedDiscarded.forEach(line -> out.println("  " + abbreviate(line)));
        out.println("Artigos fora da sequência (anterior + 1, ou mesmo número com sufixo): " + flags.size());
        flags.forEach(line -> out.println("  " + line));
    }

    private void printAnnotations(CleaningReport report, CleaningCounters counters) {
        section("(g) Anotações legislativas removidas: " + report.annotationsRemoved.size());
        report.annotationsRemoved.forEach(line -> out.println(abbreviate(line)));
        out.println();
        out.println("Unidades descartadas por ficarem vazias após a remoção (" + counters.emptiedByAnnotation + "):");
        report.emptiedDiscarded.forEach(line -> out.println("  " + abbreviate(line)));
    }

    private void section(String title) {
        out.println();
        out.println("===== " + title + " =====");
    }

    private static String abbreviate(String line) {
        if (line.length() <= MAX_LINE_DISPLAY) {
            return line;
        }
        return line.substring(0, MAX_LINE_DISPLAY) + "…";
    }
}
