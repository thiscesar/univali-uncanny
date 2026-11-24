package render;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;

import org.junit.jupiter.api.Test;

class ConfeteTest {

	@Test
	void estouroSobeEDepoisCaiESome() {
		Confete c = new Confete(new Random(1));
		c.explodir(0.1f, 1.0f, 50, (float) -Math.PI / 2, 0.3f, 2.0f); // para cima
		assertEquals(50, c.quantidade());
		float menorY = 1;
		for (int i = 0; i < 30; i++) { // meio segundo
			c.atualizar(1 / 60f, 1.6f);
			for (Confete.Peca p : c.pecas()) menorY = Math.min(menorY, p.y);
		}
		assertTrue(menorY < 0.7f, "subiu pelo menos 30% da tela: " + menorY);
		for (int i = 0; i < 60 * 15; i++) c.atualizar(1 / 60f, 1.6f);
		assertEquals(0, c.quantidade(), "tudo caiu para fora da tela");
	}

	@Test
	void quedaTemVelocidadeMaximaEAFolhaDeProvaCaiMaisDevagar() {
		assertEquals(Confete.GRAVIDADE / Confete.ARRASTO_PAPELZINHO, 0.46f, 0.01f);
		assertTrue(Confete.GRAVIDADE / Confete.ARRASTO_PROVA < Confete.GRAVIDADE / Confete.ARRASTO_PAPELZINHO);
	}

	@Test
	void chuvaCriaPecasNoRitmoPedido() {
		Confete c = new Confete(new Random(2));
		c.chover(10);
		for (int i = 0; i < 60; i++) c.atualizar(1 / 60f, 1.6f); // 1 segundo
		assertTrue(c.quantidade() >= 9 && c.quantidade() <= 10, "10 por segundo: " + c.quantidade());
		c.chover(0);
		int antes = c.quantidade();
		c.atualizar(1f, 1.6f);
		assertTrue(c.quantidade() <= antes);
	}
}
