package telas;

import static org.lwjgl.glfw.GLFW.*;

import java.util.Locale;
import java.util.function.Function;

import audio.Audio;
import audio.SonsInterface;
import dados.Opcoes;
import entrada.Entrada;
import render.Alternador;
import render.Botao;
import render.CampoTexto;
import render.Deslizante;
import render.Overlay2D;
import render.Seletor;

/**
 * Opções: volumes, mouse (sensibilidade e eixo Y), vídeo (campo de visão, árvores, modo PS1, tela cheia, sombras) e seed do mapa. As mudanças só valem ao
 * "Salvar" (que grava o config.properties); "Cancelar" e ESC descartam.
 *
 * Teclado: setas para cima e para baixo (ou Tab) escolhem a linha; esquerda e direita
 * mudam o controle selecionado; digitar escreve na seed; Enter aciona o botão selecionado.
 */
class PainelOpcoes extends Painel {

	private static final Function<Float, String> PORCENTAGEM = v -> Math.round(v * 100) + "%";

	private final Deslizante volumeGeral, volumeMusica, volumeEfeitos, sensibilidade, campoVisao, densidade;
	private final Alternador inverterY, modoPs1, telaCheia;
	private final Seletor sombras;
	private final CampoTexto seed;
	private final Botao salvar = new Botao("Salvar", 0, 0, 200, 46);
	private final Botao cancelar = new Botao("Cancelar", 0, 0, 200, 46);

	/** Para ouvir a mudança de volume na hora, enquanto o controle é arrastado. */
	private final Audio audio;
	/** Para pôr a janela em tela cheia ao salvar. */
	private final GerenciadorTelas gerenciador;

	/**
	 * As linhas, de cima para baixo: 0 a 3 volumes e sensibilidade; 4 inverter Y; 5 campo de visão;
	 * 6 densidade das árvores; 7 modo PS1; 8 tela cheia; 9 sombras; 10 seed; 11 Salvar; 12 Cancelar.
	 */
	private int foco = 0;
	private static final int INVERTER = 4, CAMPO = 5, DENSIDADE = 6, PS1 = 7, TELA_CHEIA = 8, SOMBRAS = 9, SEED = 10,
			SALVAR = 11, CANCELAR = 12, ITENS = 13, LINHAS = 11;

	PainelOpcoes(GerenciadorTelas gerenciador) {
		this.gerenciador = gerenciador;
		this.audio = gerenciador.audio();
		Opcoes o = Opcoes.atual();
		volumeGeral = new Deslizante("Volume geral", o.volumeGeral, PORCENTAGEM);
		volumeMusica = new Deslizante("Música", o.volumeMusica, PORCENTAGEM);
		volumeEfeitos = new Deslizante("Efeitos", o.volumeEfeitos, PORCENTAGEM);
		sensibilidade = new Deslizante("Sensibilidade do mouse", paraDeslizante(o.sensibilidade),
				v -> String.format(Locale.ROOT, "%.2fx", deDeslizante(v)));
		inverterY = new Alternador("Inverter o eixo Y do mouse", o.inverterY);
		campoVisao = new Deslizante("Campo de visão", entre(o.campoVisao, Opcoes.CAMPO_VISAO_MINIMO, Opcoes.CAMPO_VISAO_MAXIMO),
				v -> Math.round(de(v, Opcoes.CAMPO_VISAO_MINIMO, Opcoes.CAMPO_VISAO_MAXIMO)) + "°");
		densidade = new Deslizante("Quantidade de árvores",
				entre(o.densidadeArvores, Opcoes.DENSIDADE_MINIMA, Opcoes.DENSIDADE_MAXIMA),
				v -> Math.round(de(v, Opcoes.DENSIDADE_MINIMA, Opcoes.DENSIDADE_MAXIMA) * 100) + "%");
		modoPs1 = new Alternador("Modo PS1 (pixels grandes)", o.modoPs1);
		telaCheia = new Alternador("Tela cheia (F11)", gerenciador.telaCheia());
		sombras = new Seletor("Sombras da lanterna", new String[] { "desligadas", "médias", "altas" }, o.sombras);
		seed = new CampoTexto("Seed do mapa", o.seed, 9, true, "vazia = mapa novo");
	}

	/** Os deslizantes com a linha de cada um. */
	private Deslizante deslizante(int linha) {
		return switch (linha) {
		case 0 -> volumeGeral;
		case 1 -> volumeMusica;
		case 2 -> volumeEfeitos;
		case 3 -> sensibilidade;
		case CAMPO -> campoVisao;
		case DENSIDADE -> densidade;
		default -> null;
		};
	}

	private Alternador alternador(int linha) {
		return linha == INVERTER ? inverterY : linha == PS1 ? modoPs1 : linha == TELA_CHEIA ? telaCheia : null;
	}

	@Override
	boolean atualizar(Entrada entrada, int w, int h, long diftime) {
		layout(w, h);
		posicionar();
		int focoAntes = foco;
		String valoresAntes = valores();

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
		if (foco >= SALVAR) { // na linha dos botões, esquerda e direita escolhem o botão
			if (entrada.foiPressionada(GLFW_KEY_LEFT)) foco = SALVAR;
			if (entrada.foiPressionada(GLFW_KEY_RIGHT)) foco = CANCELAR;
		}

		for (int i = 0; i < SEED; i++) {
			Deslizante d = deslizante(i);
			Alternador a = alternador(i);
			boolean clicou = d != null ? d.atualizar(entrada, foco == i)
					: a != null ? a.atualizar(entrada, foco == i) : sombras.atualizar(entrada, foco == i);
			if (clicou) {
				foco = i;
			}
		}
		if (seed.atualizar(entrada, foco == SEED, diftime)) {
			foco = SEED;
		}

		salvar.definirFoco(foco == SALVAR);
		cancelar.definirFoco(foco == CANCELAR);
		boolean clicouSalvar = salvar.atualizar(entrada);
		boolean clicouCancelar = cancelar.atualizar(entrada);

		// prévia: os volumes mudam na hora; "Cancelar" volta aos salvos
		audio.definirVolumes(volumeGeral.valor, volumeMusica.valor, volumeEfeitos.valor);

		boolean enter = entrada.foiPressionada(GLFW_KEY_ENTER) || entrada.foiPressionada(GLFW_KEY_KP_ENTER);
		if (clicouSalvar || (enter && foco == SALVAR)) {
			aplicarESalvar();
			return true;
		}
		if (clicouCancelar || (enter && foco == CANCELAR)) {
			restaurarVolumes();
			return true;
		}
		if (enter && alternador(foco) == null && foco != SOMBRAS) {
			foco++; // Enter num controle passa para o próximo (num alternador ou seletor, o Enter troca o valor)
		}
		if (foco != focoAntes) SonsInterface.passar(audio);
		if (!valores().equals(valoresAntes)) SonsInterface.ajuste(audio);
		return false;
	}

	/** Os valores dos controles num texto só, para saber se algum mudou neste quadro. */
	private String valores() {
		return volumeGeral.valor + " " + volumeMusica.valor + " " + volumeEfeitos.valor + " " + sensibilidade.valor
				+ " " + campoVisao.valor + " " + densidade.valor + " " + inverterY.valor + " " + modoPs1.valor + " "
				+ telaCheia.valor + " " + sombras.indice;
	}

	/** Onze linhas de controles: a altura de cada uma diminui para caberem acima dos botões. */
	private void posicionar() {
		float x = px + 50, largura = pl - 100;
		float y = py + 95;
		float linha = Math.min(tamanhoTexto * 2.4f, (pa - 95 - salvar.altura - 40) / LINHAS);
		tamanhoTexto = Math.min(tamanhoTexto, Math.round(linha / 2.2f));
		for (int i = 0; i < LINHAS; i++) {
			if (deslizante(i) != null) deslizante(i).posicionar(x, y, largura, linha);
			else if (alternador(i) != null) alternador(i).posicionar(x, y, largura, linha);
			else if (i == SOMBRAS) sombras.posicionar(x, y, largura, linha);
			else seed.posicionar(x, y, largura, linha);
			y += linha;
		}
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
		o.inverterY = inverterY.valor;
		o.campoVisao = de(campoVisao.valor, Opcoes.CAMPO_VISAO_MINIMO, Opcoes.CAMPO_VISAO_MAXIMO);
		o.densidadeArvores = de(densidade.valor, Opcoes.DENSIDADE_MINIMA, Opcoes.DENSIDADE_MAXIMA);
		o.seed = seed.texto();
		o.modoPs1 = modoPs1.valor;
		o.sombras = sombras.indice;
		o.telaCheia = telaCheia.valor;
		o.salvar();
		gerenciador.definirTelaCheia(o.telaCheia);
	}

	private void restaurarVolumes() {
		Opcoes o = Opcoes.atual();
		audio.definirVolumes(o.volumeGeral, o.volumeMusica, o.volumeEfeitos);
	}

	@Override
	void desenhar(Overlay2D overlay, int w, int h) {
		desenharMoldura(overlay, "Opções");
		for (int i = 0; i < SEED; i++) {
			if (deslizante(i) != null) deslizante(i).desenhar(overlay, foco == i, tamanhoTexto);
			else if (alternador(i) != null) alternador(i).desenhar(overlay, foco == i, tamanhoTexto);
			else sombras.desenhar(overlay, foco == i, tamanhoTexto);
		}
		seed.desenhar(overlay, foco == SEED, tamanhoTexto);
		salvar.desenhar(overlay);
		cancelar.desenhar(overlay);
	}

	/** Um valor entre mínimo e máximo, levado para o deslizante (0 a 1), e de volta. */
	private static float entre(float valor, float minimo, float maximo) {
		return (valor - minimo) / (maximo - minimo);
	}

	private static float de(float v, float minimo, float maximo) {
		return minimo + v * (maximo - minimo);
	}

	/** A sensibilidade vai de 0,25x a 3x; o deslizante, de 0 a 1. */
	private static float paraDeslizante(float s) {
		return (s - Opcoes.SENSIBILIDADE_MINIMA) / (Opcoes.SENSIBILIDADE_MAXIMA - Opcoes.SENSIBILIDADE_MINIMA);
	}

	private static float deDeslizante(float v) {
		return Opcoes.SENSIBILIDADE_MINIMA + v * (Opcoes.SENSIBILIDADE_MAXIMA - Opcoes.SENSIBILIDADE_MINIMA);
	}
}
