package audio;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SintetizadorInterfaceTest {

	private static int pico(short[] s) {
		int p = 0;
		for (short v : s) p = Math.max(p, Math.abs(v));
		return p;
	}

	@Test
	void sonsDoMenuTemADuracaoEOVolumeCertos() {
		short[] passar = Sintetizador.menuPassar(44100), clique = Sintetizador.menuClique(44100),
				ajuste = Sintetizador.menuAjuste(44100);
		assertEquals(44100 * 90 / 1000, passar.length);
		assertEquals(44100 * 550 / 1000, clique.length);
		assertEquals(44100 * 35 / 1000, ajuste.length);
		// o clique é o mais forte, o tique de ajuste o mais fraco
		assertTrue(pico(clique) > pico(passar) && pico(passar) > pico(ajuste));
	}

	@Test
	void sonsDoMenuTerminamEmSilencio() {
		for (short[] s : new short[][] { Sintetizador.menuPassar(44100), Sintetizador.menuClique(44100),
				Sintetizador.menuAjuste(44100) }) {
			assertEquals(0, s[s.length - 1], "sem estalo no fim");
		}
	}
}
