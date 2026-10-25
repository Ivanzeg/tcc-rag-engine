package br.ufabc.tcc.rag.adapters.corpus;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

public final class PdfTextExtractor {

    public int pageCount(Path pdf) {
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            return document.getNumberOfPages();
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao abrir o PDF: " + pdf, e);
        }
    }

    public List<List<String>> extract(Path pdf, int startPage, int endPage) {
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            int total = document.getNumberOfPages();
            if (startPage < 1 || endPage > total || startPage > endPage) {
                throw new IllegalArgumentException("Intervalo de páginas inválido: " + startPage + "-" + endPage
                        + " (o PDF tem " + total + " páginas).");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            List<List<String>> pages = new ArrayList<>();
            for (int page = startPage; page <= endPage; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                pages.add(nonBlankLines(stripper.getText(document)));
            }
            return pages;
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao extrair texto do PDF: " + pdf, e);
        }
    }

    private static List<String> nonBlankLines(String text) {
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\\R")) {
            if (!line.isBlank()) {
                lines.add(line);
            }
        }
        return lines;
    }
}