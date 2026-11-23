package telas;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;

import java.awt.Color;

import dados.Recursos;
import entrada.Entrada;
import render.Botao;
import render.GrupoBotoes;
import render.Overlay2D;
import render.Texto;

/** Tabela de controles do jogo. ESC ou "Voltar" fecham. */
class PainelControles extends Painel {

	private static final String[][] CONTROLES = {
			{ "Andar", "W  A  S  D" },
			{ "Olhar", "mouse" },
			{ "Correr", "Shift" },
			{ "Atirar", "botão esquerdo" },
			{ "Recarregar", "R" },
			{ "Lanterna", "F" },
			{ "Interagir (pegar prova, caixa, entregar)", "E" },
			{ "Pausa", "ESC" },
	};
	private static final Color COR_ACAO = new Color(190, 182, 170);
	private static final Color COR_TECLA = new Color(225, 60, 50);

	private final GrupoBotoes botoes = new GrupoBotoes();
	private final Botao voltar = botoes.adicionar(new Botao("Voltar", 0, 0, 200, 46));

	@Override
	boolean atualizar(Entrada entrada, int w, int h, long diftime) {
		layout(w, h);
		voltar.posicionar(px + (pl - voltar.largura) / 2, py + pa - voltar.altura - 24);
		return entrada.foiPressionada(GLFW_KEY_ESCAPE) || botoes.atualizar(entrada) == voltar;
	}

	@Override
	void desenhar(Overlay2D overlay, int w, int h) {
		desenharMoldura(overlay, "Controles");
		float linha = tamanhoTexto * 1.9f;
		float y = py + 110;
		for (String[] c : CONTROLES) {
			Texto.desenharAlinhado(overlay, Recursos.fonteTexto(), c[0], px + 50, y, tamanhoTexto, COR_ACAO,
					Texto.Alinhamento.ESQUERDA, false, true);
			Texto.desenharAlinhado(overlay, Recursos.fonteTexto(), c[1], px + pl - 50, y, tamanhoTexto, COR_TECLA,
					Texto.Alinhamento.DIREITA, false, true);
			y += linha;
		}
		botoes.desenhar(overlay);
	}
}
