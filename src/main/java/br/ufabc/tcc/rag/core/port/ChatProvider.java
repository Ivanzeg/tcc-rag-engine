package br.ufabc.tcc.rag.core.port;

// name() identifica o provedor; generate() recebe a instrução de sistema e o prompt do usuário (trechos + pergunta)
public interface ChatProvider {
    
    
    String name();
    String generate(String systemPrompt, String userPrompt);
}