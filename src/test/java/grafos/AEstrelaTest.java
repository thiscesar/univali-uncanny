package grafos;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AEstrelaTest {

	@Test
	void gradeLivreVaiEmLinhaRetaNaDiagonal() {
		GradeCaminhada g = GradeCaminhada.deTexto(1, ".....", ".....", ".....", ".....", ".....");
		AEstrela.Caminho c = AEstrela.buscar(g, 0, 0, 4, 4, 0);
		assertNotNull(c);
		assertEquals(5, c.celulas().size());
		assertEquals(4 * Math.sqrt(2), c.comprimento(), 1e-4);
	}

	@Test
	void contornaOObstaculo() {
		GradeCaminhada g = GradeCaminhada.deTexto(2,
				".....",
				".###.",
				".#...",
				".#.#.",
				"...#.");
		AEstrela.Caminho c = AEstrela.buscar(g, 2, 2, 0, 0, 0);
		assertNotNull(c);
		for (int[] cel : c.celulas()) assertTrue(g.caminhavel(cel[0], cel[1]), "passou por obstáculo");
		// sai por (3,2) -> (4,1) -> (4,0)? não: dá a volta por cima, pela direita
		assertTrue(c.comprimento() > 2 * 2 * Math.sqrt(2), "mais longo que a linha reta");
	}

	@Test
	void destinoInalcancavelDevolveNull() {
		GradeCaminhada g = GradeCaminhada.deTexto(1,
				"..#..",
				"..#..",
				"..#..",
				"..#..",
				"..#..");
		assertNull(AEstrela.buscar(g, 0, 0, 4, 0, 0));
		assertNotEquals(g.componente(0, 0), g.componente(4, 0));
		assertEquals(g.componente(0, 0), g.componente(1, 2));
	}

	@Test
	void inclinacaoFazContornarOMorro() {
		// o meio da linha do meio é um morro (inclinação 0,9): reto é mais curto, mas mais caro
		GradeCaminhada g = GradeCaminhada.deTexto(1,
				".......",
				"..999..",
				".......",
				".......",
				".......",
				".......",
				".......");
		AEstrela.Caminho semPeso = AEstrela.buscar(g, 0, 1, 6, 1, 0);
		AEstrela.Caminho comPeso = AEstrela.buscar(g, 0, 1, 6, 1, 3);
		assertEquals(6, semPeso.comprimento(), 1e-4);
		assertTrue(comPeso.comprimento() > 6, "desviou do morro");
		for (int[] c : comPeso.celulas()) assertNotEquals(9, (int) (g.inclinacao(c[0], c[1]) * 10));
	}

	@Test
	void naoCortaQuina() {
		GradeCaminhada g = GradeCaminhada.deTexto(1,
				".#",
				"#.");
		assertNull(AEstrela.buscar(g, 0, 0, 1, 1, 0), "na diagonal entre dois obstáculos não passa");
	}

	@Test
	void heuristicaOctile() {
		assertEquals(3 + 2 * (Math.sqrt(2) - 1), AEstrela.octile(0, 0, 3, 2), 1e-5);
	}
}
