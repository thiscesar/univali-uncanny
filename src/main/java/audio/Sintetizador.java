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
}
