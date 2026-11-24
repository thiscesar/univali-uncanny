package audio;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SintetizadorVozTest {

	private static double rms(short[] s, int de, int ate) {
		double soma = 0;
		for (int i = de; i < ate; i++) soma += (double) s[i] * s[i];
		return Math.sqrt(soma / Math.max(1, ate - de));
	}

	@Test
	void fraseMaiorFalaMaisTempoEMesmaFraseSaiIgual() {
		short[] curta = Sintetizador.voz(44100, "Hm?", 110, 1);
		short[] longa = Sintetizador.voz(44100, "Elas estão na floresta. A porta da frente está aberta.", 110, 1);
		assertTrue(longa.length > curta.length * 3, "uma sílaba por vogal");
		assertArrayEquals(longa, Sintetizador.voz(44100, "Elas estão na floresta. A porta da frente está aberta.", 110, 1));
	}

	@Test
	void temSomEPausaNoPonto() {
		short[] s = Sintetizador.voz(44100, "Tá bom. Vou corrigir.", 110, 2);
		assertTrue(rms(s, 0, s.length) > 1000, "não é silêncio");
		// em algum lugar há uma pausa de pelo menos 150 ms (depois do ponto)
		int janela = 44100 * 15 / 100;
		boolean pausa = false;
		for (int i = 0; i + janela < s.length - 44100 / 10; i += janela / 4) {
			if (rms(s, i, i + janela) < 200) pausa = true;
		}
		assertTrue(pausa);
	}

	@Test
	void textoSemVogalNaoQuebra() {
		assertTrue(Sintetizador.voz(44100, "...", 110, 3).length > 0);
	}
}
