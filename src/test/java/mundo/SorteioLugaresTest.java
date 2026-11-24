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

	@Test
	void placasDeTrilhaApontamParaAProva() {
		ConsultaAltura c = chao();
		Random r = new Random(3);
		float[] u = SorteioLugares.univali(c, INICIO, r);
		List<float[]> provas = SorteioLugares.provas(c, INICIO, u, 3, r);
		List<SorteioLugares.Placa> placas = SorteioLugares.placas(c, INICIO, provas, r);
		assertEquals(SorteioLugares.PLACAS_EVENTO, placas.stream().filter(SorteioLugares.Placa::evento).count());
		assertEquals(3 * SorteioLugares.PLACAS_TRILHA_POR_PROVA, placas.stream().filter(p -> !p.evento()).count());
		for (SorteioLugares.Placa p : placas) {
			assertFalse(c.ehAgua(p.x(), p.z()));
			if (p.evento()) continue;
			float[] prova = provas.get(Integer.parseInt(p.texto().substring(1)) - 1);
			float dx = prova[0] - p.x(), dz = prova[1] - p.z(), d = (float) Math.hypot(dx, dz);
			assertTrue(d >= SorteioLugares.TRILHA_MINIMA && d <= SorteioLugares.TRILHA_MAXIMA, "distância " + d);
			// a seta (o +x do modelo girado) aponta para a prova
			float sx = (float) Math.cos(p.giro()), sz = (float) -Math.sin(p.giro());
			assertEquals(1f, (sx * dx + sz * dz) / d, 1e-4f);
		}
	}

	@Test
	void primeiraPlacaDoEventoPertoDoInicioEVirada() {
		ConsultaAltura c = chao();
		SorteioLugares.Placa p = SorteioLugares.placas(c, INICIO, List.of(), new Random(9)).get(0);
		assertTrue(p.evento());
		assertEquals(10, Math.hypot(p.x() - 150, p.z() - 150), 0.01);
		// a frente (+z do modelo girado) olha para o início
		float fx = (float) Math.sin(p.giro()), fz = (float) Math.cos(p.giro());
		float dx = 150 - p.x(), dz = 150 - p.z(), d = (float) Math.hypot(dx, dz);
		assertEquals(1f, (fx * dx + fz * dz) / d, 1e-4f);
	}
}
