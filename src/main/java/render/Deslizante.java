package render;

import static org.lwjgl.glfw.GLFW.*;

import java.awt.Color;
import java.util.function.Function;

import dados.Recursos;
import entrada.Entrada;

/**
 * Controle deslizante (slider) com rótulo, para valores de 0 a 1.
 * Mouse: clique ou arraste na barra. Teclado (quando selecionado): setas esquerda e direita.
 */
public class Deslizante {

	private static final float PASSO_TECLADO = 0.05f;
	private static final Color COR_ROTULO = new Color(200, 192, 180);
	private static final Color COR_ROTULO_FOCO = new Color(220, 40, 35);
	private static final Color COR_TRILHO = new Color(60, 55, 60, 220);
	private static final Color COR_PREENCHIDO = new Color(150, 20, 20, 240);
	private static final Color COR_PINO = new Color(225, 215, 200);

	public String rotulo;
	/** Valor entre 0 e 1. */
	public float valor;
	public float x, y, largura, altura;

	/** Como mostrar o valor ao lado da barra (por exemplo, "80%"). */
	private final Function<Float, String> formato;
	private boolean arrastando = false;

	public Deslizante(String rotulo, float valor, Function<Float, String> formato) {
		this.rotulo = rotulo;
		this.valor = valor;
		this.formato = formato;
	}

	public void posicionar(float x, float y, float largura, float altura) {
		this.x = x;
		this.y = y;
		this.largura = largura;
		this.altura = altura;
	}

	private float barraX() { return x + largura * 0.42f; }
	private float barraLargura() { return largura * 0.43f; }

	public boolean contem(double px, double py) {
		return px >= x && px < x + largura && py >= y && py < y + altura;
	}

	/** Devolve true se o mouse foi apertado sobre a linha (o painel usa para mudar a seleção). */
	public boolean atualizar(Entrada entrada, boolean focado) {
		boolean clicou = false;
		double mx = entrada.mouseX(), my = entrada.mouseY();
		if (entrada.botaoFoiPressionado(GLFW_MOUSE_BUTTON_LEFT) && contem(mx, my)) {
			clicou = true;
			if (mx >= barraX() - 10 && mx <= barraX() + barraLargura() + 10) {
				arrastando = true;
			}
		}
		if (!entrada.botaoPressionado(GLFW_MOUSE_BUTTON_LEFT)) {
			arrastando = false;
		}
		if (arrastando) {
			valor = limitar((float) (mx - barraX()) / barraLargura());
		}
		if (focado) {
			if (entrada.foiPressionadaOuRepetida(GLFW_KEY_LEFT)) valor = limitar(valor - PASSO_TECLADO);
			if (entrada.foiPressionadaOuRepetida(GLFW_KEY_RIGHT)) valor = limitar(valor + PASSO_TECLADO);
		}
		return clicou;
	}

	public void desenhar(Overlay2D overlay, boolean focado, int tamanhoTexto) {
		float meio = y + altura / 2;
		Texto.desenharAlinhado(overlay, Recursos.fonteTexto(), rotulo, x, meio, tamanhoTexto,
				focado ? COR_ROTULO_FOCO : COR_ROTULO, Texto.Alinhamento.ESQUERDA, false, true);

		float bx = barraX(), bl = barraLargura();
		overlay.retangulo(bx, meio - 3, bl, 6, COR_TRILHO);
		overlay.retangulo(bx, meio - 3, bl * valor, 6, COR_PREENCHIDO);
		overlay.retangulo(bx + bl * valor - 5, meio - 11, 10, 22, COR_PINO);

		Texto.desenharAlinhado(overlay, Recursos.fonteTexto(), formato.apply(valor), x + largura, meio,
				tamanhoTexto, COR_ROTULO, Texto.Alinhamento.DIREITA, false, true);
	}

	private static float limitar(float v) {
		return Math.max(0, Math.min(1, v));
	}
}
