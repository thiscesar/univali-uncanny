package telas;

import java.awt.Color;

import dados.Recursos;
import entrada.Entrada;
import render.Overlay2D;
import render.Texto;

/**
 * Um painel que abre por cima do menu (Controles, Opções, Créditos): uma caixa escura no
 * centro, com título. O menu continua animando atrás.
 */
abstract class Painel {

	private static final Color COR_CAIXA = new Color(8, 6, 10, 228);
	private static final Color COR_BORDA = new Color(110, 18, 18, 220);
	private static final Color COR_TITULO = new Color(214, 204, 188);

	/** Posição e tamanho da caixa, recalculados a cada quadro (a janela pode mudar de tamanho). */
	protected float px, py, pl, pa;
	/** Tamanho de letra base, proporcional à altura da janela. */
	protected int tamanhoTexto;

	protected void layout(int w, int h) {
		pl = Math.min(w * 0.66f, 880);
		pa = Math.min(h * 0.80f, 660);
		px = (w - pl) / 2;
		py = (h - pa) / 2;
		tamanhoTexto = Math.max(16, Math.min(28, Math.round(h * 0.027f)));
	}

	/** Atualiza o painel. Devolve true quando ele deve fechar. diftime em ms. */
	abstract boolean atualizar(Entrada entrada, int w, int h, long diftime);

	abstract void desenhar(Overlay2D overlay, int w, int h);

	protected void desenharMoldura(Overlay2D overlay, String titulo) {
		overlay.retangulo(px - 2, py - 2, pl + 4, pa + 4, COR_BORDA);
		overlay.retangulo(px, py, pl, pa, COR_CAIXA);
		Texto.desenharCentralizado(overlay, Recursos.fonteTitulo(), titulo, px + pl / 2, py + 50,
				Math.round(tamanhoTexto * 1.8f), COR_TITULO, true, true);
	}
}
