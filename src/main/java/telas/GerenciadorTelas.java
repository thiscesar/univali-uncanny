package telas;

import static org.lwjgl.glfw.GLFW.GLFW_DONT_CARE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_F11;
import static org.lwjgl.glfw.GLFW.glfwGetPrimaryMonitor;
import static org.lwjgl.glfw.GLFW.glfwGetVideoMode;
import static org.lwjgl.glfw.GLFW.glfwGetWindowMonitor;
import static org.lwjgl.glfw.GLFW.glfwGetWindowSize;
import static org.lwjgl.glfw.GLFW.glfwSetWindowMonitor;
import static org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose;
import static org.lwjgl.glfw.GLFW.glfwSetWindowSizeCallback;
import static org.lwjgl.glfw.GLFW.glfwSetWindowTitle;

import java.awt.Color;

import org.lwjgl.glfw.GLFWVidMode;

import audio.Audio;
import dados.Opcoes;
import dados.Recursos;
import entrada.Entrada;
import render.Identidade;
import render.Overlay2D;

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
 *
 * Transições: ao trocar de tela, a atual escurece até o preto (congelada) e a nova clareia a
 * partir dele. A sobreposição (a pausa) abre e fecha sem transição.
 *
 * Tela cheia: F11, em qualquer tela, alterna entre tela cheia e janela (e grava a escolha nas opções).
 */
public class GerenciadorTelas {

	/** Maior diftime repassado às telas: depois de um travamento (ex.: gerar o mapa), nada "pula". */
	public static final long DIFTIME_MAXIMO = 100;
	/** Tamanho da janela fora da tela cheia (o mesmo com que o Main3D a cria), limitado a 85% do monitor. */
	public static final int LARGURA_JANELA = 1500, ALTURA_JANELA = 1000;

	private final long janela;
	private final Entrada entrada;
	private final Audio audio;

	private Tela atual;
	private Tela sobreposta;

	private Tela proxima;
	private Tela proximaSobreposta;
	private boolean fecharSobreposta = false;

	/** Duração do escurecer (antes da troca) e do clarear (depois), em segundos. */
	static final float ESCURECER = 0.25f, CLAREAR = 0.35f;
	/** Sem janela (testes), as trocas são imediatas, como antes. */
	private boolean transicoes;
	/** Tempo escurecendo e clareando (-1 = não está). */
	private float saindo = -1, entrando = -1;

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
		this.transicoes = janela != 0;
		if (janela != 0) {
			Identidade.aplicar(janela); // ícone da janela e cursor do jogo
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
		if (transicoes && atual != null && saindo < 0) {
			saindo = 0; // escurece primeiro; a troca acontece quando estiver tudo preto
		}
	}

	/** Liga ou desliga as transições (os testes usam para conferir o escurecer e o clarear). */
	void ligarTransicoes(boolean ligar) {
		transicoes = ligar;
	}

	/** De 0 a 1: o quanto a tela está coberta de preto pela transição agora. */
	public float escuridao() {
		if (saindo >= 0) return Math.min(1, saindo / ESCURECER);
		if (entrando >= 0) return Math.max(0, 1 - entrando / CLAREAR);
		return 0;
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
		if (proxima != null && (saindo < 0 || saindo >= ESCURECER)) {
			Tela nova = proxima;
			proxima = null;
			saindo = -1;
			entrando = transicoes ? 0 : -1;
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
		if (janela != 0 && entrada.foiPressionada(GLFW_KEY_F11)) {
			Opcoes o = Opcoes.atual();
			o.telaCheia = !telaCheia();
			o.salvar();
			definirTelaCheia(o.telaCheia);
		}
		long dt = Math.min(diftime, DIFTIME_MAXIMO);
		if (saindo >= 0) {
			saindo += dt / 1000f; // escurecendo: as telas ficam congeladas até a troca
			return;
		}
		if (entrando >= 0) {
			entrando += dt / 1000f;
			if (entrando >= CLAREAR) entrando = -1;
		}
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
		float preto = escuridao();
		if (janela != 0 && preto > 0) {
			Overlay2D o = Recursos.overlay();
			o.iniciar(larguraJanela, alturaJanela);
			o.retangulo(0, 0, larguraJanela, alturaJanela, new Color(0f, 0f, 0f, preto));
			o.finalizar();
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

	/** A janela está em tela cheia (ligada a um monitor)? */
	public boolean telaCheia() {
		return janela != 0 && glfwGetWindowMonitor(janela) != 0;
	}

	/**
	 * Põe a janela em tela cheia no monitor principal, na resolução que ele já usa (sem trocar o modo
	 * de vídeo, então a troca é rápida), ou a devolve para uma janela centralizada. Os callbacks de
	 * tamanho avisam o redimensionar e o redimensionarJanela, como quando o jogador arrasta a borda.
	 */
	public void definirTelaCheia(boolean cheia) {
		long monitor = glfwGetPrimaryMonitor();
		if (janela == 0 || monitor == 0 || cheia == telaCheia()) {
			return;
		}
		GLFWVidMode modo = glfwGetVideoMode(monitor);
		if (cheia) {
			glfwSetWindowMonitor(janela, monitor, 0, 0, modo.width(), modo.height(), modo.refreshRate());
		} else {
			int w = Math.min(LARGURA_JANELA, Math.round(modo.width() * 0.85f));
			int h = Math.min(ALTURA_JANELA, Math.round(modo.height() * 0.85f));
			glfwSetWindowMonitor(janela, 0, (modo.width() - w) / 2, (modo.height() - h) / 2, w, h, GLFW_DONT_CARE);
		}
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
