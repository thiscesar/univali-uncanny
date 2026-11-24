package jogo;

import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import jogo.SustosFalsos.Lugar;
import jogo.SustosFalsos.Tipo;

class SustosFalsosTest {

	/** Uma hora de jogo, a 20 passos por segundo: quantos de cada susto, e quando veio o primeiro. */
	private static Map<Tipo, Integer> umaHora(SustosFalsos s, Lugar lugar, float[] primeiro) {
		Map<Tipo, Integer> conta = new EnumMap<>(Tipo.class);
		float t = 0;
		primeiro[0] = -1;
		for (int i = 0; i < 20 * 60 * 60; i++) {
			t += 0.05f;
			Tipo tipo = s.atualizar(0.05f, lugar, false);
			if (tipo == null) continue;
			if (primeiro[0] < 0) primeiro[0] = t;
			conta.merge(tipo, 1, Integer::sum);
		}
		return conta;
	}

	@Test
	void nadaForaDosLugaresNemComOMonstroEmCena() {
		SustosFalsos s = new SustosFalsos(1);
		for (int i = 0; i < 10000; i++) {
			assertNull(s.atualizar(0.1f, null, false));
			assertNull(s.atualizar(0.1f, Lugar.FLORESTA, true));
		}
	}

	@Test
	void naFlorestaOsQuatroTiposNemRaroDemaisNemFrequenteDemais() {
		float[] primeiro = new float[1];
		Map<Tipo, Integer> conta = umaHora(new SustosFalsos(7), Lugar.FLORESTA, primeiro);
		assertEquals(SustosFalsos.CARENCIA, primeiro[0], 0.1f);
		int total = conta.values().stream().mapToInt(Integer::intValue).sum();
		assertTrue(total >= 3600 / SustosFalsos.INTERVALO_MAXIMO && total <= 3600 / SustosFalsos.INTERVALO_MINIMO + 1,
				"sustos numa hora: " + total);
		for (Tipo t : Tipo.values()) assertTrue(conta.getOrDefault(t, 0) > 0, "nenhum " + t + ": " + conta);
		assertTrue(conta.get(Tipo.FANTASMA) > conta.get(Tipo.RISADA), "o fantasma é o mais comum");
	}

	@Test
	void naUnivaliSoOFantasma() {
		Map<Tipo, Integer> conta = umaHora(new SustosFalsos(3), Lugar.PREDIO, new float[1]);
		assertEquals(java.util.Set.of(Tipo.FANTASMA), conta.keySet());
	}

	@Test
	void forcadoSaiNaProximaVezQuePuder() {
		SustosFalsos s = new SustosFalsos(3);
		s.forcar(Tipo.GALHO);
		assertNull(s.atualizar(0.1f, null, false), "fora dos lugares, espera");
		assertEquals(Tipo.GALHO, s.atualizar(0.1f, Lugar.FLORESTA, false));
		assertNull(s.atualizar(0.1f, Lugar.FLORESTA, false), "só uma vez");
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
