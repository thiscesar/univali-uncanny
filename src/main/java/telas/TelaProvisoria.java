package telas;

import static org.lwjgl.opengl.GL11.*;

import java.awt.Color;

import dados.Recursos;
import render.Overlay2D;
import render.Texto;

/**
 * Base das telas provisórias: pintam a janela de uma cor e mostram o nome no centro,
 * com uma dica de teclas embaixo. Serão substituídas pelas telas de verdade.
 */
public abstract class TelaProvisoria implements Tela {

	private static final Color COR_NOME = new Color(235, 230, 220);
	private static final Color COR_DICA = new Color(170, 165, 160);

	protected final GerenciadorTelas gerenciador;
	private final String nome;
	private String dica;
	private final float r, g, b;

	protected TelaProvisoria(GerenciadorTelas gerenciador, String nome, String dica, float r, float g, float b) {
		this.gerenciador = gerenciador;
		this.nome = nome;
		this.dica = dica;
		this.r = r;
		this.g = g;
		this.b = b;
	}

	/** Troca a dica embaixo do nome (e o título da janela). */
	protected void definirDica(String dica) {
		this.dica = dica;
		gerenciador.definirTitulo(nome + " (" + dica + ")");
	}

	@Override
	public void entrar() {
		gerenciador.definirTitulo(nome + " (" + dica + ")");
	}

	@Override
	public void sair() {
	}

	@Override
	public void desenhar() {
		glClearColor(r, g, b, 1.0f);
		glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

		int w = gerenciador.larguraJanela();
		int h = gerenciador.alturaJanela();
		Overlay2D overlay = Recursos.overlay();
		overlay.iniciar(w, h);
		Texto.desenharCentralizado(overlay, Recursos.fonteTitulo(), nome, w / 2f, h / 2f - 40, 72,
				COR_NOME, true, true);
		Texto.desenhar(overlay, dica, w / 2f, h / 2f + 10, 22, COR_DICA, Texto.Alinhamento.CENTRO);
		desenharExtras(overlay, w, h);
		overlay.finalizar();
	}

	/** Para as subclasses desenharem algo a mais (botões...) com o overlay já iniciado. */
	protected void desenharExtras(Overlay2D overlay, int largura, int altura) {
	}
}
