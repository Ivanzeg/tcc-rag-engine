package br.ufabc.tcc.rag.app;

import br.ufabc.tcc.rag.adapters.corpus.CorpusBuild;
import br.ufabc.tcc.rag.adapters.corpus.CorpusPipeline;
import br.ufabc.tcc.rag.adapters.corpus.CorpusWriter;
import br.ufabc.tcc.rag.adapters.corpus.GoldenResolver;
import br.ufabc.tcc.rag.adapters.corpus.GoldenValidationException;
import br.ufabc.tcc.rag.adapters.corpus.InspectionPrinter;
import br.ufabc.tcc.rag.adapters.corpus.PdfTextExtractor;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class Main {

    private static final int MAX_DISPLAY_LENGTH = 100;

    private static final List<String> COMMANDS = List.of(
            "inspect-pdf",
            "prepare",
            "validate-golden",
            "search",
            "evaluate",
            "run-all",
            "calibrate-adaptive",
            "ask");

    private Main() {
    }

    public static void main(String[] args) {
        Config config = Config.load(Path.of("config.properties"));
        if (args.length == 0 || !COMMANDS.contains(args[0])) {
            System.out.println("Comandos previstos:");
            COMMANDS.forEach(command -> System.out.println("  " + command));
            return;
        }
        switch (args[0]) {
            case "inspect-pdf" -> inspectPdf(config);
            case "prepare" -> prepare(config);
            case "validate-golden" -> validateGolden();
            case "search" -> search(config, args);
            case "evaluate" -> evaluate(config, args);
            case "calibrate-adaptive" -> calibrateAdaptive(config);
            case "run-all" -> runAll(config);
            default -> System.out.println(args[0] + ": ainda não implementado");
        }
    }

    private static void calibrateAdaptive(Config config) {
        try {
            new CalibrateAdaptiveCommand(new ExperimentServices(config)).run();
        } catch (IllegalStateException e) {
            System.out.println("Erro: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void runAll(Config config) {
        try {
            new RunAllCommand(new ExperimentServices(config)).run();
        } catch (IllegalStateException e) {
            System.out.println("Erro: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void inspectPdf(Config config) {
        Path pdf = config.pdfPath();
        PdfTextExtractor extractor = new PdfTextExtractor();
        int total = extractor.pageCount(pdf);
        System.out.println("Arquivo: " + pdf);
        System.out.println("Total de páginas: " + total);
        System.out.println("Contagem de linhas ignora linhas em branco.");
        List<List<String>> pages = extractor.extract(pdf, 1, total);
        for (int i = 0; i < pages.size(); i++) {
            printPage(i + 1, pages.get(i));
        }
    }

    private static void prepare(Config config) {
        Path pdf = config.pdfPath();
        int startPage = config.pdfStartPage();
        int endPage = config.pdfEndPage();
        CorpusBuild build = new CorpusPipeline().build(pdf, startPage, endPage, config.pdfHeaderFooterThreshold());
        new InspectionPrinter(System.out).print(build);
        Map<String, Object> stats = new CorpusWriter().write(Path.of("data", "corpus"), build, pdf, startPage, endPage);
        System.out.println();
        System.out.println("Corpus gravado em data/corpus. Estatísticas: " + stats);
    }

    private static void validateGolden() {
        try {
            new GoldenResolver().resolve(
                    Path.of("data", "golden", "queries.json"),
                    Path.of("data", "corpus", "units.jsonl"),
                    Path.of("data", "golden", "queries_resolved.json"),
                    System.out);
            System.out.println("Gravado em data/golden/queries_resolved.json");
        } catch (GoldenValidationException e) {
            System.out.println(e.getMessage());
            System.exit(1);
        }
    }

    private static void search(Config config, String[] args) {
        if (args.length < 2) {
            System.out.println("Uso: search \"texto da consulta\"");
            System.exit(1);
        }
        String query = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        try {
            new SearchCommand(new ExperimentServices(config)).run(query);
        } catch (IllegalStateException e) {
            System.out.println("Erro: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void evaluate(Config config, String[] args) {
        if (args.length != 2) {
            System.out.println("Uso: evaluate <estrategia>");
            System.exit(1);
        }
        try {
            new EvaluateCommand(new ExperimentServices(config)).run(args[1]);
        } catch (IllegalStateException e) {
            System.out.println("Erro: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void printPage(int number, List<String> lines) {
        int count = lines.size();
        System.out.println();
        System.out.println("--- Página " + number + " | linhas: " + count + " ---");
        if (count == 0) {
            System.out.println("  (sem texto)");
            return;
        }
        System.out.println("  início 1: " + display(lines.get(0)));
        if (count >= 2) {
            System.out.println("  início 2: " + display(lines.get(1)));
        }
        if (count >= 3) {
            System.out.println("  fim -2: " + display(lines.get(count - 2)));
            System.out.println("  fim -1: " + display(lines.get(count - 1)));
        }
    }

    private static String display(String line) {
        String trimmed = line.trim();
        if (trimmed.length() <= MAX_DISPLAY_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, MAX_DISPLAY_LENGTH) + "…";
    }
}
