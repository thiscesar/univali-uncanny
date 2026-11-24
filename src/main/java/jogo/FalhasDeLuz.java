package jogo;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * As lâmpadas da Univali falhando, como lâmpadas fluorescentes velhas:
 *  - cada lâmpada "piscando" fica acesa por alguns segundos e, de repente, entra numa rajada de 1
 *    a 2 s acendendo e apagando rápido, de forma irregular (cada uma no seu tempo);
 *  - de vez em quando, com o jogador dentro do prédio, todas as lâmpadas falham juntas (uma queda
 *    de energia); às vezes terminam apagadas por um ou dois segundos.
 * Diz o brilho de cada lâmpada (0 apagada, 1 acesa) e avisa quando uma rajada começa (para o som).
 * Só dados: a TelaJogo e o DesenhoPredio usam o brilho, para o desenho e a luz baterem.
 */
public class FalhasDeLuz {

	/** Uma rajada começou: na lâmpada "lampada", ou em todas (geral). */
	public record Evento(int lampada, boolean geral) {}

	public static final float CALMA_MINIMA = 2.5f, CALMA_MAXIMA = 9f, RAJADA_MINIMA = 0.8f, RAJADA_MAXIMA = 2.0f;
	/** Quanto tempo cada estado (acesa ou apagada) dura dentro de uma rajada. */
	public static final float TROCA_MINIMA = 0.03f, TROCA_MAXIMA = 0.14f;
	/** A falha geral: carência, intervalo, duração da rajada, chance de terminar no escuro e quanto. */
	public static final float CARENCIA_GERAL = 25f, GERAL_MINIMO = 35f, GERAL_MAXIMO = 70f, RAJADA_GERAL = 1.8f;
	public static final float CHANCE_APAGAO = 0.35f, APAGAO_MINIMO = 1.2f, APAGAO_MAXIMO = 2.2f;

	private final boolean[] funciona, pisca;
	private final Random random;
	// por lâmpada: tempo até a próxima rajada (ou até o fim dela), se está em rajada, e o brilho do momento
	private final float[] cronometro, troca, brilho;
	private final boolean[] emRajada;
	// a falha geral
	private float cronometroGeral = CARENCIA_GERAL, rajadaGeral = 0, apagao = 0, trocaGeral = 0, brilhoGeral = 1;

	/**
	 * @param funciona quais lâmpadas acendem (as queimadas, não)
	 * @param pisca    quais delas são das que falham sozinhas
	 */
	public FalhasDeLuz(boolean[] funciona, boolean[] pisca, long semente) {
		this.funciona = funciona.clone();
		this.pisca = pisca.clone();
		random = new Random(semente);
		int n = funciona.length;
		cronometro = new float[n];
		troca = new float[n];
		brilho = new float[n];
		emRajada = new boolean[n];
		for (int i = 0; i < n; i++) {
			brilho[i] = funciona[i] ? 1 : 0;
			cronometro[i] = sortear(0.5f, CALMA_MAXIMA); // começam fora de sincronia
		}
	}

	/** Um passo do tempo. dentro: o jogador está no prédio (a falha geral só acontece com ele lá). */
	public List<Evento> atualizar(float dt, boolean dentro) {
		List<Evento> eventos = new ArrayList<>();
		for (int i = 0; i < funciona.length; i++) {
			if (!funciona[i] || !pisca[i]) continue;
			cronometro[i] -= dt;
			if (!emRajada[i] && cronometro[i] <= 0) {
				emRajada[i] = true;
				cronometro[i] = sortear(RAJADA_MINIMA, RAJADA_MAXIMA);
				troca[i] = 0;
				eventos.add(new Evento(i, false));
			} else if (emRajada[i] && cronometro[i] <= 0) {
				emRajada[i] = false;
				cronometro[i] = sortear(CALMA_MINIMA, CALMA_MAXIMA);
				brilho[i] = 1;
			}
			if (emRajada[i]) {
				troca[i] -= dt;
				if (troca[i] <= 0) {
					brilho[i] = estadoDaRajada();
					troca[i] = sortear(TROCA_MINIMA, TROCA_MAXIMA);
				}
			}
		}
		// a falha geral
		if (apagao > 0) {
			apagao -= dt;
		} else if (rajadaGeral > 0) {
			rajadaGeral -= dt;
			trocaGeral -= dt;
			if (trocaGeral <= 0) {
				brilhoGeral = estadoDaRajada();
				trocaGeral = sortear(TROCA_MINIMA, TROCA_MAXIMA);
			}
			if (rajadaGeral <= 0) {
				brilhoGeral = 1;
				if (random.nextFloat() < CHANCE_APAGAO) apagao = sortear(APAGAO_MINIMO, APAGAO_MAXIMO);
			}
		} else if (dentro) {
			cronometroGeral -= dt;
			if (cronometroGeral <= 0) {
				rajadaGeral = RAJADA_GERAL;
				trocaGeral = 0;
				cronometroGeral = sortear(GERAL_MINIMO, GERAL_MAXIMO);
				eventos.add(new Evento(-1, true));
			}
		}
		return eventos;
	}

	/** Numa rajada: na maior parte do tempo apagada ou quase, às vezes acesa, às vezes meia-luz. */
	private float estadoDaRajada() {
		float r = random.nextFloat();
		return r < 0.45f ? 0.03f : r < 0.75f ? 1f : 0.4f;
	}

	private float sortear(float minimo, float maximo) {
		return minimo + random.nextFloat() * (maximo - minimo);
	}

	/** O brilho da lâmpada agora: 0 (apagada) a 1. */
	public float brilho(int i) {
		if (!funciona[i] || apagao > 0) return 0;
		float b = brilho[i];
		if (rajadaGeral > 0) b = Math.min(b, brilhoGeral);
		return b;
	}

	/** Uma falha geral (ou o escuro depois dela) está acontecendo? */
	public boolean falhaGeral() {
		return rajadaGeral > 0 || apagao > 0;
	}

	/** Depuração: a falha geral começa agora. */
	public void forcarFalhaGeral() {
		cronometroGeral = 0;
	}

	public int quantidade() {
		return funciona.length;
	}
}
