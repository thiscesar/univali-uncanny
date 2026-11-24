package render;

import static org.lwjgl.glfw.GLFW.*;

import java.awt.Color;

import dados.Recursos;
import entrada.Entrada;

/**
 * Uma opção com alguns valores fixos (ex.: desligadas, médias, altas), com rótulo. O clique
 * passa para o próximo valor; com a linha selecionada, esquerda e direita andam pela lista
 * (Enter e Espaço passam para o próximo). É o Alternador com mais de dois valores.
 */
public class Seletor {

	private static final Color COR_ROTULO = new Color(200, 192, 180);
	private static final Color COR_ROTULO_FOCO = new Color(220, 40, 35);
	private static final Color COR_VALOR = new Color(220, 60, 50);
	private static final Color COR_PRIMEIRO = new Color(120, 114, 108);

	public final String rotulo;
	private final String[] valores;
	public int indice;
	public float x, y, largura, altura;

	public Seletor(String rotulo, String[] valores, int indice) {
		this.rotulo = rotulo;
		this.valores = valores;
		this.indice = Math.max(0, Math.min(valores.length - 1, indice));
	}

	public void posicionar(float x, float y, float largura, float altura) {
		this.x = x;
		this.y = y;
		this.largura = largura;
		this.altura = altura;
	}

	public boolean contem(double px, double py) {
		return px >= x && px < x + largura && py >= y && py < y + altura;
	}

	/** Devolve true se o mouse clicou na linha (o painel usa para dar a seleção a ela). */
	public boolean atualizar(Entrada entrada, boolean focado) {
		boolean clicou = entrada.botaoFoiPressionado(GLFW_MOUSE_BUTTON_LEFT) && contem(entrada.mouseX(), entrada.mouseY());
		if (clicou || (focado && (entrada.foiPressionada(GLFW_KEY_RIGHT) || entrada.foiPressionada(GLFW_KEY_ENTER)
				|| entrada.foiPressionada(GLFW_KEY_KP_ENTER) || entrada.foiPressionada(GLFW_KEY_SPACE)))) {
			indice = (indice + 1) % valores.length;
		} else if (focado && entrada.foiPressionada(GLFW_KEY_LEFT)) {
			indice = (indice - 1 + valores.length) % valores.length;
		}
		return clicou;
	}

	public void desenhar(Overlay2D overlay, boolean focado, int tamanhoTexto) {
		float meio = y + altura / 2;
		Texto.desenharAlinhado(overlay, Recursos.fonteTexto(), rotulo, x, meio, tamanhoTexto,
				focado ? COR_ROTULO_FOCO : COR_ROTULO, Texto.Alinhamento.ESQUERDA, false, true);
		Texto.desenharAlinhado(overlay, Recursos.fonteTexto(), valores[indice], x + largura, meio, tamanhoTexto,
				indice == 0 ? COR_PRIMEIRO : COR_VALOR, Texto.Alinhamento.DIREITA, false, true);
	}
}
