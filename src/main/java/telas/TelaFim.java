package telas;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.opengl.GL11.*;

import java.awt.Color;
import java.util.Random;

import audio.Audio;
import audio.Efeito;
import audio.Sintetizador;
import dados.Recursos;
import jogo.Partida;
import render.AtmosferaMenu;
import render.Botao;
import render.GrupoBotoes;
import render.Overlay2D;
import render.Texto;
import render.TextoTextura;

/**
 * O que as telas de fim de partida (APROVADO e REPROVADO) têm em comum:
 *  - o título "carimbado": entra enorme e bate no lugar em 0,2 s, com um clarão, uma pancada
 *    e a tela tremendo; depois fica tremendo de leve, como o título do menu;
 *  - uma frase que aparece letra por letra, como digitada;
 *  - uma linha de destaque (a média, ou as provas entregues) e as estatísticas da partida;
 *  - os botões Tentar de novo (mesmo mapa), Novo mapa e Menu, que aparecem depois do carimbo;
 *  - granulação e a lâmpada falhando por cima de tudo (os mesmos efeitos do menu).
 * O fundo e o que vem a mais em volta do título ficam com cada tela.
 */
public abstract class TelaFim implements Tela {

	/** Quando o carimbo bate e quando os botões aparecem, em segundos. */
	protected static final float CARIMBO = 0.2f;
	private static final float BOTOES = 1.6f;
	/** O título é gerado nesse tamanho e ampliado conforme a janela. */
	protected static final int TAMANHO_TITULO = 150;
	private static final Color COR_RESUMO = new Color(150, 140, 130);

	protected final GerenciadorTelas gerenciador;
	protected final Partida.Estatisticas estatisticas;
	protected final Random random = new Random();
	private final AtmosferaMenu atmosfera = new AtmosferaMenu();
	private final String frase;

	private final GrupoBotoes botoes = new GrupoBotoes();
	private final Botao tentarDeNovo = criarBotao("Tentar de novo");
	private final Botao novoMapa = criarBotao("Novo mapa");
	private final Botao menu = criarBotao("Menu");

	protected float tempo = 0;
	private boolean carimbou = false;
	private Efeito somCarimbo, somPassar, somClique;

	protected TelaFim(GerenciadorTelas gerenciador, Partida.Estatisticas estatisticas, String frase) {
		this.gerenciador = gerenciador;
		this.estatisticas = estatisticas;
		this.frase = frase;
	}

	private Botao criarBotao(String texto) {
		Botao b = botoes.adicionar(new Botao(texto, 0, 0, 300, 50));
		b.estilo = Botao.Estilo.TEXTO;
		b.fonte = Recursos.fonteTitulo();
		return b;
	}

	// ---------- o que cada tela define ----------

	protected abstract String titulo();

	protected abstract Color corTitulo();

	/** A linha grande embaixo da frase ("Média final: 8,7", "Provas entregues: 1 de 3"). */
	protected abstract String linhaDestaque();

	/** O fundo da tela (já com o overlay iniciado). */
	protected abstract void desenharFundo(Overlay2D o, int w, int h);

	/** Algo a mais junto do título (o sangue escorrendo), com a posição e a escala dele na tela. */
	protected void desenharJuntoDoTitulo(Overlay2D o, float x, float y, float escala) {
	}

	/** Um som a mais na entrada (além da pancada do carimbo). */
	protected void tocarEntrada(Audio audio) {
	}

	// ---------- ciclo da tela ----------

	@Override
	public void entrar() {
		gerenciador.definirTitulo(titulo());
		gerenciador.entrada().prenderCursor(false);
		Audio audio = gerenciador.audio();
		somCarimbo = audio.criarEfeito("pancada do carimbo", Sintetizador.pancada(44100), 44100);
		somPassar = audio.carregarEfeito("audio/menu_passar.ogg");
		somClique = audio.carregarEfeito("audio/menu_clique.ogg");
		tocarEntrada(audio);
	}

	@Override
	public void sair() {
	}

	@Override
	public void atualizar(long diftime) {
		tempo += diftime / 1000f;
		atmosfera.atualizar(diftime);
		if (!carimbou && tempo >= CARIMBO) {
			carimbou = true;
			gerenciador.audio().tocar(somCarimbo, 1f);
		}
		if (gerenciador.entrada().foiPressionada(GLFW_KEY_ESCAPE)) {
			gerenciador.trocarPara(new TelaMenu(gerenciador));
			return;
		}
		if (tempo < BOTOES) {
			return; // os botões só respondem depois de aparecer
		}
		posicionarBotoes(gerenciador.larguraJanela(), gerenciador.alturaJanela());
		Botao acionado = botoes.atualizar(gerenciador.entrada());
		if (botoes.focoMudou()) {
			gerenciador.audio().tocar(somPassar, 0.6f);
		}
		if (acionado != null) {
			gerenciador.audio().tocar(somClique, 0.9f);
		}
		if (acionado == tentarDeNovo) {
			// a mesma seed: a tela de carregamento reaproveita o mundo, sem rodar o AG de novo
			gerenciador.trocarPara(new TelaCarregando(gerenciador, estatisticas.seed()));
		} else if (acionado == novoMapa) {
			gerenciador.trocarPara(new TelaCarregando(gerenciador, random.nextInt(1_000_000)));
		} else if (acionado == menu) {
			gerenciador.trocarPara(new TelaMenu(gerenciador));
		}
	}

	/** Os três botões lado a lado, centralizados, no terço de baixo. */
	private void posicionarBotoes(int w, int h) {
		int tamanho = Math.max(20, Math.min(40, Math.round(h * 0.036f)));
		float espaco = tamanho * 2.2f;
		float total = 0;
		for (Botao b : botoes.botoes()) {
			b.tamanhoTexto = tamanho;
			b.largura = Texto.largura(b.fonte, b.texto, tamanho) + 24; // + o marcador e o avanço
			b.altura = tamanho * 1.5f;
			total += b.largura;
		}
		total += espaco * (botoes.botoes().size() - 1);
		float x = (w - total) / 2;
		for (Botao b : botoes.botoes()) {
			b.posicionar(x, h * 0.8f);
			x += b.largura + espaco;
		}
	}

	@Override
	public void desenhar() {
		glClearColor(0, 0, 0, 1);
		glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
		int w = gerenciador.larguraJanela(), h = gerenciador.alturaJanela();
		Overlay2D o = Recursos.overlay();
		o.iniciar(w, h);

		// no carimbo, a tela toda treme por meio segundo
		float tremor = carimbou ? Math.max(0, 1 - (tempo - CARIMBO) / 0.5f) : 0;
		float dx = (random.nextFloat() - 0.5f) * 18 * tremor, dy = (random.nextFloat() - 0.5f) * 18 * tremor;

		desenharFundo(o, w, h);
		desenharTitulo(o, w, h, dx, dy);
		desenharTextos(o, w, h);
		if (tempo >= BOTOES) {
			posicionarBotoes(w, h);
			botoes.desenhar(o);
		}
		atmosfera.desenharEfeitos(o, w, h);

		// clarão do carimbo
		if (carimbou && tempo - CARIMBO < 0.25f) {
			float a = 1 - (tempo - CARIMBO) / 0.25f;
			Color c = corTitulo();
			o.retangulo(0, 0, w, h, new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.round(90 * a)));
		}
		o.finalizar();
	}

	/** O título: de 2,5 vezes o tamanho até o tamanho final em 0,2 s, depois tremendo de leve. */
	private void desenharTitulo(Overlay2D o, int w, int h, float dx, float dy) {
		TextoTextura.Imagem img = Recursos.textos().obter(titulo(), Recursos.fonteTitulo(), TAMANHO_TITULO, corTitulo(),
				true, true);
		if (img == null) return;
		float p = Math.min(1, tempo / CARIMBO);
		float entrada = 1 + 1.5f * (1 - p) * (1 - p); // 2,5 -> 1
		float escala = Math.min(w * 0.85f / img.largura(), h * 0.22f / img.altura()) * entrada;
		float lw = img.largura() * escala, lh = img.altura() * escala;
		float tx = (random.nextFloat() - 0.5f) * 3, ty = (random.nextFloat() - 0.5f) * 3; // tremor leve
		float x = w / 2f - lw / 2 + dx + tx, y = h * 0.3f - lh / 2 + dy + ty;
		if (carimbou) {
			desenharJuntoDoTitulo(o, x, y, escala);
		}
		o.imagem(img.textura(), x, y, lw, lh, new Color(1f, 1f, 1f, p));
	}

	/** A frase, digitada letra por letra depois do carimbo, e o resumo da partida embaixo dela. */
	private void desenharTextos(Overlay2D o, int w, int h) {
		// uma textura só com a frase inteira; a "digitação" mostra uma fatia cada vez mais larga dela
		float mostrado = Math.max(0, Math.min(1, (tempo - 0.6f) * 30 / frase.length()));
		TextoTextura.Imagem img = Recursos.textos().obter(frase, Recursos.fonteTexto(), Math.max(18, Math.round(h * 0.034f)),
				new Color(205, 195, 182), false, true);
		if (img != null && mostrado > 0) {
			float x = w / 2f - img.largura() / 2f, y = Math.round(h * 0.5f - img.altura() / 2f);
			o.imagemRecorte(img.textura(), Math.round(x), y, img.largura() * mostrado, img.altura(), 0, 0, mostrado, 1,
					Color.WHITE);
		}
		if (tempo > 1.0f) {
			Texto.desenharAlinhado(o, Recursos.fonteTitulo(), linhaDestaque(), w / 2f, h * 0.58f,
					Math.max(20, Math.round(h * 0.04f)), corTitulo(), Texto.Alinhamento.CENTRO, true, true);
		}
		if (tempo > 1.4f) {
			Partida.Estatisticas e = estatisticas;
			int minutos = (int) (e.segundos() / 60), segundos = (int) (e.segundos() % 60);
			int n = e.aparicoesSobrevividas();
			String linha1 = String.format("Mapa %d   ·   %d:%02d na floresta   ·   %d %s", e.seed(), minutos, segundos, n,
					n == 1 ? "aparição sobrevivida" : "aparições sobrevividas");
			// as provas não entram aqui: o REPROVADO já mostra na linha de destaque, e o APROVADO é sempre 3 de 3
			String linha2 = String.format("%d %s   ·   pontaria de %d%%", e.tiros(), e.tiros() == 1 ? "tiro" : "tiros",
					e.precisao());
			int tamanho = Math.max(14, Math.round(h * 0.022f));
			Texto.desenharAlinhado(o, Recursos.fonteTexto(), linha1, w / 2f, h * 0.645f, tamanho, COR_RESUMO,
					Texto.Alinhamento.CENTRO, false, true);
			Texto.desenharAlinhado(o, Recursos.fonteTexto(), linha2, w / 2f, h * 0.645f + tamanho * 1.6f, tamanho, COR_RESUMO,
					Texto.Alinhamento.CENTRO, false, true);
		}
	}
}
