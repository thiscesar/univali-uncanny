package jogo;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SustosFalsosTest {

	@Test
	void nadaDentroDoPredioNemComOMonstroEmCena() {
		SustosFalsos s = new SustosFalsos(1);
		for (int i = 0; i < 10000; i++) {
			assertNull(s.atualizar(0.1f, false, false));
			assertNull(s.atualizar(0.1f, true, true));
		}
	}

	@Test
	void rarosEDosDoisTipos() {
		SustosFalsos s = new SustosFalsos(7);
		int silhuetas = 0, galhos = 0;
		float primeiro = -1, t = 0;
		for (int i = 0; i < 20 * 60 * 60; i++) { // 1 hora a 20 passos por segundo
			t += 0.05f;
			SustosFalsos.Tipo tipo = s.atualizar(0.05f, true, false);
			if (tipo == null) continue;
			if (primeiro < 0) primeiro = t;
			if (tipo == SustosFalsos.Tipo.SILHUETA) silhuetas++;
			else galhos++;
		}
		assertEquals(SustosFalsos.CARENCIA, primeiro, 0.1f);
		int total = silhuetas + galhos;
		assertTrue(total >= 3600 / SustosFalsos.INTERVALO_MAXIMO && total <= 3600 / SustosFalsos.INTERVALO_MINIMO + 1,
				"sustos numa hora: " + total);
		assertTrue(silhuetas > 0 && galhos > 0);
	}

	@Test
	void lanternaIluminaSoDentroDoConeEDoAlcance() {
		assertTrue(SustosFalsos.iluminado(true, 0, 0, 0, 1, 1, 15));      // quase em frente
		assertFalse(SustosFalsos.iluminado(false, 0, 0, 0, 1, 1, 15));    // desligada
		assertFalse(SustosFalsos.iluminado(true, 0, 0, 0, 1, 10, 15));    // fora do cone
		assertFalse(SustosFalsos.iluminado(true, 0, 0, 0, 1, 0, 40));     // longe demais
		assertFalse(SustosFalsos.iluminado(true, 0, 0, 0, 1, 0, -15));    // atrás
	}
}
