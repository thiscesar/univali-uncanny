package telas;

import static org.lwjgl.glfw.GLFW.*;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import audio.Sintetizador;
import dados.Recursos;
import entrada.Entrada;
import render.Fonte;
import render.Overlay2D;
import render.Texto;

/**
 * A carta do aluno que tentou fugir antes, lida como os arquivos de Resident Evil: a partida
 * congela (a tela fica POR CIMA dela, como a pausa) e a folha aparece no meio da tela, com o
 * texto na fonte de máquina de escrever. E, ESC, Enter, Espaço ou um clique fecham.
 *
 * Ela aparece ao pegar a escopeta no balcão do saguão e pode ser relida pela pausa ("Carta").
 */
public class TelaCarta implements Tela {

	/** O texto da carta. Linha vazia = espaço entre parágrafos; as linhas longas quebram sozinhas. */
	public static final String[] TEXTO = {
			"23:58. De novo.",
			"",
			"Se você está lendo isso, também dormiu na carteira.",
			"",
			"As provas estão na floresta. Deixei a escopeta aqui; o resto da munição está perto delas.",
			"",
			"Lá fora estão os jubilados. A luz atrai eles, e eles ouvem quem corre. A escopeta só derruba.",
			"",
			"Na volta, cuidado: tem uma coisa a cavalo aqui dentro.",
			"",
			"Eu não consegui.",
			"",
			"— R." };

	private static final Color VEU = new Color(0, 0, 0, 200);
	private static final Color TINTA = new Color(38, 32, 28);
	private static final Color COR_DICA = new Color(150, 145, 140);
	/** A folha: proporção (largura / altura) e quanto da altura da tela ela ocupa. */
	private static final float PROPORCAO = 0.72f, ALTURA = 0.84f;
	/** Tempo para a folha aparecer, e antes de aceitar fechar (para o E que a abriu não a fechar). */
	private static final float APARECER = 0.25f, TRAVA = 0.3f;

	private final GerenciadorTelas gerenciador;
	/** O que fazer ao fechar (relida pela pausa: volta para a pausa); null = volta para a partida. */
	private final Runnable depois;
	private float tempo = 0;

	public TelaCarta(GerenciadorTelas gerenciador, Runnable depois) {
		this.gerenciador = gerenciador;
		this.depois = depois;
	}

	@Override
	public void entrar() {
		gerenciador.definirTitulo("Carta");
		gerenciador.entrada().prenderCursor(false);
		gerenciador.audio().pausarTudo();
		folha();
	}

	@Override
	public void sair() {
		if (depois == null) {
			gerenciador.audio().retomarTudo();
			gerenciador.entrada().prenderCursor(true);
			gerenciador.definirTitulo(TelaJogo.TITULO);
		}
	}

	/** O som de papel ao abrir e ao fechar. */
	private void folha() {
		var audio = gerenciador.audio();
		audio.tocar(audio.criarEfeito("folha de papel", Sintetizador.folhaPapel(44100, 7), 44100), 0.8f);
	}

	@Override
	public void atualizar(long diftime) {
		tempo += diftime / 1000f;
		Entrada e = gerenciador.entrada();
		boolean fechar = e.foiPressionada(GLFW_KEY_E) || e.foiPressionada(GLFW_KEY_ESCAPE) || e.foiPressionada(GLFW_KEY_ENTER)
				|| e.foiPressionada(GLFW_KEY_SPACE) || e.botaoFoiPressionado(GLFW_MOUSE_BUTTON_LEFT);
		if (tempo > TRAVA && fechar) {
			folha();
			gerenciador.fecharSobreposicao();
			if (depois != null) depois.run();
		}
	}

	@Override
	public void desenhar() {
		int w = gerenciador.larguraJanela(), h = gerenciador.alturaJanela();
		float alfa = Math.min(1, tempo / APARECER);
		Overlay2D o = Recursos.overlay();
		o.iniciar(w, h);
		o.retangulo(0, 0, w, h, new Color(0, 0, 0, Math.round(VEU.getAlpha() * alfa)));

		float fh = h * ALTURA, fw = fh * PROPORCAO;
		fw = Math.min(fw, w * 0.9f);
		float fx = (w - fw) / 2, fy = (h - fh) / 2 + (1 - alfa) * h * 0.03f; // sobe um pouco ao aparecer
		int papel = Recursos.texturaGerada("carta: papel", TelaCarta::papel, false, false).id();
		o.imagem(papel, fx, fy, fw, fh, new Color(1f, 1f, 1f, alfa));

		// o texto: margens de 11% da largura, linhas quebradas para caber
		Fonte fonte = Recursos.fonteMaquina();
		int tamanho = Math.max(14, Math.round(fw * 0.042f));
		float margem = fw * 0.11f, largura = fw - 2 * margem;
		float y = fy + fh * 0.1f, entreLinhas = tamanho * 1.45f;
		Color tinta = new Color(TINTA.getRed(), TINTA.getGreen(), TINTA.getBlue(), Math.round(255 * alfa));
		for (String linha : quebrar(fonte, tamanho, largura)) {
			if (!linha.isEmpty()) {
				Texto.desenhar(o, fonte, linha, fx + margem, y, tamanho, tinta, Texto.Alinhamento.ESQUERDA, false, false);
			}
			y += linha.isEmpty() ? entreLinhas * 0.6f : entreLinhas;
		}
		if (tempo > TRAVA) {
			Texto.desenhar(o, "E ou ESC para fechar", w / 2f, fy + fh + h * 0.015f, Math.max(13, Math.round(h * 0.02f)),
					COR_DICA, Texto.Alinhamento.CENTRO);
		}
		o.finalizar();
	}

	/** As linhas do texto quebradas na largura (palavra por palavra), mantendo as linhas vazias. */
	private static List<String> quebrar(Fonte fonte, int tamanho, float largura) {
		List<String> linhas = new ArrayList<>();
		for (String paragrafo : TEXTO) {
			if (paragrafo.isEmpty()) {
				linhas.add("");
				continue;
			}
			StringBuilder atual = new StringBuilder();
			for (String palavra : paragrafo.split(" ")) {
				String tentativa = atual.length() == 0 ? palavra : atual + " " + palavra;
				if (atual.length() > 0 && Texto.largura(fonte, tentativa, tamanho) > largura) {
					linhas.add(atual.toString());
					atual = new StringBuilder(palavra);
				} else {
					atual = new StringBuilder(tentativa);
				}
			}
			linhas.add(atual.toString());
		}
		return linhas;
	}

	/**
	 * A folha: papel amarelado, mais escuro nas bordas, com grão, uma dobra no meio, manchas de
	 * água e uma de sangue seco num canto. Gerada uma vez (Java2D).
	 */
	static BufferedImage papel() {
		int w = 720, h = 1000;
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setPaint(new GradientPaint(0, 0, new Color(222, 208, 176), w, h, new Color(196, 178, 140)));
		g.fillRect(0, 0, w, h);
		Random r = new Random(58);
		for (int i = 0; i < 9000; i++) { // grão do papel
			int c = 120 + r.nextInt(80);
			g.setColor(new Color(c, c - 15, c - 40, 18));
			g.fillRect(r.nextInt(w), r.nextInt(h), 2, 2);
		}
		for (int i = 0; i < 6; i++) { // manchas de água
			int x = r.nextInt(w), y = r.nextInt(h), d = 60 + r.nextInt(140);
			g.setColor(new Color(150, 120, 70, 22));
			g.fillOval(x - d / 2, y - d / 2, d, d);
			g.setColor(new Color(130, 100, 55, 30));
			g.drawOval(x - d / 2, y - d / 2, d, d);
		}
		g.setColor(new Color(110, 25, 15, 70)); // sangue seco
		g.fillOval(w - 170, h - 150, 70, 52);
		g.fillOval(w - 120, h - 118, 30, 24);
		g.setColor(new Color(150, 130, 95, 70)); // a dobra no meio
		g.fillRect(0, h / 2 - 1, w, 2);
		g.setColor(new Color(255, 250, 235, 40));
		g.fillRect(0, h / 2 + 1, w, 2);
		for (int i = 0; i < 40; i++) { // bordas escurecidas
			g.setColor(new Color(90, 70, 40, 7));
			g.drawRect(i, i, w - 2 * i - 1, h - 2 * i - 1);
		}
		g.dispose();
		return img;
	}
}
