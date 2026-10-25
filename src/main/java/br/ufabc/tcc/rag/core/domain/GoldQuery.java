package br.ufabc.tcc.rag.core.domain;
import java.util.List;
public record GoldQuery(String id,Stratum stratum,String question,List<String> evidenceUnitIds) {

}
