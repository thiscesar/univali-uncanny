package entrada;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.*;

import org.junit.jupiter.api.Test;

/** Simula os callbacks do GLFW chamando direto os métodos ao*, sem abrir janela. */
class EntradaTest {

	@Test
	void foiPressionadaValeSoNoQuadroDoAperto() {
		Entrada e = new Entrada();
		e.aoTeclar(GLFW_KEY_W, GLFW_PRESS);

		assertTrue(e.estaPressionada(GLFW_KEY_W));
		assertTrue(e.foiPressionada(GLFW_KEY_W));

		e.fimDoQuadro();
		assertTrue(e.estaPressionada(GLFW_KEY_W), "continua segurada");
		assertFalse(e.foiPressionada(GLFW_KEY_W), "mas o aperto já passou");
	}

	@Test
	void repeticaoDoSistemaNaoContaComoNovoAperto() {
		Entrada e = new Entrada();
		e.aoTeclar(GLFW_KEY_ESCAPE, GLFW_PRESS);
		e.fimDoQuadro();
		e.aoTeclar(GLFW_KEY_ESCAPE, GLFW_REPEAT);

		assertFalse(e.foiPressionada(GLFW_KEY_ESCAPE));
	}

	@Test
	void soltarMarcaFoiSolta() {
		Entrada e = new Entrada();
		e.aoTeclar(GLFW_KEY_SPACE, GLFW_PRESS);
		e.fimDoQuadro();
		e.aoTeclar(GLFW_KEY_SPACE, GLFW_RELEASE);

		assertFalse(e.estaPressionada(GLFW_KEY_SPACE));
		assertTrue(e.foiSolta(GLFW_KEY_SPACE));
		e.fimDoQuadro();
		assertFalse(e.foiSolta(GLFW_KEY_SPACE));
	}

	@Test
	void teclaDesconhecidaNaoQuebra() {
		Entrada e = new Entrada();
		e.aoTeclar(GLFW_KEY_UNKNOWN, GLFW_PRESS);
		assertFalse(e.estaPressionada(GLFW_KEY_UNKNOWN));
	}

	@Test
	void botoesDoMouseFuncionamComoTeclas() {
		Entrada e = new Entrada();
		e.aoClicar(GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS);
		assertTrue(e.botaoPressionado(GLFW_MOUSE_BUTTON_LEFT));
		assertTrue(e.botaoFoiPressionado(GLFW_MOUSE_BUTTON_LEFT));
		e.fimDoQuadro();
		assertFalse(e.botaoFoiPressionado(GLFW_MOUSE_BUTTON_LEFT));
	}

	@Test
	void deltaDoMouseAcumulaNoQuadroEZeraDepois() {
		Entrada e = new Entrada();
		e.aoMoverMouse(100, 100); // primeiro movimento: só referência
		e.aoMoverMouse(110, 95);
		e.aoMoverMouse(115, 90);

		assertEquals(15f, e.deltaMouse().x, 1e-6);
		assertEquals(-10f, e.deltaMouse().y, 1e-6);
		assertEquals(115.0, e.mouseX(), 1e-9);

		e.fimDoQuadro();
		assertEquals(0f, e.deltaMouse().x, 1e-6);
	}

	@Test
	void prenderCursorDescartaOSaltoDoPrimeiroMovimento() {
		Entrada e = new Entrada();
		e.aoMoverMouse(100, 100);
		e.prenderCursor(true);
		e.aoMoverMouse(750, 500); // o GLFW recentraliza o cursor ao prender
		assertEquals(0f, e.deltaMouse().x, 1e-6);
		assertTrue(e.cursorPreso());
	}

	@Test
	void textoDigitadoValeSoNoQuadro() {
		Entrada e = new Entrada();
		e.aoDigitar('ç');
		e.aoDigitar('a');
		assertEquals("ça", e.textoDigitado());
		e.fimDoQuadro();
		assertEquals("", e.textoDigitado());
	}

	@Test
	void repeticaoContaParaFoiPressionadaOuRepetida() {
		Entrada e = new Entrada();
		e.aoTeclar(GLFW_KEY_LEFT, GLFW_PRESS);
		e.fimDoQuadro();
		assertFalse(e.foiPressionadaOuRepetida(GLFW_KEY_LEFT));
		e.aoTeclar(GLFW_KEY_LEFT, GLFW_REPEAT);
		assertTrue(e.foiPressionadaOuRepetida(GLFW_KEY_LEFT));
		assertFalse(e.foiPressionada(GLFW_KEY_LEFT));
	}

	@Test
	void rolagemAcumulaNoQuadro() {
		Entrada e = new Entrada();
		e.aoRolar(1);
		e.aoRolar(1);
		assertEquals(2.0, e.rolagem(), 1e-9);
		e.fimDoQuadro();
		assertEquals(0.0, e.rolagem(), 1e-9);
	}
}
