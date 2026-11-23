package render;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class LanternaTest {

	private static float comprimento(float[] v) {
		return (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
	}

	@Test
	void nuncaFicaExatamenteNosOlhos() {
		Lanterna l = new Lanterna();
		l.atualizar(new Camera(0, 0, 0, 0, 0), 0.016f);
		assertTrue(comprimento(l.posicaoVista()) > 0.2f);
		assertTrue(l.posicaoVista()[0] > 0, "à direita");
		assertTrue(l.posicaoVista()[1] < 0, "abaixo");
	}

	@Test
	void comACameraParadaApontaParaFrente() {
		Lanterna l = new Lanterna();
		Camera c = new Camera(0, 0, 0, 0.7f, -0.2f);
		for (int i = 0; i < 60; i++) l.atualizar(c, 0.016f);
		float[] d = l.direcaoVista();
		assertEquals(1f, comprimento(d), 1e-4);
		assertTrue(d[2] < -0.99f, "quase -z, a frente da câmera em espaço da câmera");
	}

	@Test
	void aoGirarRapidoFicaParaTrasEDepoisAlcanca() {
		Lanterna l = new Lanterna();
		Camera c = new Camera(0, 0, 0, 0, 0);
		l.atualizar(c, 0.016f);
		c.girar((float) Math.toRadians(60), 0); // giro brusco para a direita
		l.atualizar(c, 0.016f);
		assertTrue(l.direcaoVista()[0] < -0.3f, "logo depois do giro, a luz ainda aponta para a esquerda da tela");
		for (int i = 0; i < 60; i++) l.atualizar(c, 0.016f);
		assertTrue(l.direcaoVista()[2] < -0.99f, "um segundo depois, voltou para o centro");
	}

	@Test
	void desligadaTemIntensidadeZeroELigadaTremeUmPouco() {
		Lanterna l = new Lanterna();
		Camera c = new Camera(0, 0, 0, 0, 0);
		float menor = Float.MAX_VALUE, maior = 0;
		for (int i = 0; i < 200; i++) {
			l.atualizar(c, 0.016f);
			menor = Math.min(menor, l.intensidade());
			maior = Math.max(maior, l.intensidade());
		}
		assertTrue(maior > menor, "tremor");
		assertTrue(menor > Lanterna.INTENSIDADE * 0.9f, "mas sutil");
		l.alternar();
		assertFalse(l.ligada());
		assertEquals(0f, l.intensidade());
	}

	@Test
	void aplicarCopiaParaOAmbiente() {
		Lanterna l = new Lanterna();
		l.atualizar(new Camera(0, 0, 0, 0, 0), 0.016f);
		Ambiente a = new Ambiente();
		l.aplicar(a);
		assertEquals(l.intensidade(), a.intensidadeLanterna);
		assertArrayEquals(l.posicaoVista(), a.posLanterna);
		assertArrayEquals(l.direcaoVista(), a.dirLanterna);
	}
}
