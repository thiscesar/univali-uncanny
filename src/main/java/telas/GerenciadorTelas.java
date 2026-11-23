package telas;

import static org.lwjgl.glfw.GLFW.glfwGetWindowSize;
import static org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose;
import static org.lwjgl.glfw.GLFW.glfwSetWindowSizeCallback;
import static org.lwjgl.glfw.GLFW.glfwSetWindowTitle;

import audio.Audio;
import entrada.Entrada;

/**
 * Guarda a tela atual e faz as trocas entre telas.
 *
 * Trocas pedidas durante o quadro (trocarPara, sobrepor, fecharSobreposicao) só acontecem
 * em aplicarTrocas(), que o Main3D chama no fim do quadro. Assim nenhuma tela é trocada
 * no meio do próprio atualizar ou desenhar, o que deixaria estado pela metade.
 *
 * Tela sobreposta: uma segunda tela desenhada por cima da atual (a pausa sobre a partida).
 * Enquanto ela existe, só ela é atualizada: a tela de baixo fica congelada, mas continua
 * sendo desenhada.
 */
public class GerenciadorTelas {

	/** Maior diftime repassado às telas: depois de um travamento (ex.: gerar o mapa), nada "pula". */
	public static final long DIFTIME_MAXIMO = 100;

	private final long janela;
	private final Entrada entrada;
	private final Audio audio;

	private Tela atual;
	private Tela sobreposta;

	private Tela proxima;
	private Tela proximaSobreposta;
	private boolean fecharSobreposta = false;

	/** Tamanho da área de desenho do OpenGL, em pixels reais (usado no glViewport e na projeção 3D). */
	private int largura = 1;
	private int altura = 1;

	/**
	 * Tamanho da janela nas coordenadas do mouse (usado na interface 2D). Em telas comuns é
	 * igual ao de cima; em telas de alta densidade o framebuffer pode ter o dobro de pixels.
	 */
	private int larguraJanela = 1;
	private int alturaJanela = 1;

	/** Sem áudio (usado nos testes): um Audio que não foi iniciado fica mudo. */
	public GerenciadorTelas(long janela, Entrada entrada) {
		this(janela, entrada, new Audio());
	}

	public GerenciadorTelas(long janela, Entrada entrada, Audio audio) {
		this.janela = janela;
		this.entrada = entrada;
		this.audio = audio;
		if (janela != 0) {
			int[] w = new int[1], h = new int[1];
			glfwGetWindowSize(janela, w, h);
			redimensionarJanela(w[0], h[0]);
			glfwSetWindowSizeCallback(janela, (j, nw, nh) -> redimensionarJanela(nw, nh));
		}
	}

	// ---------- pedidos de troca (aplicados no fim do quadro) ----------

	/** Agenda a troca da tela atual. Uma sobreposição aberta é fechada junto. */
	public void trocarPara(Tela nova) {
		proxima = nova;
	}

	/** Agenda uma tela por cima da atual (a de baixo congela). */
	public void sobrepor(Tela tela) {
		proximaSobreposta = tela;
	}

	/** Agenda o fechamento da tela sobreposta. */
	public void fecharSobreposicao() {
		fecharSobreposta = true;
	}

	/** Aplica as trocas pedidas durante o quadro. Chamado pelo Main3D depois de desenhar. */
	public void aplicarTrocas() {
		if (fecharSobreposta) {
			fecharSobreposta = false;
			if (sobreposta != null) {
				sobreposta.sair();
				sobreposta = null;
			}
		}
		if (proxima != null) {
			Tela nova = proxima;
			proxima = null;
			if (sobreposta != null) {
				sobreposta.sair();
				sobreposta = null;
			}
			if (atual != null) {
				atual.sair();
			}
			atual = nova;
			atual.entrar();
		}
		if (proximaSobreposta != null) {
			Tela nova = proximaSobreposta;
			proximaSobreposta = null;
			if (sobreposta != null) {
				sobreposta.sair();
			}
			sobreposta = nova;
			sobreposta.entrar();
		}
	}

	// ---------- quadro ----------

	public void atualizar(long diftime) {
		long dt = Math.min(diftime, DIFTIME_MAXIMO);
		if (sobreposta != null) {
			sobreposta.atualizar(dt);
		} else if (atual != null) {
			atual.atualizar(dt);
		}
	}

	public void desenhar() {
		if (atual != null) {
			atual.desenhar();
		}
		if (sobreposta != null) {
			sobreposta.desenhar();
		}
	}

	/** Chamado ao fechar o jogo, para a tela atual (e a sobreposta) liberarem o que usam. */
	public void encerrar() {
		if (sobreposta != null) {
			sobreposta.sair();
			sobreposta = null;
		}
		if (atual != null) {
			atual.sair();
			atual = null;
		}
	}

	// ---------- informações para as telas ----------

	public Tela atual() {
		return atual;
	}

	public Tela sobreposta() {
		return sobreposta;
	}

	public Entrada entrada() {
		return entrada;
	}

	public Audio audio() {
		return audio;
	}

	/** Tamanho da área de desenho em pixels, atualizado pelo callback de redimensionamento. */
	public void redimensionar(int largura, int altura) {
		this.largura = Math.max(1, largura);
		this.altura = Math.max(1, altura);
	}

	public void redimensionarJanela(int largura, int altura) {
		this.larguraJanela = Math.max(1, largura);
		this.alturaJanela = Math.max(1, altura);
	}

	public int largura() {
		return largura;
	}

	/** Largura para a interface 2D (mesmas coordenadas do mouse). */
	public int larguraJanela() {
		return larguraJanela;
	}

	public int alturaJanela() {
		return alturaJanela;
	}

	public int altura() {
		return altura;
	}

	/** Proporção largura / altura, para a projeção não distorcer a imagem. */
	public float proporcao() {
		return (float) largura / altura;
	}

	public void definirTitulo(String titulo) {
		if (janela != 0) {
			glfwSetWindowTitle(janela, "Univali Uncanny | " + titulo);
		}
	}

	/** Pede para o jogo fechar ao fim do quadro (botão "Sair" do menu). */
	public void fecharJogo() {
		if (janela != 0) {
			glfwSetWindowShouldClose(janela, true);
		}
	}
}
