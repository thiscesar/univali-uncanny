package render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector4f;

/** Confere a matriz ortográfica: pixels (origem em cima, à esquerda) viram coordenadas da tela. */
class Overlay2DTest {

	private static Vector4f aplicar(Matrix4f m, float x, float y) {
		return Matrix4f.transform(m, new Vector4f(x, y, 0, 1), null);
	}

	@Test
	void cantoSuperiorEsquerdoViraMenosUmMaisUm() {
		Vector4f p = aplicar(Overlay2D.ortografica(800, 600), 0, 0);
		assertEquals(-1f, p.x, 1e-6);
		assertEquals(1f, p.y, 1e-6);
	}

	@Test
	void cantoInferiorDireitoViraMaisUmMenosUm() {
		Vector4f p = aplicar(Overlay2D.ortografica(800, 600), 800, 600);
		assertEquals(1f, p.x, 1e-6);
		assertEquals(-1f, p.y, 1e-6);
	}

	@Test
	void centroDaJanelaViraOrigem() {
		Vector4f p = aplicar(Overlay2D.ortografica(800, 600), 400, 300);
		assertEquals(0f, p.x, 1e-6);
		assertEquals(0f, p.y, 1e-6);
	}
}
