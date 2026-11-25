package mundo;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class GradeColisaoTest {

	@Test
	void colideSoQuandoOsCirculosSeTocam() {
		GradeColisao g = new GradeColisao(100);
		g.adicionar(10, 10, 0.5f);
		assertTrue(g.colide(10.7f, 10, 0.35f));
		assertFalse(g.colide(10.9f, 10, 0.35f));
		assertFalse(g.colide(50, 50, 0.35f));
	}

	@Test
	void achaObstaculoNaCelulaVizinha() {
		GradeColisao g = new GradeColisao(100);
		g.adicionar(7.9f, 10, 0.4f);              // célula de x de 4 a 8
		assertTrue(g.colide(8.3f, 10, 0.35f));    // consulta na célula de x de 8 a 12
	}

	@Test
	void resolverEmpurraParaForaNaDirecaoCerta() {
		GradeColisao g = new GradeColisao(100);
		g.adicionar(10, 10, 0.5f);
		float[] p = g.resolver(10.3f, 10, 0.35f);
		assertEquals(10.85f, p[0], 1e-4);
		assertEquals(10f, p[1], 1e-4);
		assertFalse(g.colide(p[0], p[1], 0.349f));
	}

	@Test
	void semObstaculoNaoMexe() {
		GradeColisao g = new GradeColisao(100);
		float[] p = g.resolver(33, 44, 0.35f);
		assertEquals(33, p[0], 0);
		assertEquals(44, p[1], 0);
		assertEquals(0, g.quantidade());
	}

	@Test
	void linhaLivreSoSeNenhumTroncoCortaOCaminho() {
		GradeColisao g = new GradeColisao(100);
		g.adicionar(50, 50, 0.35f);
		assertFalse(g.linhaLivre(40, 50, 60, 50), "passa pelo centro do tronco");
		assertFalse(g.linhaLivre(40, 50.3f, 60, 50.3f), "raspa no tronco");
		assertTrue(g.linhaLivre(40, 50.5f, 60, 50.5f), "passa ao lado");
		assertTrue(g.linhaLivre(40, 50, 49, 50), "termina antes do tronco");
	}

	@Test
	void linhaLivreAchaTroncoLongeDasPontas() {
		GradeColisao g = new GradeColisao(100);
		g.adicionar(30, 30, 0.35f);
		assertFalse(g.linhaLivre(10, 10, 50, 50), "diagonal longa, tronco no meio");
	}

	@Test
	void distanciaAoSegmento() {
		assertEquals(1f, GradeColisao.distanciaAoSegmento(5, 1, 0, 0, 10, 0), 1e-5);
		assertEquals(5f, GradeColisao.distanciaAoSegmento(13, 4, 0, 0, 10, 0), 1e-5, "além da ponta: mede até a ponta");
	}

	@Test
	void retanguloBloqueiaEmpurraEFechaALinha() {
		GradeColisao g = new GradeColisao(100);
		g.adicionarRetangulo(40, 40, 60, 50);
		assertTrue(g.colide(50, 45, 0.3f), "dentro");
		assertTrue(g.colide(60.2f, 45, 0.35f), "encostado na parede");
		assertFalse(g.colide(61, 45, 0.35f));
		float[] fora = g.resolver(59.8f, 45, 0.35f); // entrou um pouco pela parede leste
		assertEquals(60.35f, fora[0], 1e-4f);
		float[] canto = g.resolver(60.1f, 50.1f, 0.35f); // perto do canto: afasta na diagonal
		assertFalse(g.colide(canto[0], canto[1], 0.349f));
		assertFalse(g.linhaLivre(30, 45, 70, 45), "a linha atravessa o prédio");
		assertTrue(g.linhaLivre(30, 55, 70, 55));
		assertFalse(g.linhaLivre(30, 51, 70, 51, 1.2f), "passa perto demais com a folga");
	}

	@Test
	void movelBaixoSeguraOAndarMasNaoOTiroNemAVista() {
		GradeColisao g = new GradeColisao(50);
		g.adicionarRetangulo(new GradeColisao.Retangulo(10, 10, 12, 12, 1.2f)); // um balcão de 1,2 m
		assertTrue(g.colide(11, 11, 0.3f), "ninguém anda através dele");
		assertTrue(g.colideNaAltura(11, 1.0f, 11), "abaixo do topo, o tiro bate");
		assertFalse(g.colideNaAltura(11, 1.5f, 11), "acima do topo, o tiro passa");
		assertFalse(g.linhaLivre(5, 11, 20, 11), "no chão, ele bloqueia");
		assertTrue(g.linhaLivreNaAltura(5, 11, 20, 11, 0, 1.6f), "na altura dos olhos, dá para ver por cima");
		g.adicionarRetangulo(30, 10, 32, 12); // uma parede: infinita
		assertTrue(g.colideNaAltura(31, 100, 11));
	}
}
