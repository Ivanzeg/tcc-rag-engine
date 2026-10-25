package br.ufabc.tcc.rag.core.domain;

import java.util.List;

public record Hit(int rank, String chunkId,double score,List<String> unitIds   
) {
   
}