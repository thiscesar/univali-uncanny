package jogo;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class FantasmaTest {

	private static void passar(Fantasma f, float segundos, float jx, float jz) {
		for (float t = 0; t < segundos; t += 0.05f) f.atualizar(0.05f, jx, jz);
	}

	@Test
	void apareceAndaESomeNoFimDoCaminho() {
		Fantasma f = new Fantasma(0, 20, 1, 0, 1f, 10);
		assertEquals(0, f.alfa(), 1e-6);
		passar(f, 0.5f, 0, 0);
		assertTrue(f.alfa() > 0.3f && f.alfa() < 0.7f, "aparecendo");
		passar(f, 4, 0, 0);
		assertEquals(1, f.alfa(), 1e-6);
		assertEquals(4.5f, f.x, 0.1f);
		assertEquals(20, f.z, 1e-4);
		passar(f, 6, 0, 0);
		assertTrue(f.acabou());
		assertEquals(0, f.alfa(), 1e-6);
	}

	@Test
	void someSeOJogadorChegaPertoOuSePedir() {
		Fantasma f = new Fantasma(0, 20, 1, 0, 1f, 30);
		passar(f, 2, 0, 0);
		passar(f, Fantasma.SAIDA + 0.1f, 0, 16); // o jogador a 4 m
		assertTrue(f.acabou());
		Fantasma g = new Fantasma(0, 20, 0, 1, 1f, 30);
		passar(g, 2, 0, 0);
		g.sumir();
		passar(g, Fantasma.SAIDA + 0.1f, 0, 0);
		assertTrue(g.acabou());
	}
}
