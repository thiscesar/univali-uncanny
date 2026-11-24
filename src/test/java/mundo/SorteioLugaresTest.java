package mundo;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

class SorteioLugaresTest {

	/** 301 x 301 m, plano a 1 m, com um lago no quadrado (100..140, 100..140). */
	private static ConsultaAltura chao() {
		int[][] cm = new int[301][301];
		for (int z = 0; z < 301; z++) {
			for (int x = 0; x < 301; x++) {
				cm[z][x] = x >= 100 && x <= 140 && z >= 100 && z <= 140 ? -200 : 100;
			}
		}
		return new ConsultaAltura(cm, 1f, 0.01f);
	}

	private static final float[] INICIO = { 150, 150 };

	@Test
	void univaliLongeDoInicioEDaBorda() {
		ConsultaAltura c = chao();
		for (int s = 0; s < 10; s++) {
			float[] u = SorteioLugares.univali(c, INICIO, new Random(s));
			assertTrue(Math.hypot(u[0] - 150, u[1] - 150) >= SorteioLugares.DISTANCIA_UNIVALI_INICIO);
			assertTrue(c.dentroDoMapa(u[0], u[1], SorteioLugares.MARGEM_UNIVALI));
			assertFalse(c.ehAgua(u[0], u[1]));
		}
	}

	@Test
	void provasEspalhadasEmChaoValido() {
		ConsultaAltura c = chao();
		for (int s = 0; s < 10; s++) {
			Random r = new Random(s);
			float[] u = SorteioLugares.univali(c, INICIO, r);
			List<float[]> p = SorteioLugares.provas(c, INICIO, u, 3, r);
			assertEquals(3, p.size());
			for (int i = 0; i < 3; i++) {
				assertFalse(c.ehAgua(p.get(i)[0], p.get(i)[1]), "prova no lago");
				assertTrue(Math.hypot(p.get(i)[0] - 150, p.get(i)[1] - 150) >= SorteioLugares.DISTANCIA_ENTRE_PROVAS);
				assertTrue(Math.hypot(p.get(i)[0] - u[0], p.get(i)[1] - u[1]) >= SorteioLugares.DISTANCIA_PROVA_UNIVALI);
				for (int j = i + 1; j < 3; j++) {
					assertTrue(Math.hypot(p.get(i)[0] - p.get(j)[0], p.get(i)[1] - p.get(j)[1])
							>= SorteioLugares.DISTANCIA_ENTRE_PROVAS);
				}
			}
		}
	}

	@Test
	void mesmaSeedMesmosLugares() {
		ConsultaAltura c = chao();
		Random a = new Random(42), b = new Random(42);
		float[] ua = SorteioLugares.univali(c, INICIO, a), ub = SorteioLugares.univali(c, INICIO, b);
		assertArrayEquals(ua, ub);
		List<float[]> pa = SorteioLugares.provas(c, INICIO, ua, 3, a), pb = SorteioLugares.provas(c, INICIO, ub, 3, b);
		for (int i = 0; i < 3; i++) assertArrayEquals(pa.get(i), pb.get(i));
	}

	@Test
	void mapaApertadoAindaDaUmaResposta() {
		int[][] cm = new int[101][101];
		for (int[] l : cm) java.util.Arrays.fill(l, 100);
		ConsultaAltura pequeno = new ConsultaAltura(cm, 1f, 0.01f);
		List<float[]> p = SorteioLugares.provas(pequeno, new float[] { 50, 50 }, null, 3, new Random(1));
		assertEquals(3, p.size(), "60 m não cabem num mapa de 100 m: a distância exigida diminui");
	}
}
