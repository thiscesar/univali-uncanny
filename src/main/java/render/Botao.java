package render;

import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;

import java.awt.Color;

import dados.Recursos;
import entrada.Entrada;

/**
 * Botão retangular com texto, em coordenadas de tela (as mesmas do mouse).
 *
 * Estados: NORMAL; SOBRE (mouse em cima ou selecionado pelo teclado); PRESSIONADO
 * (botão do mouse apertado em cima dele). O clique só conta quando o botão do mouse
 * é apertado E solto em cima do botão, como nos sistemas operacionais.
 */
public class Botao {

	public enum Estado { NORMAL, SOBRE, PRESSIONADO }

	/** CAIXA: retângulo com texto no meio. TEXTO: só o texto, alinhado à esquerda (menu principal). */
	public enum Estilo { CAIXA, TEXTO }

	private static final Color FUNDO_NORMAL = new Color(25, 22, 30, 210);
	private static final Color FUNDO_SOBRE = new Color(95, 18, 18, 230);
	private static final Color FUNDO_PRESSIONADO = new Color(150, 25, 25, 255);
	private static final Color BORDA = new Color(140, 130, 120, 200);
	private static final Color BORDA_SOBRE = new Color(230, 200, 180, 255);
	private static final Color COR_TEXTO = new Color(235, 230, 220);
	private static final Color TEXTO_NORMAL = new Color(190, 182, 170);
	private static final Color TEXTO_SOBRE = new Color(200, 28, 28);
	private static final Color TEXTO_PRESSIONADO = new Color(255, 70, 60);
	/** Quanto o texto anda para a direita quando o botão está selecionado (estilo TEXTO). */
	private static final float AVANCO_SELECIONADO = 18;

	public float x, y, largura, altura;
	public String texto;
	public int tamanhoTexto = 24;
	public Estilo estilo = Estilo.CAIXA;
	/** Fonte do texto; null usa a fonte padrão da interface. */
	public Fonte fonte = null;

	/** Selecionado pelo teclado (definido pelo GrupoBotoes). */
	boolean focado = false;

	private Estado estado = Estado.NORMAL;
	private boolean apertadoAqui = false;

	public Botao(String texto, float x, float y, float largura, float altura) {
		this.texto = texto;
		this.x = x;
		this.y = y;
		this.largura = largura;
		this.altura = altura;
	}

	public void posicionar(float x, float y) {
		this.x = x;
		this.y = y;
	}

	public boolean contem(double px, double py) {
		return px >= x && px < x + largura && py >= y && py < y + altura;
	}

	/** Atualiza o estado pelo mouse. Devolve true no quadro em que o botão foi clicado. */
	public boolean atualizar(Entrada entrada) {
		boolean dentro = contem(entrada.mouseX(), entrada.mouseY());

		if (entrada.botaoFoiPressionado(GLFW_MOUSE_BUTTON_LEFT) && dentro) {
			apertadoAqui = true;
		}
		boolean clicou = false;
		if (entrada.botaoFoiSolto(GLFW_MOUSE_BUTTON_LEFT)) {
			clicou = apertadoAqui && dentro;
			apertadoAqui = false;
		}

		if (apertadoAqui && dentro) {
			estado = Estado.PRESSIONADO;
		} else if (dentro || focado) {
			estado = Estado.SOBRE;
		} else {
			estado = Estado.NORMAL;
		}
		return clicou;
	}

	public Estado estado() {
		return estado;
	}

	/** Marca o botão como selecionado pelo teclado (aparece como SOBRE). */
	public void definirFoco(boolean focado) {
		this.focado = focado;
	}

	public void desenhar(Overlay2D overlay) {
		Fonte f = fonte != null ? fonte : Recursos.fonteTexto();
		if (estilo == Estilo.TEXTO) {
			Color cor = switch (estado) {
				case NORMAL -> TEXTO_NORMAL;
				case SOBRE -> TEXTO_SOBRE;
				case PRESSIONADO -> TEXTO_PRESSIONADO;
			};
			float avanco = estado == Estado.NORMAL ? 0 : AVANCO_SELECIONADO;
			if (estado != Estado.NORMAL) {
				overlay.retangulo(x, y + altura / 2 - 2, 10, 4, cor); // marcador ao lado do texto
			}
			Texto.desenharAlinhado(overlay, f, texto, x + avanco, y + altura / 2, tamanhoTexto, cor,
					Texto.Alinhamento.ESQUERDA, false, true);
			return;
		}
		Color fundo = switch (estado) {
			case NORMAL -> FUNDO_NORMAL;
			case SOBRE -> FUNDO_SOBRE;
			case PRESSIONADO -> FUNDO_PRESSIONADO;
		};
		Color borda = estado == Estado.NORMAL ? BORDA : BORDA_SOBRE;
		// a borda é um retângulo um pouco maior desenhado atrás do fundo
		overlay.retangulo(x - 2, y - 2, largura + 4, altura + 4, borda);
		overlay.retangulo(x, y, largura, altura, fundo);
		Texto.desenharCentralizado(overlay, f, texto, x + largura / 2, y + altura / 2,
				tamanhoTexto, COR_TEXTO, false, true);
	}
}
