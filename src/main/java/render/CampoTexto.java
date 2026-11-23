package render;

import static org.lwjgl.glfw.GLFW.*;

import java.awt.Color;

import dados.Recursos;
import entrada.Entrada;

/**
 * Campo para digitar um texto curto, com rótulo. Quando selecionado, recebe o que for
 * digitado e apaga com Backspace (segurando, apaga vários). Pode aceitar só dígitos.
 */
public class CampoTexto {

	private static final Color COR_ROTULO = new Color(200, 192, 180);
	private static final Color COR_ROTULO_FOCO = new Color(220, 40, 35);
	private static final Color COR_CAIXA = new Color(15, 12, 16, 230);
	private static final Color COR_BORDA = new Color(90, 80, 80, 220);
	private static final Color COR_BORDA_FOCO = new Color(190, 30, 30, 255);
	private static final Color COR_TEXTO = new Color(235, 230, 220);
	private static final Color COR_DICA = new Color(110, 105, 100);

	public String rotulo;
	public float x, y, largura, altura;

	private final StringBuilder texto = new StringBuilder();
	private final int maximo;
	private final boolean soDigitos;
	/** Texto cinza mostrado quando o campo está vazio. */
	private final String dica;
	private float tempoCursor = 0;

	public CampoTexto(String rotulo, String inicial, int maximo, boolean soDigitos, String dica) {
		this.rotulo = rotulo;
		this.maximo = maximo;
		this.soDigitos = soDigitos;
		this.dica = dica;
		inserir(inicial);
	}

	public void posicionar(float x, float y, float largura, float altura) {
		this.x = x;
		this.y = y;
		this.largura = largura;
		this.altura = altura;
	}

	public String texto() {
		return texto.toString();
	}

	public boolean contem(double px, double py) {
		return px >= x && px < x + largura && py >= y && py < y + altura;
	}

	/** Devolve true se foi clicado (o painel usa para dar a seleção a ele). diftime em ms. */
	public boolean atualizar(Entrada entrada, boolean focado, long diftime) {
		tempoCursor += diftime / 1000f;
		boolean clicou = entrada.botaoFoiPressionado(GLFW_MOUSE_BUTTON_LEFT)
				&& contem(entrada.mouseX(), entrada.mouseY());
		if (focado) {
			inserir(entrada.textoDigitado());
			if (entrada.foiPressionadaOuRepetida(GLFW_KEY_BACKSPACE) && texto.length() > 0) {
				texto.setLength(texto.length() - 1);
			}
		}
		return clicou;
	}

	/** Acrescenta texto respeitando o filtro e o tamanho máximo. */
	public void inserir(String novo) {
		for (char c : novo.toCharArray()) {
			if (texto.length() >= maximo) {
				return;
			}
			if (soDigitos && !Character.isDigit(c)) {
				continue;
			}
			texto.append(c);
		}
	}

	public void desenhar(Overlay2D overlay, boolean focado, int tamanhoTexto) {
		float meio = y + altura / 2;
		Texto.desenharAlinhado(overlay, Recursos.fonteTexto(), rotulo, x, meio, tamanhoTexto,
				focado ? COR_ROTULO_FOCO : COR_ROTULO, Texto.Alinhamento.ESQUERDA, false, true);

		float cx = x + largura * 0.42f, cl = largura * 0.58f;
		overlay.retangulo(cx - 2, y + 4, cl + 4, altura - 8, focado ? COR_BORDA_FOCO : COR_BORDA);
		overlay.retangulo(cx, y + 6, cl, altura - 12, COR_CAIXA);

		Fonte fonte = Recursos.fonteTexto();
		if (texto.length() == 0 && !focado) {
			Texto.desenharAlinhado(overlay, fonte, dica, cx + 10, meio, tamanhoTexto, COR_DICA,
					Texto.Alinhamento.ESQUERDA, false, false);
		} else {
			Texto.desenharAlinhado(overlay, fonte, texto(), cx + 10, meio, tamanhoTexto, COR_TEXTO,
					Texto.Alinhamento.ESQUERDA, false, false);
		}
		// cursor piscando no fim do texto
		if (focado && (tempoCursor % 1.0f) < 0.55f) {
			float fim = cx + 10 + Math.max(0, Texto.largura(fonte, texto(), tamanhoTexto) - 2);
			overlay.retangulo(fim, meio - tamanhoTexto * 0.45f, 2, tamanhoTexto * 0.9f, COR_TEXTO);
		}
	}
}
