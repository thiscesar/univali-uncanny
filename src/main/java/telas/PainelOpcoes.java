package telas;

import static org.lwjgl.glfw.GLFW.*;

import java.util.Locale;
import java.util.function.Function;

import audio.Audio;
import dados.Opcoes;
import entrada.Entrada;
import render.Botao;
import render.CampoTexto;
import render.Deslizante;
import render.Overlay2D;

/**
 * Opções: volumes, sensibilidade do mouse e seed do mapa. As mudanças só valem ao
 * "Salvar" (que grava o config.properties); "Cancelar" e ESC descartam.
 *
 * Teclado: setas para cima e para baixo (ou Tab) escolhem a linha; esquerda e direita
 * mudam o controle selecionado; digitar escreve na seed; Enter aciona o botão selecionado.
 */
class PainelOpcoes extends Painel {

	private static final Function<Float, String> PORCENTAGEM = v -> Math.round(v * 100) + "%";

	private final Deslizante volumeGeral, volumeMusica, volumeEfeitos, sensibilidade;
	private final Deslizante[] deslizantes;
	private final CampoTexto seed;
	private final Botao salvar = new Botao("Salvar", 0, 0, 200, 46);
	private final Botao cancelar = new Botao("Cancelar", 0, 0, 200, 46);

	/** Para ouvir a mudança de volume na hora, enquanto o controle é arrastado. */
	private final Audio audio;

	/** 0 a 3: deslizantes; 4: seed; 5: Salvar; 6: Cancelar. */
	private int foco = 0;
	private static final int ITENS = 7;

	PainelOpcoes(Audio audio) {
		this.audio = audio;
		Opcoes o = Opcoes.atual();
		volumeGeral = new Deslizante("Volume geral", o.volumeGeral, PORCENTAGEM);
		volumeMusica = new Deslizante("Música", o.volumeMusica, PORCENTAGEM);
		volumeEfeitos = new Deslizante("Efeitos", o.volumeEfeitos, PORCENTAGEM);
		sensibilidade = new Deslizante("Sensibilidade do mouse", paraDeslizante(o.sensibilidade),
				v -> String.format(Locale.ROOT, "%.2fx", deDeslizante(v)));
		deslizantes = new Deslizante[] { volumeGeral, volumeMusica, volumeEfeitos, sensibilidade };
		seed = new CampoTexto("Seed do mapa", o.seed, 9, true, "vazia = mapa novo");
	}

	@Override
	boolean atualizar(Entrada entrada, int w, int h, long diftime) {
		layout(w, h);
		posicionar();

		if (entrada.foiPressionada(GLFW_KEY_ESCAPE)) {
			restaurarVolumes();
			return true;
		}
		if (entrada.foiPressionadaOuRepetida(GLFW_KEY_DOWN) || entrada.foiPressionada(GLFW_KEY_TAB)) {
			foco = (foco + 1) % ITENS;
		}
		if (entrada.foiPressionadaOuRepetida(GLFW_KEY_UP)) {
			foco = (foco - 1 + ITENS) % ITENS;
		}
		if (foco >= 5) { // na linha dos botões, esquerda e direita escolhem o botão
			if (entrada.foiPressionada(GLFW_KEY_LEFT)) foco = 5;
			if (entrada.foiPressionada(GLFW_KEY_RIGHT)) foco = 6;
		}

		for (int i = 0; i < deslizantes.length; i++) {
			if (deslizantes[i].atualizar(entrada, foco == i)) {
				foco = i;
			}
		}
		if (seed.atualizar(entrada, foco == 4, diftime)) {
			foco = 4;
		}

		salvar.definirFoco(foco == 5);
		cancelar.definirFoco(foco == 6);
		boolean clicouSalvar = salvar.atualizar(entrada);
		boolean clicouCancelar = cancelar.atualizar(entrada);

		// prévia: os volumes mudam na hora; "Cancelar" volta aos salvos
		audio.definirVolumes(volumeGeral.valor, volumeMusica.valor, volumeEfeitos.valor);

		boolean enter = entrada.foiPressionada(GLFW_KEY_ENTER) || entrada.foiPressionada(GLFW_KEY_KP_ENTER);
		if (clicouSalvar || (enter && foco == 5)) {
			aplicarESalvar();
			return true;
		}
		if (clicouCancelar || (enter && foco == 6)) {
			restaurarVolumes();
			return true;
		}
		if (enter) {
			foco++; // Enter num controle passa para o próximo
		}
		return false;
	}

	private void posicionar() {
		float x = px + 50, largura = pl - 100;
		float linha = tamanhoTexto * 2.4f;
		float y = py + 100;
		for (Deslizante d : deslizantes) {
			d.posicionar(x, y, largura, linha);
			y += linha;
		}
		seed.posicionar(x, y, largura, linha);
		float by = py + pa - salvar.altura - 24;
		salvar.posicionar(px + pl / 2 - salvar.largura - 12, by);
		cancelar.posicionar(px + pl / 2 + 12, by);
	}

	private void aplicarESalvar() {
		Opcoes o = Opcoes.atual();
		o.volumeGeral = volumeGeral.valor;
		o.volumeMusica = volumeMusica.valor;
		o.volumeEfeitos = volumeEfeitos.valor;
		o.sensibilidade = deDeslizante(sensibilidade.valor);
		o.seed = seed.texto();
		o.salvar();
	}

	private void restaurarVolumes() {
		Opcoes o = Opcoes.atual();
		audio.definirVolumes(o.volumeGeral, o.volumeMusica, o.volumeEfeitos);
	}

	@Override
	void desenhar(Overlay2D overlay, int w, int h) {
		desenharMoldura(overlay, "Opções");
		for (int i = 0; i < deslizantes.length; i++) {
			deslizantes[i].desenhar(overlay, foco == i, tamanhoTexto);
		}
		seed.desenhar(overlay, foco == 4, tamanhoTexto);
		salvar.desenhar(overlay);
		cancelar.desenhar(overlay);
	}

	/** A sensibilidade vai de 0,25x a 3x; o deslizante, de 0 a 1. */
	private static float paraDeslizante(float s) {
		return (s - Opcoes.SENSIBILIDADE_MINIMA) / (Opcoes.SENSIBILIDADE_MAXIMA - Opcoes.SENSIBILIDADE_MINIMA);
	}

	private static float deDeslizante(float v) {
		return Opcoes.SENSIBILIDADE_MINIMA + v * (Opcoes.SENSIBILIDADE_MAXIMA - Opcoes.SENSIBILIDADE_MINIMA);
	}
}
