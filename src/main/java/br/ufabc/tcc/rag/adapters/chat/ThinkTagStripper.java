package br.ufabc.tcc.rag.adapters.chat;

import java.util.regex.Pattern;

public final class ThinkTagStripper {

    private static final Pattern CLOSED_BLOCK = Pattern.compile("<think>.*?</think>", Pattern.DOTALL);
    private static final Pattern OPEN_BLOCK = Pattern.compile("<think>.*$", Pattern.DOTALL);

    private ThinkTagStripper() {
    }

    public static String strip(String text) {
        String withoutClosed = CLOSED_BLOCK.matcher(text).replaceAll("");
        return OPEN_BLOCK.matcher(withoutClosed).replaceAll("").strip();
    }
}
