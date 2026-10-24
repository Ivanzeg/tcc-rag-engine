package br.ufabc.tcc.rag.core.port;

import br.ufabc.tcc.rag.core.domain.Chunk;
import br.ufabc.tcc.rag.core.domain.Corpus;
import java.util.List;

public interface Chunker {

    // Retorna o nome da estratégia se fixed, semantic
    String name();

    // Recebe o documento inteiro e o fatia em pedaços
    List<Chunk> chunk(Corpus corpus);
    
}