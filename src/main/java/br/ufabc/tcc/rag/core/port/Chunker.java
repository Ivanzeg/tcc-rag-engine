package br.ufabc.tcc.rag.core.port;

import br.ufabc.tcc.rag.core.domain.Chunk;
import br.ufabc.tcc.rag.core.domain.Corpus;
import java.util.List;

public interface Chunker {

    String name();

    List<Chunk> chunk(Corpus corpus);
    
}