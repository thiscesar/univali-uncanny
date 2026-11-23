package entrada;

import static org.lwjgl.glfw.GLFW.*;

import org.lwjgl.util.vector.Vector2f;

/**
 * Estado do teclado e do mouse num só lugar.
 *
 * Os callbacks do GLFW só registram o que aconteceu; as telas perguntam aqui:
 *   estaPressionada(tecla)  a tecla está apertada agora
 *   foiPressionada(tecla)   a tecla foi apertada NESTE quadro (bom para ações únicas, como pausar)
 *   foiSolta(tecla)         a tecla foi solta neste quadro
 * O mesmo vale para os botões do mouse. Use as constantes do GLFW (GLFW_KEY_W, GLFW_MOUSE_BUTTON_LEFT...).
 *
 * O laço principal chama fimDoQuadro() depois de atualizar e desenhar, o que zera os
 * "foi pressionada/solta" e o movimento acumulado do mouse.
 *
 * Os métodos aoTeclar, aoClicar, aoMoverMouse e aoRolar são o que os callbacks chamam;
 * ficaram públicos para os testes poderem simular a entrada sem abrir janela.
 */
public class Entrada {

	private final boolean[] teclas = new boolean[GLFW_KEY_LAST + 1];
	private final boolean[] teclasApertadas = new boolean[GLFW_KEY_LAST + 1];
	private final boolean[] teclasSoltas = new boolean[GLFW_KEY_LAST + 1];
	/** Teclas que o sistema repetiu neste quadro por estarem seguradas (apagar texto, mover controles). */
	private final boolean[] teclasRepetidas = new boolean[GLFW_KEY_LAST + 1];

	/** Caracteres digitados neste quadro, já com acentos e maiúsculas resolvidos pelo sistema. */
	private final StringBuilder digitado = new StringBuilder();

	private final boolean[] botoes = new boolean[GLFW_MOUSE_BUTTON_LAST + 1];
	private final boolean[] botoesApertados = new boolean[GLFW_MOUSE_BUTTON_LAST + 1];
	private final boolean[] botoesSoltos = new boolean[GLFW_MOUSE_BUTTON_LAST + 1];

	private double mouseX, mouseY;
	private double deltaX, deltaY;
	private double rolagem;

	/**
	 * Ao prender ou soltar o cursor, o GLFW pode "teleportar" a posição do mouse.
	 * O primeiro movimento depois disso só serve de referência, sem gerar delta.
	 */
	private boolean ignorarProximoMovimento = true;

	private long janela = 0;
	private boolean cursorPreso = false;

	/** Liga os callbacks do GLFW a esta entrada. Chamado uma vez, logo após criar a janela. */
	public void registrar(long janela) {
		this.janela = janela;
		glfwSetKeyCallback(janela, (j, tecla, scancode, acao, mods) -> aoTeclar(tecla, acao));
		glfwSetMouseButtonCallback(janela, (j, botao, acao, mods) -> aoClicar(botao, acao));
		glfwSetCursorPosCallback(janela, (j, x, y) -> aoMoverMouse(x, y));
		glfwSetScrollCallback(janela, (j, dx, dy) -> aoRolar(dy));
		glfwSetCharCallback(janela, (j, codigo) -> aoDigitar(codigo));

		// o callback só avisa quando o mouse se move; até lá, a posição seria (0, 0)
		double[] x = new double[1], y = new double[1];
		glfwGetCursorPos(janela, x, y);
		mouseX = x[0];
		mouseY = y[0];
	}

	// ---------- chamados pelos callbacks ----------

	public void aoTeclar(int tecla, int acao) {
		if (tecla < 0 || tecla > GLFW_KEY_LAST) {
			return; // GLFW_KEY_UNKNOWN (-1): teclas que o GLFW não conhece
		}
		if (acao == GLFW_PRESS) {
			teclas[tecla] = true;
			teclasApertadas[tecla] = true;
		} else if (acao == GLFW_RELEASE) {
			teclas[tecla] = false;
			teclasSoltas[tecla] = true;
		}
		else if (acao == GLFW_REPEAT) {
			teclasRepetidas[tecla] = true; // não conta como novo aperto, só como repetição
		}
	}

	/** Chamado pelo callback de caracteres: recebe o código Unicode do que foi digitado. */
	public void aoDigitar(int codigo) {
		digitado.appendCodePoint(codigo);
	}

	public void aoClicar(int botao, int acao) {
		if (botao < 0 || botao > GLFW_MOUSE_BUTTON_LAST) {
			return;
		}
		if (acao == GLFW_PRESS) {
			botoes[botao] = true;
			botoesApertados[botao] = true;
		} else if (acao == GLFW_RELEASE) {
			botoes[botao] = false;
			botoesSoltos[botao] = true;
		}
	}

	public void aoMoverMouse(double x, double y) {
		if (ignorarProximoMovimento) {
			ignorarProximoMovimento = false;
		} else {
			deltaX += x - mouseX;
			deltaY += y - mouseY;
		}
		mouseX = x;
		mouseY = y;
	}

	public void aoRolar(double dy) {
		rolagem += dy;
	}

	// ---------- consultas do teclado ----------

	public boolean estaPressionada(int tecla) {
		return valida(tecla) && teclas[tecla];
	}

	public boolean foiPressionada(int tecla) {
		return valida(tecla) && teclasApertadas[tecla];
	}

	public boolean foiSolta(int tecla) {
		return valida(tecla) && teclasSoltas[tecla];
	}

	/** Apertada neste quadro ou repetida pelo sistema por estar segurada (como num editor de texto). */
	public boolean foiPressionadaOuRepetida(int tecla) {
		return valida(tecla) && (teclasApertadas[tecla] || teclasRepetidas[tecla]);
	}

	/** O texto digitado neste quadro (vazio se nada foi digitado). */
	public String textoDigitado() {
		return digitado.toString();
	}

	// ---------- consultas do mouse ----------

	public boolean botaoPressionado(int botao) {
		return botaoValido(botao) && botoes[botao];
	}

	public boolean botaoFoiPressionado(int botao) {
		return botaoValido(botao) && botoesApertados[botao];
	}

	public boolean botaoFoiSolto(int botao) {
		return botaoValido(botao) && botoesSoltos[botao];
	}

	/** Posição do cursor em pixels, com origem no canto superior esquerdo da janela. */
	public double mouseX() {
		return mouseX;
	}

	public double mouseY() {
		return mouseY;
	}

	/** Quanto o mouse andou neste quadro, em pixels (x para a direita, y para baixo). */
	public Vector2f deltaMouse() {
		return new Vector2f((float) deltaX, (float) deltaY);
	}

	/** Quanto a rodinha girou neste quadro (positivo = para cima). */
	public double rolagem() {
		return rolagem;
	}

	// ---------- cursor ----------

	/**
	 * true: o cursor some e fica preso no centro (jogo em primeira pessoa);
	 * false: cursor normal (menus).
	 */
	public void prenderCursor(boolean prender) {
		cursorPreso = prender;
		ignorarProximoMovimento = true;
		deltaX = 0;
		deltaY = 0;
		if (janela == 0) {
			return; // sem janela (testes)
		}
		glfwSetInputMode(janela, GLFW_CURSOR, prender ? GLFW_CURSOR_DISABLED : GLFW_CURSOR_NORMAL);
		// movimento "cru" do mouse, sem a aceleração do sistema, quando disponível
		if (glfwRawMouseMotionSupported()) {
			glfwSetInputMode(janela, GLFW_RAW_MOUSE_MOTION, prender ? GLFW_TRUE : GLFW_FALSE);
		}
	}

	public boolean cursorPreso() {
		return cursorPreso;
	}

	/** Zera o que só vale por um quadro. Chamado pelo laço principal no fim de cada quadro. */
	public void fimDoQuadro() {
		java.util.Arrays.fill(teclasApertadas, false);
		java.util.Arrays.fill(teclasSoltas, false);
		java.util.Arrays.fill(teclasRepetidas, false);
		digitado.setLength(0);
		java.util.Arrays.fill(botoesApertados, false);
		java.util.Arrays.fill(botoesSoltos, false);
		deltaX = 0;
		deltaY = 0;
		rolagem = 0;
	}

	private static boolean valida(int tecla) {
		return tecla >= 0 && tecla <= GLFW_KEY_LAST;
	}

	private static boolean botaoValido(int botao) {
		return botao >= 0 && botao <= GLFW_MOUSE_BUTTON_LAST;
	}
}
