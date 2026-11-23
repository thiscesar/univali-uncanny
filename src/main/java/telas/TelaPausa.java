package telas;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;

import java.awt.Color;

import dados.Recursos;
import render.Overlay2D;
import render.Texto;

/**
 * Pausa provisória, desenhada POR CIMA da partida congelada: escurece a cena com um
 * retângulo semitransparente e escreve "PAUSADO". ESC fecha a pausa.
 */
public class TelaPausa implements Tela {

	private static final Color VEU = new Color(0, 0, 0, 170);

	private final GerenciadorTelas gerenciador;

	public TelaPausa(GerenciadorTelas gerenciador) {
		this.gerenciador = gerenciador;
	}

	@Override
	public void entrar() {
		gerenciador.definirTitulo("PAUSADO (ESC: continuar)");
		gerenciador.entrada().prenderCursor(false);
	}

	@Override
	public void sair() {
		// a pausa sempre fica sobre a partida, que usa o cursor preso
		gerenciador.entrada().prenderCursor(true);
		gerenciador.definirTitulo(TelaJogo.TITULO);
	}

	@Override
	public void atualizar(long diftime) {
		if (gerenciador.entrada().foiPressionada(GLFW_KEY_ESCAPE)) {
			gerenciador.fecharSobreposicao();
		}
	}

	@Override
	public void desenhar() {
		int w = gerenciador.larguraJanela();
		int h = gerenciador.alturaJanela();
		Overlay2D overlay = Recursos.overlay();
		overlay.iniciar(w, h);
		overlay.retangulo(0, 0, w, h, VEU);
		Texto.desenharCentralizado(overlay, Recursos.fonteTitulo(), "PAUSADO", w / 2f, h / 2f - 30, 72,
				new Color(235, 230, 220), true, true);
		Texto.desenhar(overlay, "ESC: continuar", w / 2f, h / 2f + 20, 22, new Color(170, 165, 160),
				Texto.Alinhamento.CENTRO);
		overlay.finalizar();
	}
}
