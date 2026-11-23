package telas;

import static org.lwjgl.opengl.GL11.*;

import java.awt.Color;
import java.util.Random;

import audio.Audio;
import audio.Efeito;
import dados.Opcoes;
import dados.Recursos;
import render.AtmosferaMenu;
import render.Botao;
import render.GrupoBotoes;
import render.Overlay2D;
import render.Texto;

/**
 * Menu principal: a Univali à noite ao fundo, neblina, vinheta, granulação, a lâmpada
 * falhando, o título tremendo e os botões. Controles, Opções e Créditos abrem painéis
 * por cima, com o menu ainda animando atrás.
 */
public class TelaMenu implements Tela {

	private static final String TITULO = "UNIVALI UNCANNY";
	private static final String SUBTITULO = "Três provas. Uma floresta. Três segundos.";
	private static final Color COR_SUBTITULO = new Color(150, 140, 130);
	private static final Color COR_DICA = new Color(95, 90, 88);

	private static final String MUSICA = "audio/musica_menu.ogg";
	private static final long FADE_ENTRADA_MS = 2500;
	private static final long FADE_SAIDA_MS = 1200;

	private final GerenciadorTelas gerenciador;
	private final AtmosferaMenu atmosfera = new AtmosferaMenu();
	private final Random random = new Random();

	private final GrupoBotoes botoes = new GrupoBotoes();
	private final Botao jogar = criarBotao("Jogar");
	private final Botao controles = criarBotao("Controles");
	private final Botao opcoes = criarBotao("Opções");
	private final Botao creditos = criarBotao("Créditos");
	private final Botao bestiario = criarBotao("Bestiário"); // o Dragon Knight girando, com as animações
	private final Botao sair = criarBotao("Sair");

	/** Painel aberto por cima do menu, ou null. */
	private Painel painel = null;

	public TelaMenu(GerenciadorTelas gerenciador) {
		this.gerenciador = gerenciador;
	}

	private Botao criarBotao(String texto) {
		Botao b = botoes.adicionar(new Botao(texto, 0, 0, 380, 50));
		b.estilo = Botao.Estilo.TEXTO;
		b.fonte = Recursos.fonteTitulo();
		return b;
	}

	/** Sons curtos dos botões (arquivos ausentes tocam silêncio). */
	private Efeito somPassar, somClique;

	@Override
	public void entrar() {
		gerenciador.definirTitulo("Menu");
		gerenciador.entrada().prenderCursor(false);
		Audio audio = gerenciador.audio();
		somPassar = audio.carregarEfeito("audio/menu_passar.ogg");
		somClique = audio.carregarEfeito("audio/menu_clique.ogg");
		audio.musica().tocar(MUSICA, true);
		audio.musica().fadeIn(FADE_ENTRADA_MS);
	}

	@Override
	public void sair() {
		gerenciador.audio().musica().fadeOut(FADE_SAIDA_MS);
	}

	@Override
	public void atualizar(long diftime) {
		atmosfera.atualizar(diftime);
		// visualizador do modelo do monstro (depuração): F9 ou M, porque em muitos notebooks
		// o F9 é uma tecla de mídia e não chega ao jogo
		if (painel == null && (gerenciador.entrada().foiPressionada(org.lwjgl.glfw.GLFW.GLFW_KEY_F9)
				|| gerenciador.entrada().foiPressionada(org.lwjgl.glfw.GLFW.GLFW_KEY_M))) {
			gerenciador.trocarPara(new TelaVisualizadorMd2(gerenciador));
			return;
		}
		int w = gerenciador.larguraJanela(), h = gerenciador.alturaJanela();

		if (painel != null) {
			if (painel.atualizar(gerenciador.entrada(), w, h, diftime)) {
				painel = null;
			}
			return;
		}

		posicionarBotoes(w, h);
		Botao acionado = botoes.atualizar(gerenciador.entrada());
		if (botoes.focoMudou()) {
			gerenciador.audio().tocar(somPassar, 0.6f);
		}
		if (acionado != null) {
			gerenciador.audio().tocar(somClique, 0.9f);
		}
		if (acionado == jogar) {
			long seed = Opcoes.atual().seedDaPartida(random);
			gerenciador.trocarPara(new TelaCarregando(gerenciador, seed));
		} else if (acionado == controles) {
			painel = new PainelControles();
		} else if (acionado == opcoes) {
			painel = new PainelOpcoes(gerenciador.audio());
		} else if (acionado == creditos) {
			painel = new PainelCreditos();
		} else if (acionado == bestiario) {
			gerenciador.trocarPara(new TelaVisualizadorMd2(gerenciador));
		} else if (acionado == sair) {
			gerenciador.fecharJogo();
		}
	}

	/** Coluna de botões à esquerda, abaixo da linha do horizonte da foto. */
	private void posicionarBotoes(int w, int h) {
		int tamanho = Math.max(22, Math.min(46, Math.round(h * 0.042f)));
		float passo = tamanho * 1.55f;
		float x = w * 0.07f;
		float y = h * 0.47f;
		for (Botao b : botoes.botoes()) {
			b.tamanhoTexto = tamanho;
			b.altura = passo - 4;
			b.posicionar(x, y);
			y += passo;
		}
	}

	@Override
	public void desenhar() {
		glClearColor(0, 0, 0, 1);
		glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

		int w = gerenciador.larguraJanela(), h = gerenciador.alturaJanela();
		Overlay2D overlay = Recursos.overlay();
		overlay.iniciar(w, h);
		atmosfera.desenharFundo(overlay, w, h);

		if (painel == null) {
			int tamanhoTitulo = Math.max(36, Math.min(120, Math.round(h * 0.10f)));
			atmosfera.desenharTitulo(overlay, TITULO, w / 2f, h * 0.15f, tamanhoTitulo);
			Texto.desenharAlinhado(overlay, Recursos.fonteTexto(), SUBTITULO, w / 2f, h * 0.15f + tamanhoTitulo * 0.75f,
					Math.max(14, Math.round(h * 0.022f)), COR_SUBTITULO, Texto.Alinhamento.CENTRO, false, true);
			botoes.desenhar(overlay);
			Texto.desenhar(overlay, "setas e Enter, ou mouse", w * 0.07f, h - 40, 16, COR_DICA,
					Texto.Alinhamento.ESQUERDA);
		} else {
			painel.desenhar(overlay, w, h);
		}

		atmosfera.desenharEfeitos(overlay, w, h);
		overlay.finalizar();
	}
}
