package md2;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import util.Caminhos;

class PontoMd2Test {

	@Test
	void olhosDoDragonKnightUmDeCadaLadoDaCabeca() {
		if (!Caminhos.existe("modelos/dragonknight/tris.md2")) return; // sem o modelo, nada a testar
		PersonagemMd2.Dados d = PersonagemMd2.ler("modelos/dragonknight", 2.2f);
		MalhaMd2 m = d.corpo();
		List<PontoMd2> olhos = PontoMd2.naSkin(m, 54.5f / 256, 122f / 256);
		assertEquals(2, olhos.size());
		float[] caixa = m.limites(0);
		for (PontoMd2 o : olhos) {
			float[] p = o.posicao(m, 0, 0, 0);
			assertTrue(p[0] > caixa[3] - 0.7f, "na frente (a cabeça do dragão): x = " + p[0]);
			assertTrue(p[1] > 0.5f && p[1] < 1.5f, "altura da cabeça: y = " + p[1]);
		}
		// misturar dois quadros dá o ponto entre as posições de cada quadro
		PontoMd2 o = olhos.get(0);
		float[] a = o.posicao(m, 0, 0, 0), b = o.posicao(m, 5, 5, 0), meio = o.posicao(m, 0, 5, 0.5f);
		for (int k = 0; k < 3; k++) assertEquals((a[k] + b[k]) / 2, meio[k], 1e-4f);
	}

	@Test
	void limitesEnvolvemTodosOsVertices() {
		if (!Caminhos.existe("modelos/dragonknight/tris.md2")) return;
		MalhaMd2 m = PersonagemMd2.ler("modelos/dragonknight", 2.2f).corpo();
		float[] c = m.limites(0);
		assertEquals(0f, c[1], 1e-4f);   // pés no chão
		assertEquals(2.2f, c[4], 1e-3f); // 2,2 m de altura
		assertTrue(c[3] - c[0] > 4.5f, "o dragão tem quase 5 m de comprimento");
	}
}
