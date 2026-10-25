package br.ufabc.tcc.rag.app;

import java.nio.file.Path;
import java.util.List;

public final class Main {

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
        Config.load(Path.of("config.properties"));
        if (args.length == 0 || !COMMANDS.contains(args[0])) {
            System.out.println("Comandos previstos:");
            COMMANDS.forEach(command -> System.out.println("  " + command));
            return;
        }
        System.out.println(args[0] + ": ainda não implementado");
    }
}
