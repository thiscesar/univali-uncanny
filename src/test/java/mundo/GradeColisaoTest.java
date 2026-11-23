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
}
