package mundo;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class DistribuidorPedrasTest {

	/** 301 x 301 m, plano a 1 m, com um lago no quadrado (100..140, 100..140). */
	private static ConsultaAltura chao() {
		int[][] cm = new int[301][301];
		for (int z = 0; z < 301; z++) {
			for (int x = 0; x < 301; x++) {
				boolean lago = x >= 100 && x <= 140 && z >= 100 && z <= 140;
				boolean margem = x >= 96 && x <= 144 && z >= 96 && z <= 144;
				cm[z][x] = lago ? -200 : margem ? 20 : 100;
			}
		}
		return new ConsultaAltura(cm, 1f, 0.01f);
	}

	private static final float[] LARGURAS = { 0.8f, 1.6f, 1.0f, 1.5f };

	@Test
	void forDaAguaEDasClareiras() {
		ConsultaAltura c = chao();
		List<DistribuidorArvores.Clareira> clareiras = List.of(new DistribuidorArvores.Clareira(200, 200, 10));
		List<DistribuidorPedras.Pedra> pedras = DistribuidorPedras.distribuir(c, LARGURAS, clareiras, 1);
		assertTrue(pedras.size() > 300, "quantidade " + pedras.size());
		for (DistribuidorPedras.Pedra p : pedras) {
			assertFalse(c.ehAgua(p.x(), p.z()), "pedra na água");
			assertTrue(Math.hypot(p.x() - 200, p.z() - 200) >= 10, "pedra na clareira");
		}
	}

	@Test
	void temPedrasNaMargemDoLago() {
		List<DistribuidorPedras.Pedra> pedras = DistribuidorPedras.distribuir(chao(), LARGURAS, List.of(), 2);
		long naMargem = pedras.stream().filter(p -> p.x() >= 95 && p.x() <= 145 && p.z() >= 95 && p.z() <= 145).count();
		assertTrue(naMargem > 20, "pedras na margem: " + naMargem);
	}

	@Test
	void grandesSaoSolidasEPedemClareira() {
		List<DistribuidorPedras.Pedra> pedras = DistribuidorPedras.distribuir(chao(), LARGURAS, List.of(), 3);
		long grandes = pedras.stream().filter(p -> p.escala() >= 2.2f).count();
		assertTrue(grandes >= DistribuidorPedras.GRANDES - 3, "grandes: " + grandes);
		assertTrue(pedras.stream().filter(p -> p.escala() >= 2.2f).allMatch(DistribuidorPedras.Pedra::solida));
		GradeColisao grade = new GradeColisao(301);
		DistribuidorPedras.registrar(pedras, LARGURAS, grade);
		DistribuidorPedras.Pedra uma = pedras.stream().filter(p -> p.escala() >= 2.2f).findFirst().orElseThrow();
		assertTrue(grade.colide(uma.x(), uma.z(), 0.1f));
		assertEquals(pedras.stream().filter(DistribuidorPedras.Pedra::solida).count(),
				DistribuidorPedras.clareiras(pedras, LARGURAS).size());
	}

	@Test
	void mesmaSeedMesmasPedras() {
		assertEquals(DistribuidorPedras.distribuir(chao(), LARGURAS, List.of(), 5),
				DistribuidorPedras.distribuir(chao(), LARGURAS, List.of(), 5));
	}
}
