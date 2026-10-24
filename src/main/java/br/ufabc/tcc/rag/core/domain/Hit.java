package br.ufabc.tcc.rag.core.domain;

import java.util.List;

  // rank é A posição no ranking (ex: 1 para o melhor resultado)
// chunkid é o id do pedaço de texto recuperado
// score: A pontuação de similaridade (ex: 0.85)
// unitIds: IDs das unidades contidas neste chunk (usado para checar o gabarito)
public record Hit(int rank, String chunkId,double score,List<String> unitIds   
) {
   
}