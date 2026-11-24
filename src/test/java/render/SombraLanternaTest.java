package render;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

class SombraLanternaTest {

	private static Vector4f vezes(Matrix4f m, float x, float y, float z) {
		return Matrix4f.transform(m, new Vector4f(x, y, z, 1), null);
	}

	@Test
	void olharParaPoeOAlvoNoEixoMenosZ() {
		Matrix4f v = SombraLanterna.olharPara(new float[] { 10, 2, 5 }, new float[] { 1, 0, 0 });
		Vector4f p = vezes(v, 15, 2, 5); // 5 m à frente da luz
		assertEquals(0, p.x, 1e-4);
		assertEquals(0, p.y, 1e-4);
		assertEquals(-5, p.z, 1e-4);
		Vector4f acima = vezes(v, 15, 3, 5);
		assertEquals(1, acima.y, 1e-4, "o y de cima continua para cima");
		// olhando reto para baixo não quebra (o "cima" de referência troca)
		Vector4f baixo = vezes(SombraLanterna.olharPara(new float[] { 0, 5, 0 }, new float[] { 0, -1, 0 }), 0, 0, 0);
		assertEquals(-5, baixo.z, 1e-4);
	}

	/**
	 * Com a câmera girada e deslocada e a lanterna fora do centro dos olhos (como presa na arma),
	 * um ponto bem na frente da lanterna cai no centro do mapa, e um ponto fora do cone cai fora.
	 */
	@Test
	void vistaParaLuzLevaOPontoDaFrenteDaLanternaAoCentroDoMapa() {
		Matrix4f visao = new Matrix4f();
		visao.rotate(0.7f, new Vector3f(0, 1, 0));
		visao.rotate(-0.2f, new Vector3f(1, 0, 0));
		visao.translate(new Vector3f(-120, -3, -80));
		float[] pos = { 0.25f, -0.3f, -0.3f }, dir = { 0.05f, 0.02f, -1f };
		float t = (float) Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2]);
		Matrix4f[] m = SombraLanterna.matrizes(visao, pos, dir, 24, 30);
		Matrix4f vistaParaLuz = m[2];

		Vector4f c = vezes(vistaParaLuz, pos[0] + dir[0] / t * 8, pos[1] + dir[1] / t * 8, pos[2] + dir[2] / t * 8);
		assertEquals(0, c.x / c.w, 1e-3);
		assertEquals(0, c.y / c.w, 1e-3);
		assertTrue(c.z / c.w > -1 && c.z / c.w < 1, "dentro do alcance");

		// 40 graus para o lado (o cone vai até 24 + 4): fora do mapa
		double a = Math.toRadians(40);
		Vector4f fora = vezes(vistaParaLuz, pos[0] + (float) Math.sin(a) * 8, pos[1], pos[2] - (float) Math.cos(a) * 8);
		assertTrue(Math.abs(fora.x / fora.w) > 1);
		// mais longe que o alcance: profundidade além de 1
		Vector4f longe = vezes(vistaParaLuz, pos[0], pos[1], pos[2] - 40);
		assertTrue(longe.z / longe.w > 1);
	}

	@Test
	void maisPertoDaLuzTemProfundidadeMenor() {
		Matrix4f vistaParaLuz = SombraLanterna.matrizes(new Matrix4f(), new float[] { 0, 0, 0 }, new float[] { 0, 0, -1 }, 24,
				30)[2];
		Vector4f perto = vezes(vistaParaLuz, 0, 0, -3), longe = vezes(vistaParaLuz, 0, 0, -12);
		assertTrue(perto.z / perto.w < longe.z / longe.w);
	}
}
