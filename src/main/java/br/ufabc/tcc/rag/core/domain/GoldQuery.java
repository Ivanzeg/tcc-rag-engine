package br.ufabc.tcc.rag.core.domain;
import java.util.List;
//abaixo, o Stratum é Enum é a dificulddade entre a pergunta e o texto(FACTUAL, TERMINOLOGICAL, COMPREHENSIVE), um tipo de consulta
//question é a pergunta real em portugues
//evidenceUnitIds é O gabarito: IDs das Units (dispositivos) que contêm a resposta
//uma informação importante: Como criamos os dois no mesmo pacote core/domain, o Java enxerga o Stratum automaticamente sem precisar de import 
public record GoldQuery(String id,Stratum stratum,String question,List<String> evidenceUnitIds) {

}
