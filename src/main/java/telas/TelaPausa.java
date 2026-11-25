package telas;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;

import java.awt.Color;

import audio.SonsInterface;
import dados.Recursos;
import render.Botao;
import render.GrupoBotoes;
import render.Overlay2D;
import render.Texto;

/**
 * A pausa (ESC), desenhada POR CIMA da partida, que fica congelada: o gerenciador de telas só
 * atualiza a tela de cima, então o tempo da partida e os cronômetros do Dragon Knight param.
 * Todos os sons pausam (alSourcePause) e voltam de onde pararam ao continuar.
 *
 * Botões: Continuar (ou ESC), Carta (relê a carta do balcão, depois de pegá-la), Opções (o mesmo
 * painel do menu: volumes e sensibilidade) e Desistir, que pede confirmação antes de voltar ao menu. A seed aparece embaixo, para anotar
 * um mapa bom.
 */
public class TelaPausa implements Tela {

	private static final Color VEU = new Color(0, 0, 0, 175);
	private static final Color COR_TITULO = new Color(235, 230, 220);
	private static final Color COR_DICA = new Color(150, 145, 140);
	private static final Color COR_AVISO = new Color(220, 120, 110);

	private final GerenciadorTelas gerenciador;
	private final long seed;

	private final GrupoBotoes botoes = new GrupoBotoes();
	private final Botao continuar, carta, opcoes, desistir;

	private final GrupoBotoes confirmacao = new GrupoBotoes();
	// o "Não" vem primeiro: o foco começa nele, e dois Enter seguidos não desistem sem querer
	private final Botao nao = criarBotao(confirmacao, "Não");
	private final Botao sim = criarBotao(confirmacao, "Sim, desistir");
	private boolean confirmando = false;
	private boolean desistiu = false;

	private Painel painel = null;

	/** temCarta: o jogador já pegou a carta (aparece o botão para relê-la). */
	public TelaPausa(GerenciadorTelas gerenciador, long seed, boolean temCarta) {
		this.gerenciador = gerenciador;
		this.seed = seed;
		continuar = criarBotao(botoes, "Continuar");
		carta = temCarta ? criarBotao(botoes, "Carta") : null;
		opcoes = criarBotao(botoes, "Opções");
		desistir = criarBotao(botoes, "Desistir");
	}

	private static Botao criarBotao(GrupoBotoes grupo, String texto) {
		Botao b = grupo.adicionar(new Botao(texto, 0, 0, 380, 50));
		b.estilo = Botao.Estilo.TEXTO;
		b.fonte = Recursos.fonteTitulo();
		return b;
	}

	@Override
	public void entrar() {
		gerenciador.definirTitulo("PAUSADO");
		gerenciador.entrada().prenderCursor(false);
		gerenciador.audio().pausarTudo();
	}

	@Override
	public void sair() {
		if (!desistiu) {
			gerenciador.audio().retomarTudo(); // desistindo, a partida sai e para os sons dela
		}
		// a pausa sempre fica sobre a partida, que usa o cursor preso
		gerenciador.entrada().prenderCursor(true);
		gerenciador.definirTitulo(TelaJogo.TITULO);
	}

	@Override
	public void atualizar(long diftime) {
		int w = gerenciador.larguraJanela(), h = gerenciador.alturaJanela();
		if (painel != null) {
			if (painel.atualizar(gerenciador.entrada(), w, h, diftime)) {
				SonsInterface.clique(gerenciador.audio()); // Salvar, Cancelar ou ESC
				painel = null;
			}
			return;
		}
		if (gerenciador.entrada().foiPressionada(GLFW_KEY_ESCAPE)) {
			if (confirmando) {
				confirmando = false;
			} else {
				gerenciador.fecharSobreposicao();
			}
			return;
		}
		GrupoBotoes grupo = confirmando ? confirmacao : botoes;
		posicionar(grupo, w, h);
		Botao acionado = grupo.atualizar(gerenciador.entrada());
		if (grupo.focoMudou()) SonsInterface.passar(gerenciador.audio());
		if (acionado != null) SonsInterface.clique(gerenciador.audio());

		if (acionado == continuar) {
			gerenciador.fecharSobreposicao();
		} else if (acionado != null && acionado == carta) {
			// a carta por cima da partida; ao fechar, volta para a pausa
			gerenciador.sobrepor(new TelaCarta(gerenciador, () -> gerenciador.sobrepor(new TelaPausa(gerenciador, seed, true))));
			return;
		} else if (acionado == opcoes) {
			painel = new PainelOpcoes(gerenciador.audio());
		} else if (acionado == desistir) {
			confirmando = true;
		} else if (acionado == nao) {
			confirmando = false;
		} else if (acionado == sim) {
			desistiu = true;
			gerenciador.trocarPara(new TelaMenu(gerenciador)); // a partida sai e libera o que usa
		}
	}

	/** Os botões em coluna, centralizados, abaixo do título. */
	private static void posicionar(GrupoBotoes grupo, int w, int h) {
		int tamanho = Math.max(22, Math.min(42, Math.round(h * 0.04f)));
		float passo = tamanho * 1.6f, y = h * 0.45f;
		for (Botao b : grupo.botoes()) {
			b.tamanhoTexto = tamanho;
			b.largura = Texto.largura(b.fonte, b.texto, tamanho) + 24;
			b.altura = passo - 4;
			b.posicionar(w / 2f - b.largura / 2, y);
			y += passo;
		}
	}

	@Override
	public void desenhar() {
		int w = gerenciador.larguraJanela(), h = gerenciador.alturaJanela();
		Overlay2D o = Recursos.overlay();
		o.iniciar(w, h);
		o.retangulo(0, 0, w, h, VEU);
		if (painel != null) {
			painel.desenhar(o, w, h);
		} else {
			int titulo = Math.max(40, Math.min(96, Math.round(h * 0.09f)));
			Texto.desenharCentralizado(o, Recursos.fonteTitulo(), "PAUSADO", w / 2f, h * 0.28f, titulo, COR_TITULO, true, true);
			if (confirmando) {
				Texto.desenhar(o, "Desistir da partida e voltar ao menu?", w / 2f, h * 0.37f, Math.max(16, Math.round(h * 0.028f)),
						COR_AVISO, Texto.Alinhamento.CENTRO);
				posicionar(confirmacao, w, h);
				confirmacao.desenhar(o);
			} else {
				posicionar(botoes, w, h);
				botoes.desenhar(o);
			}
			Texto.desenhar(o, "Mapa (seed) " + seed + "      ESC: continuar", w / 2f, h - 50, 18, COR_DICA,
					Texto.Alinhamento.CENTRO);
		}
		o.finalizar();
	}
}
