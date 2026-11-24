package render;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

import org.junit.jupiter.api.Test;

import entrada.Entrada;

class FiltroPs1Test {

	@Test
	void imagemPequenaTemPerto240LinhasEEscalaInteira() {
		assertArrayEquals(new int[] { 320, 240 }, FiltroPs1.tamanhoBaixo(640, 480));
		assertArrayEquals(new int[] { 426, 266 }, FiltroPs1.tamanhoBaixo(1280, 800));   // escala 3
		assertArrayEquals(new int[] { 480, 270 }, FiltroPs1.tamanhoBaixo(1920, 1080));  // escala 4
		assertArrayEquals(new int[] { 300, 200 }, FiltroPs1.tamanhoBaixo(300, 200));    // janela pequena: escala 1
	}

	@Test
	void alternadorTrocaComTeclaSoQuandoSelecionado() {
		Entrada e = new Entrada();
		Alternador a = new Alternador("Modo PS1", true);
		e.aoTeclar(GLFW_KEY_ENTER, GLFW_PRESS);
		a.atualizar(e, false);
		assertTrue(a.valor);
		a.atualizar(e, true);
		assertFalse(a.valor);
	}

	@Test
	void alternadorTrocaComClique() {
		Entrada e = new Entrada();
		Alternador a = new Alternador("Modo PS1", false);
		a.posicionar(0, 0, 400, 50);
		e.aoMoverMouse(99, 25);
		e.aoMoverMouse(100, 25);
		e.aoClicar(GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS);
		assertTrue(a.atualizar(e, false));
		assertTrue(a.valor);
	}
}
