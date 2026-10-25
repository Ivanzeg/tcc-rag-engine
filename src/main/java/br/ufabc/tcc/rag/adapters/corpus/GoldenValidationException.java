package br.ufabc.tcc.rag.adapters.corpus;

import java.util.List;

public final class GoldenValidationException extends RuntimeException {

    private final List<String> errors;

    public GoldenValidationException(List<String> errors) {
        super(errors.size() + " erro(s) na validação do golden set:\n- " + String.join("\n- ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }
}
