package render;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

import org.junit.jupiter.api.Test;

import entrada.Entrada;

class GrupoBotoesTest {

	private final Entrada entrada = new Entrada();
	private final GrupoBotoes grupo = new GrupoBotoes();
	private final Botao jogar = grupo.adicionar(new Botao("Jogar", 200, 200, 100, 40));
	private final Botao sair = grupo.adicionar(new Botao("Sair", 200, 260, 100, 40));

	private Botao apertar(int tecla) {
		entrada.aoTeclar(tecla, GLFW_PRESS);
		Botao b = grupo.atualizar(entrada);
		entrada.aoTeclar(tecla, GLFW_RELEASE);
		entrada.fimDoQuadro();
		return b;
	}

	@Test
	void enterAcionaOPrimeiroPorPadrao() {
		assertSame(jogar, apertar(GLFW_KEY_ENTER));
	}

	@Test
	void setasMudamOFocoEDaoAVolta() {
		apertar(GLFW_KEY_DOWN);
		assertEquals(1, grupo.foco());
		assertSame(sair, apertar(GLFW_KEY_ENTER));

		apertar(GLFW_KEY_DOWN); // passa do último e volta ao primeiro
		assertEquals(0, grupo.foco());
		apertar(GLFW_KEY_UP);
		assertEquals(1, grupo.foco());
	}

	@Test
	void focadoPeloTecladoApareceComoSobre() {
		apertar(GLFW_KEY_DOWN);
		grupo.atualizar(entrada);
		assertEquals(Botao.Estado.SOBRE, sair.estado());
		assertEquals(Botao.Estado.NORMAL, jogar.estado());
	}

	@Test
	void moverOMouseSobreUmBotaoLevaOFocoParaEle() {
		entrada.aoMoverMouse(250, 270);
		entrada.aoMoverMouse(250, 280);
		grupo.atualizar(entrada);
		assertEquals(1, grupo.foco());
	}

	@Test
	void nadaAcionadoSemEnterNemClique() {
		assertNull(grupo.atualizar(entrada));
	}
}
