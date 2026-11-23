package render;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

import org.junit.jupiter.api.Test;

import entrada.Entrada;

class BotaoTest {

	private final Entrada entrada = new Entrada();
	private final Botao botao = new Botao("Jogar", 100, 100, 200, 50);

	/** Leva o mouse até (x, y), gerando movimento. */
	private void moverMouse(double x, double y) {
		entrada.aoMoverMouse(x - 1, y - 1);
		entrada.aoMoverMouse(x, y);
	}

	@Test
	void mudaParaSobreComOMouseEmCima() {
		moverMouse(10, 10);
		botao.atualizar(entrada);
		assertEquals(Botao.Estado.NORMAL, botao.estado());

		moverMouse(150, 120);
		botao.atualizar(entrada);
		assertEquals(Botao.Estado.SOBRE, botao.estado());
	}

	@Test
	void cliqueSoContaAoSoltarEmCima() {
		moverMouse(150, 120);
		entrada.aoClicar(GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS);
		assertFalse(botao.atualizar(entrada), "apertar ainda não é clicar");
		assertEquals(Botao.Estado.PRESSIONADO, botao.estado());
		entrada.fimDoQuadro();

		entrada.aoClicar(GLFW_MOUSE_BUTTON_LEFT, GLFW_RELEASE);
		assertTrue(botao.atualizar(entrada));
	}

	@Test
	void arrastarParaForaCancelaOClique() {
		moverMouse(150, 120);
		entrada.aoClicar(GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS);
		botao.atualizar(entrada);
		entrada.fimDoQuadro();

		moverMouse(500, 500);
		entrada.aoClicar(GLFW_MOUSE_BUTTON_LEFT, GLFW_RELEASE);
		assertFalse(botao.atualizar(entrada));
	}

	@Test
	void apertarForaESoltarDentroNaoClica() {
		moverMouse(500, 500);
		entrada.aoClicar(GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS);
		botao.atualizar(entrada);
		entrada.fimDoQuadro();

		moverMouse(150, 120);
		entrada.aoClicar(GLFW_MOUSE_BUTTON_LEFT, GLFW_RELEASE);
		assertFalse(botao.atualizar(entrada));
	}
}
