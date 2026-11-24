package jogo;

import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import jogo.SustosFalsos.Tipo;

class SustosFalsosTest {

	@Test
	void nadaForaDaFlorestaNemComOMonstroEmCena() {
		SustosFalsos s = new SustosFalsos(1);
		for (int i = 0; i < 10000; i++) {
			assertNull(s.atualizar(0.1f, false, false));
			assertNull(s.atualizar(0.1f, true, true));
		}
	}

	@Test
	void naFlorestaOsTresTiposNemRaroDemaisNemFrequenteDemais() {
		SustosFalsos s = new SustosFalsos(7);
		Map<Tipo, Integer> conta = new EnumMap<>(Tipo.class);
		float t = 0, primeiro = -1;
		for (int i = 0; i < 20 * 60 * 60; i++) { // 1 hora a 20 passos por segundo
			t += 0.05f;
			Tipo tipo = s.atualizar(0.05f, true, false);
			if (tipo == null) continue;
			if (primeiro < 0) primeiro = t;
			conta.merge(tipo, 1, Integer::sum);
		}
		assertEquals(SustosFalsos.CARENCIA, primeiro, 0.1f);
		int total = conta.values().stream().mapToInt(Integer::intValue).sum();
		assertTrue(total >= 3600 / SustosFalsos.INTERVALO_MAXIMO && total <= 3600 / SustosFalsos.INTERVALO_MINIMO + 1,
				"sustos numa hora: " + total);
		for (Tipo tipo : Tipo.values()) assertTrue(conta.getOrDefault(tipo, 0) > 0, "nenhum " + tipo + ": " + conta);
	}

	@Test
	void forcadoSaiNaProximaVezQuePuder() {
		SustosFalsos s = new SustosFalsos(3);
		s.forcar(Tipo.GALHO);
		assertNull(s.atualizar(0.1f, false, false), "fora da floresta, espera");
		assertEquals(Tipo.GALHO, s.atualizar(0.1f, true, false));
		assertNull(s.atualizar(0.1f, true, false), "só uma vez");
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
