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
import render.Fonte;
import render.GrupoBotoes;
import render.Overlay2D;
import render.Texto;
import util.Caminhos;

/**
 * Créditos lidos do arquivo creditos.txt. Linhas começando com "# " viram títulos de seção.
 * Eles sobem sozinhos devagar, como os créditos de um filme; no fim, esperam um pouco e voltam
 * ao começo. A rodinha do mouse e as setas também rolam (e a rolagem sozinha espera uns segundos).
 * As linhas esmaecem perto das bordas da caixa. ESC ou "Voltar" fecham.
 */
class PainelCreditos extends Painel {

	private static final String ARQUIVO = "creditos.txt";
	private static final Color COR_SECAO = new Color(225, 60, 50);
	private static final Color COR_LINHA = new Color(200, 192, 180);
	private static final Color COR_DICA = new Color(130, 122, 114);
	/** Velocidade da rolagem sozinha, em linhas por segundo, e as esperas, em segundos. */
	private static final float LINHAS_POR_SEGUNDO = 0.9f, ESPERA_INICIO = 1.5f, ESPERA_MANUAL = 4f, ESPERA_FIM = 3f;

	private final List<String> linhas;
	private final GrupoBotoes botoes = new GrupoBotoes();
	private final Botao voltar = botoes.adicionar(new Botao("Voltar", 0, 0, 200, 46));
	private float rolagem = 0;
	/** Segundos até a rolagem sozinha andar de novo (no começo, depois da rodinha e no fim). */
	private float espera = ESPERA_INICIO;
	private boolean noFim = false;

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
		float antes = rolagem;
		rolagem -= (float) entrada.rolagem() * passo * 2;
		if (entrada.foiPressionadaOuRepetida(GLFW_KEY_DOWN)) rolagem += passo;
		if (entrada.foiPressionadaOuRepetida(GLFW_KEY_UP)) rolagem -= passo;
		float maximo = Math.max(0, linhas.size() * passo - areaVisivel());
		if (rolagem != antes) { // o jogador rolou: a rolagem sozinha espera
			espera = ESPERA_MANUAL;
			noFim = false;
		} else if (espera > 0) {
			espera -= diftime / 1000f;
		} else if (noFim) { // esperou no fim: volta ao começo
			rolagem = 0;
			noFim = false;
			espera = ESPERA_INICIO;
		} else {
			rolagem += LINHAS_POR_SEGUNDO * passo * diftime / 1000f;
			if (rolagem >= maximo) {
				noFim = true;
				espera = ESPERA_FIM;
			}
		}
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
		float fundo = topo() + areaVisivel();
		float faixa = passo * 0.6f; // onde as linhas esmaecem, logo fora da área (sem chegar no título nem no botão)
		for (String linha : linhas) {
			float alfa = Math.max(0, Math.min(1, Math.min(y - (topo() - faixa), fundo + faixa - (y + passo)) / faixa));
			if (alfa > 0) {
				boolean secao = linha.startsWith("# ");
				Color c = secao ? COR_SECAO : COR_LINHA;
				String texto = secao ? linha.substring(2) : linha;
				Fonte fonte = secao ? Recursos.fonteTitulo() : Recursos.fonteTexto();
				// linha mais larga que a caixa: diminui a letra até caber
				int tamanho = tamanhoTexto;
				while (tamanho > 10 && Texto.largura(fonte, texto, tamanho) > pl - 40) tamanho--;
				Texto.desenharAlinhado(overlay, fonte, texto, px + pl / 2, y + passo / 2, tamanho,
						new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.round(alfa * 255)), Texto.Alinhamento.CENTRO,
						false, true);
			}
			y += passo;
		}
		Texto.desenharAlinhado(overlay, Recursos.fonteTexto(), "rodinha do mouse ou setas: rolar", px + pl - 20,
				py + pa - 24 - voltar.altura / 2, Math.round(tamanhoTexto * 0.65f), COR_DICA, Texto.Alinhamento.DIREITA, false,
				true);
		botoes.desenhar(overlay);
	}
}
