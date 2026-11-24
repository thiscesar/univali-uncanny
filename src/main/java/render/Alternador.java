package render;

import static org.lwjgl.glfw.GLFW.*;

import java.awt.Color;

import dados.Recursos;
import entrada.Entrada;

/**
 * Uma opção de liga/desliga com rótulo. Clique, Enter, Espaço ou as setas trocam o valor
 * (as teclas, só quando a linha está selecionada).
 */
public class Alternador {

	private static final Color COR_ROTULO = new Color(200, 192, 180);
	private static final Color COR_ROTULO_FOCO = new Color(220, 40, 35);
	private static final Color COR_LIGADO = new Color(220, 60, 50);
	private static final Color COR_DESLIGADO = new Color(120, 114, 108);

	public String rotulo;
	public boolean valor;
	public float x, y, largura, altura;

	public Alternador(String rotulo, boolean valor) {
		this.rotulo = rotulo;
		this.valor = valor;
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
		boolean tecla = focado && (entrada.foiPressionada(GLFW_KEY_ENTER) || entrada.foiPressionada(GLFW_KEY_KP_ENTER)
				|| entrada.foiPressionada(GLFW_KEY_SPACE) || entrada.foiPressionada(GLFW_KEY_LEFT)
				|| entrada.foiPressionada(GLFW_KEY_RIGHT));
		if (clicou || tecla) {
			valor = !valor;
		}
		return clicou;
	}

	public void desenhar(Overlay2D overlay, boolean focado, int tamanhoTexto) {
		float meio = y + altura / 2;
		Texto.desenharAlinhado(overlay, Recursos.fonteTexto(), rotulo, x, meio, tamanhoTexto,
				focado ? COR_ROTULO_FOCO : COR_ROTULO, Texto.Alinhamento.ESQUERDA, false, true);
		Texto.desenharAlinhado(overlay, Recursos.fonteTexto(), valor ? "ligado" : "desligado", x + largura, meio,
				tamanhoTexto, valor ? COR_LIGADO : COR_DESLIGADO, Texto.Alinhamento.DIREITA, false, true);
	}
}
