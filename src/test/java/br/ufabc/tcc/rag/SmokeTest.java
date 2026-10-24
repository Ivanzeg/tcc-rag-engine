package br.ufabc.tcc.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class SmokeTest {


  @Test 
  void deveSomarDoisMaisDois() {
    int resultado = 2 + 2;

    assertEquals(4, resultado, "A matemática básica ado JVM quebrou: 2 + 2 deveria ser 4"); 
  }
@Test 
  void deveConfirmarCondicaoVerdadeira() {
    boolean motorPronto = true;

    assertTrue(motorPronto, "A condição deveria ser verdadeira para o teste passar");
  }

}
