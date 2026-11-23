package render;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

import org.junit.jupiter.api.Test;

import entrada.Entrada;

class DeslizanteTest {

	@Test
	void setasMudamOValorSoComFocoEFicamEntreZeroEUm() {
		Entrada e = new Entrada();
		Deslizante d = new Deslizante("Volume", 0.98f, v -> "");
		e.aoTeclar(GLFW_KEY_RIGHT, GLFW_PRESS);
		d.atualizar(e, false);
		assertEquals(0.98f, d.valor, 1e-6);
		d.atualizar(e, true);
		assertEquals(1f, d.valor, 1e-6);
	}

	@Test
	void arrastarNaBarraDefineOValorPelaPosicao() {
		Entrada e = new Entrada();
		Deslizante d = new Deslizante("Volume", 0f, v -> "");
		d.posicionar(0, 0, 1000, 50); // a barra vai de x = 420 a x = 850
		e.aoMoverMouse(634, 25);
		e.aoMoverMouse(635, 25); // metade da barra
		e.aoClicar(GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS);
		assertTrue(d.atualizar(e, false));
		assertEquals(0.5f, d.valor, 0.01);
	}
}
