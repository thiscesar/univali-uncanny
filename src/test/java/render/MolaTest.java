package render;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MolaTest {

	@Test
	void trancoSaiEVoltaAoAlvo() {
		Mola m = new Mola(260, 22);
		m.empurrar(1.6f);
		float maximo = 0;
		for (int i = 0; i < 30; i++) {
			m.atualizar(0.01f);
			maximo = Math.max(maximo, m.posicao);
		}
		assertTrue(maximo > 0.03f, "o tranco tira a arma do lugar: " + maximo);
		m.atualizar(2f);
		assertEquals(0, m.posicao, 1e-3f);
	}

	@Test
	void segueONovoAlvo() {
		Mola m = new Mola(160, 24);
		m.alvo = 1;
		m.atualizar(1.5f);
		assertEquals(1, m.posicao, 1e-3f);
	}

	@Test
	void quadroLentoNaoExplode() {
		Mola m = new Mola(400, 20);
		m.empurrar(5);
		m.atualizar(0.5f); // um quadro de meio segundo: os passos internos de 5 ms seguram a conta
		assertTrue(Math.abs(m.posicao) < 1, "posição " + m.posicao);
	}
}
