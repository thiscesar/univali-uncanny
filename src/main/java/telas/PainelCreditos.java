package telas;

import static org.lwjgl.glfw.GLFW.*;

import java.awt.Color;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import dados.Recursos;
import entrada.Entrada;
import render.Botao;
import render.GrupoBotoes;
import render.Overlay2D;
import render.Texto;
import util.Caminhos;

/**
 * Créditos lidos do arquivo creditos.txt. Linhas começando com "# " viram títulos de seção.
 * Rolagem com a rodinha do mouse ou com as setas. ESC ou "Voltar" fecham.
 */
class PainelCreditos extends Painel {

	private static final String ARQUIVO = "creditos.txt";
	private static final Color COR_SECAO = new Color(225, 60, 50);
	private static final Color COR_LINHA = new Color(200, 192, 180);

	private final List<String> linhas;
	private final GrupoBotoes botoes = new GrupoBotoes();
	private final Botao voltar = botoes.adicionar(new Botao("Voltar", 0, 0, 200, 46));
	private float rolagem = 0;

	PainelCreditos() {
		List<String> lidas;
		try {
			lidas = Files.readAllLines(Caminhos.arquivo(ARQUIVO).toPath(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			lidas = List.of("# Créditos", "(arquivo " + ARQUIVO + " não encontrado)");
		}
		linhas = lidas;
	}

	@Override
	boolean atualizar(Entrada entrada, int w, int h, long diftime) {
		layout(w, h);
		voltar.posicionar(px + (pl - voltar.largura) / 2, py + pa - voltar.altura - 24);

		float passo = tamanhoTexto * 1.6f;
		rolagem -= (float) entrada.rolagem() * passo * 2;
		if (entrada.foiPressionadaOuRepetida(GLFW_KEY_DOWN)) rolagem += passo;
		if (entrada.foiPressionadaOuRepetida(GLFW_KEY_UP)) rolagem -= passo;
		float maximo = Math.max(0, linhas.size() * passo - areaVisivel());
		rolagem = Math.max(0, Math.min(maximo, rolagem));

		return entrada.foiPressionada(GLFW_KEY_ESCAPE) || botoes.atualizar(entrada) == voltar;
	}

	private float topo() { return py + 100; }
	private float areaVisivel() { return pa - 100 - 90; }

	@Override
	void desenhar(Overlay2D overlay, int w, int h) {
		desenharMoldura(overlay, "Créditos");
		float passo = tamanhoTexto * 1.6f;
		float y = topo() - rolagem;
		for (String linha : linhas) {
			// só desenha as linhas que cabem dentro da caixa
			if (y >= topo() && y + passo <= topo() + areaVisivel()) {
				boolean secao = linha.startsWith("# ");
				Texto.desenharAlinhado(overlay, secao ? Recursos.fonteTitulo() : Recursos.fonteTexto(),
						secao ? linha.substring(2) : linha, px + pl / 2, y + passo / 2, tamanhoTexto,
						secao ? COR_SECAO : COR_LINHA, Texto.Alinhamento.CENTRO, false, true);
			}
			y += passo;
		}
		botoes.desenhar(overlay);
	}
}
