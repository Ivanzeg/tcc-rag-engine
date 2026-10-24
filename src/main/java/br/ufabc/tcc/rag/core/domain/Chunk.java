package br.ufabc.tcc.rag.core.domain;

//abaixo, String strategy é a estrategia que gerou, se fixed, se semantic, etc. 
//unitIds é a lista de ids das unidades que compõem o chunk
//o próprio record injeta o construtor com os quatro parâmetros, então não é necessário criar um construtor manualmente
import java.util.List;
public record Chunk(String id, String strategy, String text, List<String> unitIds)
{

}
