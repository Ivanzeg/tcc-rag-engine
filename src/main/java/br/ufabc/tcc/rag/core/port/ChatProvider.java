package br.ufabc.tcc.rag.core.port;

public interface ChatProvider {
    
    
    String name();
    String generate(String systemPrompt, String userPrompt);
}