package render;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

/** Testa as imagens geradas pelo menu (sem OpenGL). */
class AtmosferaMenuTest {

	private static int alfa(BufferedImage img, int x, int y) {
		return img.getRGB(x, y) >>> 24;
	}

	@Test
	void vinhetaETransparenteNoCentroEEscuraNosCantos() {
		BufferedImage v = AtmosferaMenu.gerarVinheta(64);
		assertEquals(0, alfa(v, 32, 32));
		assertTrue(alfa(v, 0, 0) > 200);
	}

	@Test
	void neblinaSeRepeteSemEmenda() {
		int n = 64;
		BufferedImage nb = AtmosferaMenu.gerarNeblina(n, 7);
		// a última coluna precisa "continuar" na primeira: a diferença entre elas é
		// parecida com a diferença entre duas colunas vizinhas quaisquer
		double emenda = 0, vizinhas = 0;
		for (int y = 0; y < n; y++) {
			emenda += Math.abs(alfa(nb, n - 1, y) - alfa(nb, 0, y));
			vizinhas += Math.abs(alfa(nb, n / 2, y) - alfa(nb, n / 2 + 1, y));
		}
		assertTrue(emenda < vizinhas * 3 + n, "emenda visível: " + emenda + " contra " + vizinhas);
	}

	@Test
	void degradeReservaVaiDoEscuroAoMenosEscuro() {
		BufferedImage d = AtmosferaMenu.gerarDegrade(4, 16);
		assertTrue((d.getRGB(0, 15) & 0xFF) > (d.getRGB(0, 0) & 0xFF));
	}
}
