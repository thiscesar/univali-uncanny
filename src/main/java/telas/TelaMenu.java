package telas;

import render.Botao;
import render.GrupoBotoes;
import render.Overlay2D;

/**
 * Menu provisório com dois botões de teste. Funcionam com o mouse ou com as setas e o Enter.
 * O Enter, sem mexer em nada, aciona o "Jogar" (o primeiro).
 */
public class TelaMenu extends TelaProvisoria {

	private static final float LARGURA_BOTAO = 260;
	private static final float ALTURA_BOTAO = 52;
	private static final float ESPACO = 18;

	private final GrupoBotoes botoes = new GrupoBotoes();
	private final Botao jogar = botoes.adicionar(new Botao("Jogar", 0, 0, LARGURA_BOTAO, ALTURA_BOTAO));
	private final Botao sair = botoes.adicionar(new Botao("Sair", 0, 0, LARGURA_BOTAO, ALTURA_BOTAO));

	public TelaMenu(GerenciadorTelas gerenciador) {
		super(gerenciador, "MENU", "setas e Enter, ou clique", 0.15f, 0.05f, 0.20f);
	}

	@Override
	public void atualizar(long diftime) {
		posicionarBotoes();
		Botao acionado = botoes.atualizar(gerenciador.entrada());
		if (acionado == jogar) {
			gerenciador.trocarPara(new TelaCarregando(gerenciador));
		} else if (acionado == sair) {
			gerenciador.fecharJogo();
		}
	}

	/** Botões empilhados abaixo do nome, centralizados (acompanham o tamanho da janela). */
	private void posicionarBotoes() {
		float x = (gerenciador.larguraJanela() - LARGURA_BOTAO) / 2;
		float y = gerenciador.alturaJanela() / 2f + 70;
		for (Botao b : botoes.botoes()) {
			b.posicionar(x, y);
			y += ALTURA_BOTAO + ESPACO;
		}
	}

	@Override
	protected void desenharExtras(Overlay2D overlay, int largura, int altura) {
		botoes.desenhar(overlay);
	}
}
