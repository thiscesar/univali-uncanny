package render;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

import org.junit.jupiter.api.Test;

import entrada.Entrada;

class CampoTextoTest {

	@Test
	void soAceitaDigitosERespeitaOMaximo() {
		CampoTexto c = new CampoTexto("Seed", "", 4, true, "");
		c.inserir("1a2b3c4d5");
		assertEquals("1234", c.texto());
	}

	@Test
	void recebeODigitadoSoQuandoSelecionado() {
		Entrada e = new Entrada();
		CampoTexto c = new CampoTexto("Seed", "", 9, true, "");
		e.aoDigitar('7');
		c.atualizar(e, false, 16);
		assertEquals("", c.texto());
		c.atualizar(e, true, 16);
		assertEquals("7", c.texto());
	}

	@Test
	void backspaceApagaInclusiveSegurado() {
		Entrada e = new Entrada();
		CampoTexto c = new CampoTexto("Seed", "123", 9, true, "");
		e.aoTeclar(GLFW_KEY_BACKSPACE, GLFW_PRESS);
		c.atualizar(e, true, 16);
		e.fimDoQuadro();
		e.aoTeclar(GLFW_KEY_BACKSPACE, GLFW_REPEAT);
		c.atualizar(e, true, 16);
		assertEquals("1", c.texto());
	}
}
