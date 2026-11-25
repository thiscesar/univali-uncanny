package jogo;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TutorialTest {

	private static void passar(Tutorial t, float segundos) {
		for (float s = 0; s < segundos; s += 0.05f) t.atualizar(0.05f);
	}

	@Test
	void primeiraDicaApareceEsmaeceESomeDepoisDaAcao() {
		Tutorial t = new Tutorial();
		assertNull(t.texto(), "espera um pouco antes da primeira");
		passar(t, Tutorial.ESPERA + 0.3f);
		assertEquals(Tutorial.DICAS.get(0).texto(), t.texto());
		assertTrue(t.alfa() > 0 && t.alfa() < 1, "ainda aparecendo");
		passar(t, 1);
		assertEquals(1f, t.alfa(), 1e-6);
		t.registrar(Tutorial.Acao.ANDOU);
		passar(t, Tutorial.ESMAECER + 0.1f);
		assertNotEquals(Tutorial.DICAS.get(0).texto(), t.texto());
	}

	@Test
	void dicaDeAlgoJaFeitoNemAparece() {
		Tutorial t = new Tutorial();
		t.registrar(Tutorial.Acao.ANDOU);
		t.registrar(Tutorial.Acao.USOU);
		t.registrar(Tutorial.Acao.ARMOU);
		t.registrar(Tutorial.Acao.LANTERNA);
		passar(t, Tutorial.ESPERA + 1);
		assertEquals("Clique para atirar, R para recarregar", t.texto());
	}

	@Test
	void dicasDaArmaSoDepoisDePegarAEscopetaEDaFlorestaSoDepoisDeSair() {
		Tutorial t = new Tutorial();
		t.registrar(Tutorial.Acao.ANDOU);
		t.registrar(Tutorial.Acao.USOU);
		passar(t, 60);
		assertNull(t.texto(), "sem a escopeta, nada de lanterna nem de tiro");
		assertFalse(t.terminou());
		t.registrar(Tutorial.Acao.ARMOU);
		passar(t, Tutorial.ESPERA + 1);
		assertEquals("F liga e desliga a lanterna", t.texto());
		t.registrar(Tutorial.Acao.LANTERNA);
		passar(t, Tutorial.ESMAECER + Tutorial.ESPERA + 1);
		assertEquals("Clique para atirar, R para recarregar", t.texto());
		t.registrar(Tutorial.Acao.ATIROU);
		passar(t, 30);
		assertNull(t.texto(), "sem sair do prédio, nada da floresta");
		t.registrar(Tutorial.Acao.SAIU);
		passar(t, Tutorial.ESPERA + 1);
		assertEquals("Encontre M1, M2 e M3 na floresta", t.texto());
		passar(t, 20);
		assertTrue(t.terminou());
		assertNull(t.texto());
	}
}
