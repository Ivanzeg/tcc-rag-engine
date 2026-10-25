package br.ufabc.tcc.rag.core.port;

import java.util.List; 

public interface EmbeddingProvider {

      
    int dimension();

    
    float[] embed(String text);

    List<float[]> embedAll(List<String> texts);
    
}