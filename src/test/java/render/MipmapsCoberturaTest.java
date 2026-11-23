package render;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import mundo.TexturaArvores;

class MipmapsCoberturaTest {

	private static float cobertura(int[] argb) {
		int passa = 0;
		for (int c : argb) if ((c >>> 24) >= 128) passa++;
		return (float) passa / argb.length;
	}

	/** "Folhagem": manchinhas opacas de 2 px de raio espalhadas, com buracos entre elas, como uma copa. */
	private static BufferedImage folhagem() {
		BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
		Random r = new Random(1);
		for (int i = 0; i < 260; i++) {
			int cx = r.nextInt(128), cy = r.nextInt(128);
			for (int y = -2; y <= 2; y++) for (int x = -2; x <= 2; x++) {
				if (x * x + y * y <= 4) img.setRGB(Math.floorMod(cx + x, 128), Math.floorMod(cy + y, 128), 0xFF204020);
			}
		}
		return img;
	}

	@Test
	void geraTodosOsNiveisAte1x1() {
		List<MipmapsCobertura.Nivel> niveis = MipmapsCobertura.gerar(new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB));
		assertEquals(7, niveis.size()); // 64x32, 32x16, 16x8, 8x4, 4x2, 2x1, 1x1
		assertEquals(1, niveis.get(6).largura());
		assertEquals(1, niveis.get(6).altura());
	}

	@Test
	void mantemACoberturaDoAlfaEmQueAMediaSimplesPerderia() {
		BufferedImage img = folhagem();
		float original = cobertura(img.getRGB(0, 0, 128, 128, null, 0, 128));
		List<MipmapsCobertura.Nivel> niveis = MipmapsCobertura.gerar(img);
		int[] semAjuste = img.getRGB(0, 0, 128, 128, null, 0, 128);
		int lado = 128;
		for (int i = 1; i <= 4; i++) {
			assertEquals(original, cobertura(niveis.get(i).argb()), 0.06, "nível " + i);
			semAjuste = MipmapsCobertura.reduzir(semAjuste, lado, lado, lado / 2, lado / 2);
			lado /= 2;
		}
		// sem o ajuste, quando as manchinhas ficam menores que um pixel a média cai e a copa "derrete"
		assertTrue(cobertura(semAjuste) < original * 0.6, "sem ajuste: " + cobertura(semAjuste) + " de " + original);
	}

	@Test
	void corDosPixelsTransparentesNaoEscureceABorda() {
		// metade verde opaca, metade preta transparente: a média ponderada pelo alfa continua verde
		int[] argb = { 0xFF00C000, 0x00000000, 0xFF00C000, 0x00000000 };
		int c = MipmapsCobertura.reduzir(argb, 2, 2, 1, 1)[0];
		assertEquals(0xC0, (c >> 8) & 0xFF);
	}

	@Test
	void atlasSubstitutoTemAs4VariacoesComRecorte() {
		BufferedImage atlas = TexturaArvores.atlas();
		assertEquals(TexturaArvores.LARGURA_PEDACO * 4, atlas.getWidth());
		for (int i = 0; i < 4; i++) {
			int x0 = i * TexturaArvores.LARGURA_PEDACO;
			assertEquals(0, atlas.getRGB(x0 + 2, 2) >>> 24, "canto transparente na variação " + i);
			assertEquals(255, atlas.getRGB(x0 + TexturaArvores.LARGURA_PEDACO / 2, TexturaArvores.ALTURA_PEDACO - 5) >>> 24,
					"tronco opaco embaixo, no meio, na variação " + i);
		}
	}
}
