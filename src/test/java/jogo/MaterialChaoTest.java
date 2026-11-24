package jogo;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import mundo.ConsultaAltura;

class MaterialChaoTest {

	/** 60 x 60 m: um lago (altura negativa) em x < 10, plano a 2 m no meio, rampa forte em x > 40. */
	private static ConsultaAltura chao() {
		int[][] a = new int[61][61];
		for (int z = 0; z <= 60; z++) {
			for (int x = 0; x <= 60; x++) {
				a[z][x] = x < 10 ? -50 : x <= 40 ? 200 : 200 + (x - 40) * 60;
			}
		}
		return new ConsultaAltura(a, 1, 0.01f);
	}

	@Test
	void cadaLugarComSeuMaterial() {
		ConsultaAltura c = chao();
		assertEquals(MaterialChao.AGUA, MaterialChao.em(c, 4, 30, false, false));
		assertEquals(MaterialChao.GRAMA, MaterialChao.em(c, 25, 30, false, false));
		assertEquals(MaterialChao.TERRA, MaterialChao.em(c, 25, 30, false, true), "na trilha");
		assertEquals(MaterialChao.TERRA, MaterialChao.em(c, 50, 30, false, false), "no declive");
		assertEquals(MaterialChao.PISO, MaterialChao.em(c, 4, 30, true, false), "dentro do prédio, sempre o piso");
	}
}
