package br.ufabc.tcc.rag.adapters.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ThinkTagStripperTest {

    @Test
    void removeUmBlocoNoInicio() {
        assertEquals("Resposta final.", ThinkTagStripper.strip("<think>raciocínio</think>\n\nResposta final."));
    }

    @Test
    void removeBlocoMultilinha() {
        assertEquals("Ok", ThinkTagStripper.strip("<think>\nlinha 1\nlinha 2\n</think>Ok"));
    }

    @Test
    void removeVariosBlocos() {
        assertEquals("A B", ThinkTagStripper.strip("<think>x</think>A <think>y</think>B"));
    }

    @Test
    void mantemTextoSemBloco() {
        assertEquals("Sem raciocínio.", ThinkTagStripper.strip("  Sem raciocínio.  "));
    }

    @Test
    void removeBlocoNaoFechado() {
        assertEquals("Antes", ThinkTagStripper.strip("Antes<think>cortado no meio"));
    }
}
