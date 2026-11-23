package render;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

class GeradorNormalMapTest {

	private static int r(int c) { return (c >> 16) & 0xFF; }
	private static int g(int c) { return (c >> 8) & 0xFF; }
	private static int b(int c) { return c & 0xFF; }

	@Test
	void superficieLisaViraOAzulPadrao() {
		BufferedImage cinza = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) cinza.setRGB(x, y, 0x808080);
		int c = GeradorNormalMap.aPartirDaCor(cinza, 2).getRGB(4, 4);
		assertEquals(128, r(c), 1);
		assertEquals(128, g(c), 1);
		assertEquals(255, b(c), 1);
	}

	@Test
	void subidaParaADireitaInclinaANormalParaAEsquerda() {
		BufferedImage rampa = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
			int v = x * 12; // clareia para a direita = sobe para a direita
			rampa.setRGB(x, y, (v << 16) | (v << 8) | v);
		}
		int c = GeradorNormalMap.aPartirDaCor(rampa, 2).getRGB(8, 8);
		assertTrue(r(c) < 120, "x negativo: a face olha para o lado que desce");
		assertEquals(128, g(c), 1, "sem inclinação em y");
		assertTrue(b(c) > 128, "continua apontando para fora da superfície");
	}
}
