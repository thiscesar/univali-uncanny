package render;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;

import org.junit.jupiter.api.Test;

class PiscadasTest {

	@Test
	void comecaAcesaEEmAlgunsSegundosPisca() {
		Piscadas p = new Piscadas(new Random(1));
		assertEquals(0f, p.escuridao());

		boolean piscou = false;
		for (int i = 0; i < 60 * 20; i++) { // 20 s a 60 quadros por segundo
			p.atualizar(1 / 60f);
			float e = p.escuridao();
			assertTrue(e >= 0 && e <= 0.9f);
			if (e > 0) piscou = true;
		}
		assertTrue(piscou, "em 20 s precisa haver pelo menos uma crise (o intervalo máximo é 14 s)");
	}

	@Test
	void naMaiorParteDoTempoATelaFicaAcesa() {
		Piscadas p = new Piscadas(new Random(2));
		int escuros = 0, total = 60 * 60;
		for (int i = 0; i < total; i++) {
			p.atualizar(1 / 60f);
			if (p.escuridao() > 0) escuros++;
		}
		assertTrue(escuros < total * 0.1, "piscadas são raras");
	}
}
