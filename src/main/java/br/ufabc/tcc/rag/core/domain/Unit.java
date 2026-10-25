package br.ufabc.tcc.rag.core.domain; 

public record Unit(
    String id,       
    int index,       
    String chapter,  
    String article,  
    UnitType type,
    int start,
    int end,
    String text
) {
    
}