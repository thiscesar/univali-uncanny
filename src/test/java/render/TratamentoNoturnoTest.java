package render;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

class TratamentoNoturnoTest {

	/** Imagem 1 x 10 de uma cor só (para testar no alto e embaixo da foto). */
	private static BufferedImage cor(int rgb) {
		BufferedImage img = new BufferedImage(1, 10, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < 10; y++) img.setRGB(0, y, rgb);
		return img;
	}

	private static int brilho(int argb) {
		return ((argb >> 16) & 0xFF) + ((argb >> 8) & 0xFF) + (argb & 0xFF);
	}

	@Test
	void ceuAzulNoAltoViraQuasePreto() {
		BufferedImage noite = TratamentoNoturno.aplicar(cor(0x1E78BE)); // azul do céu da foto
		assertTrue(brilho(noite.getRGB(0, 0)) < 30);
	}

	@Test
	void brancoNeutroContinuaMaisClaroQueOCinza() {
		int branco = brilho(TratamentoNoturno.aplicar(cor(0xF0F0F0)).getRGB(0, 5));
		int cinza = brilho(TratamentoNoturno.aplicar(cor(0x909090)).getRGB(0, 5));
		assertTrue(branco > cinza * 2, "a placa branca precisa parecer iluminada");
	}

	@Test
	void tudoFicaMaisEscuroQueOOriginal() {
		int original = brilho(0xC08060);
		int noite = brilho(TratamentoNoturno.aplicar(cor(0xC08060)).getRGB(0, 9));
		assertTrue(noite < original / 2);
	}

	@Test
	void mantemOTamanho() {
		BufferedImage foto = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
		BufferedImage noite = TratamentoNoturno.aplicar(foto);
		assertEquals(40, noite.getWidth());
		assertEquals(30, noite.getHeight());
	}
}
