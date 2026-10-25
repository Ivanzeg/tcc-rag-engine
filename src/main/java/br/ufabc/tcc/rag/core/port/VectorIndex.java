package br.ufabc.tcc.rag.core.port;

import br.ufabc.tcc.rag.core.domain.Chunk;
import br.ufabc.tcc.rag.core.domain.Hit;
import java.util.List;

public interface VectorIndex {

void reset(String strategy);
void addAll(List<Chunk> chunks, List<float[]> vectors);
List<Hit> search(float[] query, int k);

}