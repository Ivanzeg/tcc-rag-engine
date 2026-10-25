package br.ufabc.tcc.rag.core.domain;

import java.util.List;
public record Chunk(String id, String strategy, String text, List<String> unitIds)
{

}
