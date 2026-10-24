package br.ufabc.tcc.rag.core.domain; 

public record Unit(
    String id,       
    int index,       
    String chapter,  
    String article,  
    UnitType type,   // Enum: se é um título (HEADING) ou um dispositivo (PROVISION)
    int start,       // Índice numérico onde o texto começa 
    int end,         // Índice numérico onde o texto termina
    String text      // O pedaço de texto limpo propriamente dito
) {
    
}