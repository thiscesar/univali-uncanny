package mundo;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import mundo.DistribuidorArvores.Arvore;
import mundo.DistribuidorArvores.Clareira;

class DistribuidorArvoresTest {

	/** Mapa de 300 m quase plano, com um lago fundo no meio e um barranco a leste. */
	private static ConsultaAltura chao;
	private static List<Arvore> arvores;
	private static final Clareira INICIO = new Clareira(80, 80, 6);

	@BeforeAll
	static void montar() {
		int[][] cm = new int[301][301];
		for (int z = 0; z <= 300; z++) {
			for (int x = 0; x <= 300; x++) {
				double d = Math.hypot(x - 150, z - 150);
				cm[z][x] = d < 20 ? -300 : 200;                    // lago de 20 m de raio
				if (x >= 230 && x <= 250) cm[z][x] = 200 + (x - 230) * 150; // barranco de 56 graus
				if (x > 250) cm[z][x] = 200 + 20 * 150;
			}
		}
		chao = new ConsultaAltura(cm, 1f, 0.01f);
		arvores = DistribuidorArvores.distribuir(chao, 42, List.of(INICIO));
	}

	@Test
	void quantidadeDentroDaMeta() {
		assertTrue(arvores.size() >= 3000 && arvores.size() <= 6000, "árvores: " + arvores.size());
	}

	@Test
	void nenhumaMaisPertoQue2EMeioMetro() {
		GradeColisao g = new GradeColisao(300);
		for (Arvore a : arvores) {
			// cada árvore vira um círculo de raio 1,249 m: se dois se tocassem, estariam a menos de 2,5 m
			assertFalse(g.colide(a.x(), a.z(), 1.249f), "árvore muito perto em " + a.x() + ", " + a.z());
			g.adicionar(a.x(), a.z(), 1.249f);
		}
	}

	@Test
	void nadaNaAguaNoBarrancoNemNaClareira() {
		for (Arvore a : arvores) {
			assertFalse(chao.ehAgua(a.x(), a.z()));
			assertTrue(chao.anguloEm(a.x(), a.z()) <= 32.5f, "declive em " + a.x());
			assertTrue(Math.hypot(a.x() - INICIO.x(), a.z() - INICIO.z()) >= INICIO.raio());
		}
	}

	@Test
	void bordaMaisDensaQueOInterior() {
		int borda = 0, interior = 0;
		for (Arvore a : arvores) {
			float d = Math.min(Math.min(a.x(), a.z()), Math.min(300 - a.x(), 300 - a.z()));
			if (d < 18) borda++; else interior++;
		}
		double areaBorda = 300.0 * 300 - 264.0 * 264, areaInterior = 264.0 * 264;
		assertTrue(borda / areaBorda > 2 * interior / areaInterior, "a borda é uma muralha");
	}

	@Test
	void mesmaSeedMesmasArvores() {
		assertEquals(arvores, DistribuidorArvores.distribuir(chao, 42, List.of(INICIO)));
		assertNotEquals(arvores, DistribuidorArvores.distribuir(chao, 43, List.of(INICIO)));
	}

	@Test
	void tamanhosETexturasNasFaixas() {
		for (Arvore a : arvores) {
			assertTrue(a.altura() >= 8 && a.altura() <= 16);
			assertTrue(a.textura() >= 0 && a.textura() < DistribuidorArvores.VARIACOES);
			assertEquals(chao.alturaEm(a.x(), a.z()) - 0.15f, a.y(), 1e-4, "apoiada no chão");
		}
	}

	@Test
	void troncosEntramNaGradeDeColisao() {
		GradeColisao g = new GradeColisao(300);
		DistribuidorArvores.registrarTroncos(arvores, g);
		assertEquals(arvores.size(), g.quantidade());
		Arvore a = arvores.get(0);
		assertTrue(g.colide(a.x() + 0.5f, a.z(), 0.35f));
	}
}
