package telas;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ProgressoEtapasTest {

	private ProgressoEtapas exemplo() {
		return new ProgressoEtapas().adicionar("relevo", 0.7f).adicionar("malha", 0.2f).adicionar("texturas", 0.1f);
	}

	@Test
	void comecaEmZeroEAcabaEmUm() {
		ProgressoEtapas p = exemplo();
		assertEquals(0f, p.total(), 1e-6);
		p.concluirEtapa();
		p.concluirEtapa();
		p.concluirEtapa();
		assertEquals(1f, p.total(), 1e-6);
		assertTrue(p.terminou());
		assertEquals("", p.nomeAtual());
	}

	@Test
	void fracaoDaEtapaAtualContaProporcionalAoPeso() {
		ProgressoEtapas p = exemplo();
		p.definirFracao(0.5f); // metade do relevo, que vale 70% da barra
		assertEquals(0.35f, p.total(), 1e-6);
		p.concluirEtapa();
		p.definirFracao(0.5f); // metade da malha
		assertEquals(0.8f, p.total(), 1e-6);
		assertEquals("malha", p.nomeAtual());
	}

	@Test
	void concluirZeraAFracaoENuncaPassaDoFim() {
		ProgressoEtapas p = exemplo();
		p.definirFracao(0.9f);
		p.concluirEtapa();
		assertEquals(0.7f, p.total(), 1e-6, "a fração não vaza para a etapa seguinte");
		for (int i = 0; i < 10; i++) p.concluirEtapa();
		assertEquals(1f, p.total(), 1e-6);
	}
}
