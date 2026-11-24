package render;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

class EscorridosTest {

	/** Duas "letras": barras verticais terminando em alturas diferentes. */
	private static BufferedImage duasBarras() {
		BufferedImage img = new BufferedImage(100, 60, BufferedImage.TYPE_INT_ARGB);
		for (int y = 5; y < 40; y++) for (int x = 10; x < 20; x++) img.setRGB(x, y, 0xFFFF0000);
		for (int y = 5; y < 50; y++) for (int x = 60; x < 75; x++) img.setRGB(x, y, 0xFFFF0000);
		return img;
	}

	@Test
	void escorridosNascemNaBordaDeBaixoDasLetras() {
		List<Escorridos.Escorrido> lista = Escorridos.gerar(duasBarras(), 10, new Random(1));
		assertFalse(lista.isEmpty());
		for (Escorridos.Escorrido e : lista) {
			float centro = e.x() + e.largura() / 2;
			boolean naPrimeira = centro >= 10 && centro < 20, naSegunda = centro >= 60 && centro < 75;
			assertTrue(naPrimeira || naSegunda, "fora das letras: " + centro);
			float borda = naPrimeira ? 39 : 49;
			assertEquals(borda - e.largura(), e.y(), 1e-4f); // começa um pouco para dentro da letra
		}
	}

	@Test
	void naoFicamColadosUnsNosOutros() {
		List<Escorridos.Escorrido> lista = Escorridos.gerar(duasBarras(), 10, new Random(2));
		for (int i = 0; i < lista.size(); i++) {
			for (int j = i + 1; j < lista.size(); j++) {
				Escorridos.Escorrido a = lista.get(i), b = lista.get(j);
				assertTrue(Math.abs(a.x() - b.x()) >= 2 * Math.max(a.largura(), b.largura()) - 1e-3f);
			}
		}
	}

	@Test
	void descemComOTempoAteOComprimento() {
		Escorridos.Escorrido e = new Escorridos.Escorrido(0, 0, 2, 30, 0.5f, 2f);
		assertEquals(0f, e.descido(0.4f));
		assertTrue(e.descido(1f) > 0 && e.descido(1f) < 30);
		assertTrue(e.descido(1.5f) > e.descido(1f));
		assertEquals(30f, e.descido(3f), 1e-4f);
	}

	@Test
	void imagemVaziaNaoTemEscorrido() {
		assertTrue(Escorridos.gerar(new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB), 5, new Random(3)).isEmpty());
	}
}
