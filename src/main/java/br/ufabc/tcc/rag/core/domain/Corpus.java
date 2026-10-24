package br.ufabc.tcc.rag.core.domain;

import java.util.List;

public record Corpus(List<Unit> units,String canonicalText
)

{
}
