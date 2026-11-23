package render;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

/** Testa só a geração da imagem do texto (Java2D), que não usa OpenGL. */
class TextoTexturaTest {

	private final Font fonte = new Font(Font.SANS_SERIF, Font.PLAIN, 32);

	@Test
	void textoComAcentosGeraPixelsVisiveis() {
		BufferedImage img = TextoTextura.gerarImagem("Avaliação", fonte, Color.WHITE, false, false);
		int visiveis = 0;
		for (int y = 0; y < img.getHeight(); y++) {
			for (int x = 0; x < img.getWidth(); x++) {
				if ((img.getRGB(x, y) >>> 24) > 0) visiveis++;
			}
		}
		assertTrue(visiveis > 100, "as letras precisam aparecer");
	}

	@Test
	void fundoETransparenteMasComACorDoTexto() {
		Color vermelho = new Color(200, 30, 30);
		BufferedImage img = TextoTextura.gerarImagem("M1", fonte, vermelho, false, false);
		int canto = img.getRGB(0, 0);
		assertEquals(0, canto >>> 24, "fundo transparente");
		assertEquals(vermelho.getRGB() & 0xFFFFFF, canto & 0xFFFFFF, "sem franja escura nas bordas");
	}

	@Test
	void textoMaiorGeraImagemMaisLarga() {
		int curta = TextoTextura.gerarImagem("Oi", fonte, Color.WHITE, false, false).getWidth();
		int longa = TextoTextura.gerarImagem("Carregando mapa...", fonte, Color.WHITE, false, false).getWidth();
		assertTrue(longa > curta);
	}

	@Test
	void contornoESombraAumentamAImagem() {
		BufferedImage simples = TextoTextura.gerarImagem("UNIVALI", fonte, Color.WHITE, false, false);
		BufferedImage enfeitada = TextoTextura.gerarImagem("UNIVALI", fonte, Color.WHITE, true, true);
		assertTrue(enfeitada.getWidth() > simples.getWidth());
		assertTrue(enfeitada.getHeight() > simples.getHeight());
	}
}
