package audio;

import java.util.Random;

/**
 * Gera sons simples por código, em PCM de 16 bits mono, sem precisar de arquivo.
 * Usado para sons de teste e substitutos.
 */
public final class Sintetizador {

	private Sintetizador() {}

	/**
	 * Um "ping" curto: duas senoides (nota e oitava) com um chiado no ataque, que some
	 * aos poucos, seguido de silêncio até completar o período. Tocado em loop, vira um
	 * bipe repetido. O chiado ajuda o ouvido a localizar a direção do som.
	 *
	 * @param taxa        amostras por segundo (ex.: 44100)
	 * @param frequencia  nota em Hz (ex.: 880)
	 * @param duracaoMs   duração do ping
	 * @param periodoMs   duração total, com o silêncio depois do ping
	 */
	public static short[] ping(int taxa, double frequencia, int duracaoMs, int periodoMs) {
		int total = taxa * periodoMs / 1000;
		int som = Math.min(total, taxa * duracaoMs / 1000);
		short[] pcm = new short[total];
		Random ruido = new Random(1);
		for (int i = 0; i < som; i++) {
			double t = (double) i / taxa;
			double envelope = Math.exp(-t * 25); // decai rápido, como uma batida
			double chiado = (ruido.nextDouble() * 2 - 1) * Math.exp(-t * 120);
			double onda = 0.6 * Math.sin(2 * Math.PI * frequencia * t)
					+ 0.25 * Math.sin(2 * Math.PI * frequencia * 2 * t)
					+ 0.35 * chiado;
			pcm[i] = (short) Math.round(Math.max(-1, Math.min(1, onda * envelope)) * 28000);
		}
		return pcm;
	}

	/**
	 * Um rugido: onda dente-de-serra grave, com a nota escorregando de "inicio" para "fim" Hz,
	 * tremida e misturada com ruído (o ruído é o que deixa o som áspero e fácil de localizar).
	 * Sobe rápido e some aos poucos. Substituto do grito, da morte e do susto do monstro.
	 */
	public static short[] rugido(int taxa, int duracaoMs, double inicio, double fim, double aspereza, long semente) {
		int total = taxa * duracaoMs / 1000;
		short[] pcm = new short[total];
		Random ruido = new Random(semente);
		double fase = 0, ruidoFiltrado = 0;
		for (int i = 0; i < total; i++) {
			double t = (double) i / total;
			double tremor = 1 + 0.06 * Math.sin(2 * Math.PI * 7.5 * i / taxa); // vibração da garganta
			double frequencia = (inicio + (fim - inicio) * t) * tremor;
			fase += frequencia / taxa;
			double serra = 2 * (fase - Math.floor(fase)) - 1;
			ruidoFiltrado += ((ruido.nextDouble() * 2 - 1) - ruidoFiltrado) * 0.25; // ruído menos chiado
			double envelope = Math.min(1, t * 12) * Math.pow(1 - t, 1.5);
			double onda = (serra * (1 - aspereza) + ruidoFiltrado * 2.2 * aspereza) * envelope;
			pcm[i] = (short) Math.round(Math.max(-1, Math.min(1, onda)) * 30000);
		}
		return pcm;
	}

	/** Uma batida de coração ("tum-tum"): dois baques graves curtos, o segundo mais fraco. */
	public static short[] batimento(int taxa) {
		int total = taxa * 450 / 1000;
		short[] pcm = new short[total];
		for (int i = 0; i < total; i++) {
			double t = (double) i / taxa;
			double onda = baque(t, 0, 1.0) + baque(t, 0.17, 0.7);
			pcm[i] = (short) Math.round(Math.max(-1, Math.min(1, onda)) * 30000);
		}
		return pcm;
	}

	/** Uma pancada só, grave e seca, com um estalo no começo (um carimbo batendo na mesa). */
	public static short[] pancada(int taxa) {
		int total = taxa * 600 / 1000;
		short[] pcm = new short[total];
		Random ruido = new Random(11);
		for (int i = 0; i < total; i++) {
			double t = (double) i / taxa;
			double estalo = (ruido.nextDouble() * 2 - 1) * Math.exp(-t * 90) * 0.6;
			double onda = baque(t, 0, 1.0) * 1.2 + estalo;
			pcm[i] = (short) Math.round(Math.max(-1, Math.min(1, onda)) * 30000);
		}
		return pcm;
	}

	/** Um baque grave: senoide que cai de 80 para 40 Hz e some em ~0,12 s. */
	private static double baque(double t, double inicio, double volume) {
		double s = t - inicio;
		if (s < 0) return 0;
		double frequencia = 40 + 40 * Math.exp(-s * 30);
		return volume * Math.sin(2 * Math.PI * frequencia * s) * Math.exp(-s * 28) * Math.min(1, s * 400);
	}
}
