package mundo;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ConsultaAlturaTest {

	/** Grade 3 x 3 em centímetros, 1 m entre pontos (mapa de 2 x 2 m). Linhas = z, colunas = x. */
	private final ConsultaAltura rampa = new ConsultaAltura(new int[][] {
			{ 0, 100, 200 },
			{ 0, 100, 200 },
			{ 0, 100, 200 },
	}, 1f, 0.01f);

	@Test
	void nosPontosDaGradeDevolveOValorExatoEmMetros() {
		assertEquals(0f, rampa.alturaEm(0, 0), 1e-5);
		assertEquals(1f, rampa.alturaEm(1, 2), 1e-5);
		assertEquals(2f, rampa.alturaEm(2, 1), 1e-5);
	}

	@Test
	void entreOsPontosInterpolaSemDegraus() {
		assertEquals(0.5f, rampa.alturaEm(0.5f, 0.3f), 1e-5);
		assertEquals(1.75f, rampa.alturaEm(1.75f, 1.9f), 1e-5);
	}

	@Test
	void interpolacaoBilinearNosDoisEixos() {
		ConsultaAltura c = new ConsultaAltura(new int[][] { { 0, 0 }, { 0, 400 } }, 1f, 0.01f);
		// no meio, a média dos quatro cantos: (0 + 0 + 0 + 4) / 4 = 1 m
		assertEquals(1f, c.alturaEm(0.5f, 0.5f), 1e-5);
		assertEquals(4f, c.alturaEm(1f, 1f), 1e-5);
	}

	@Test
	void foraDoMapaUsaABordaMaisProxima() {
		assertEquals(0f, rampa.alturaEm(-5, 1), 1e-5);
		assertEquals(2f, rampa.alturaEm(9, 1), 1e-5);
	}

	@Test
	void respeitaOPassoDaGrade() {
		ConsultaAltura c = new ConsultaAltura(new int[][] { { 0, 300 }, { 0, 300 } }, 2f, 0.01f); // 2 m entre pontos
		assertEquals(2f, c.tamanho(), 1e-5);
		assertEquals(1.5f, c.alturaEm(1f, 0f), 1e-5);
	}

	@Test
	void chaoPlanoTemNormalParaCimaEInclinacaoZero() {
		ConsultaAltura plano = new ConsultaAltura(new int[5][5], 1f, 0.01f);
		float[] n = plano.normalEm(2, 2);
		assertEquals(0f, n[0], 1e-5);
		assertEquals(1f, n[1], 1e-5);
		assertEquals(0f, plano.inclinacaoEm(2, 2), 1e-5);
	}

	@Test
	void rampaDe45GrausTemInclinacaoCerta() {
		// sobe 1 m a cada 1 m em x: 45 graus
		assertEquals(45f, rampa.anguloEm(1, 1), 0.01);
		assertEquals(1 - Math.cos(Math.toRadians(45)), rampa.inclinacaoEm(1, 1), 1e-4);
		float[] n = rampa.normalEm(1, 1);
		assertTrue(n[0] < 0, "a normal aponta para o lado que desce (x menor)");
	}

	@Test
	void aguaEProfundidade() {
		ConsultaAltura lago = new ConsultaAltura(new int[][] { { 50, 50 }, { -150, -150 } }, 1f, 0.01f);
		assertFalse(lago.ehAgua(0.5f, 0f));
		assertEquals(0f, lago.profundidadeAgua(0.5f, 0f), 1e-5);
		assertTrue(lago.ehAgua(0.5f, 1f));
		assertEquals(1.5f, lago.profundidadeAgua(0.5f, 1f), 1e-5);
	}

	@Test
	void dentroDoMapaComMargem() {
		ConsultaAltura grande = new ConsultaAltura(new int[301][301], 1f, 0.01f); // 300 m
		assertTrue(grande.dentroDoMapa(150, 150, 10));
		assertTrue(grande.dentroDoMapa(10, 290, 10));
		assertFalse(grande.dentroDoMapa(9.9f, 150, 10));
		assertFalse(grande.dentroDoMapa(150, 295, 10));
		assertFalse(grande.dentroDoMapa(-1, 150, 0));
	}
}
