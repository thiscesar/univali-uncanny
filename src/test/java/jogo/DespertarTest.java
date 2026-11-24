package jogo;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DespertarTest {

	private static Despertar em(float segundos) {
		Despertar d = new Despertar();
		d.atualizar(segundos);
		return d;
	}

	@Test
	void comecaDeOlhosFechadosDeitadoEBorrado() {
		Despertar d = em(0);
		assertEquals(0, d.palpebras(), 1e-6);
		assertEquals(1, d.deitado(), 1e-6);
		assertEquals(1, d.desfoque(), 1e-6);
		assertFalse(d.podeOlhar());
	}

	@Test
	void osOlhosPiscamAntesDeAbrirDeVez() {
		assertTrue(em(1.5f).palpebras() > 0.3f, "entreabertos");
		assertEquals(0, em(2.0f).palpebras(), 0.05f, "fecharam de novo");
		assertTrue(em(2.9f).palpebras() > 0.6f);
		assertTrue(em(3.1f).palpebras() < 0.1f, "piscada");
		assertEquals(1, em(3.9f).palpebras(), 1e-6);
	}

	@Test
	void levantaACabecaEOControleVoltaNoFim() {
		assertEquals(1, em(2.0f).deitado(), 1e-6, "ainda na carteira");
		float meio = em(3.4f).deitado();
		assertTrue(meio > 0.2f && meio < 0.8f, "levantando: " + meio);
		Despertar d = em(Despertar.ANDAR + 0.01f);
		assertEquals(0, d.deitado(), 1e-6);
		assertEquals(0, d.desfoque(), 1e-6);
		assertTrue(d.podeOlhar() && d.podeAndar());
		assertFalse(d.terminou());
		d.atualizar(1);
		assertTrue(d.terminou());
	}

	@Test
	void pularVaiDiretoAoFim() {
		Despertar d = em(1);
		d.pular();
		assertTrue(d.terminou());
		assertEquals(1, d.palpebras(), 1e-6);
		assertEquals(0, d.deitado(), 1e-6);
	}
}
