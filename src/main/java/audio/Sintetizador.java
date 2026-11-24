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

	/**
	 * Um tiro de escopeta: um estouro de ruído que some muito rápido, um "bum" grave por baixo
	 * e uma cauda de ruído abafado (o eco na floresta) que dura quase um segundo.
	 */
	public static short[] tiro(int taxa) {
		int total = taxa * 1100 / 1000;
		short[] pcm = new short[total];
		Random ruido = new Random(21);
		double abafado = 0;
		for (int i = 0; i < total; i++) {
			double t = (double) i / taxa;
			double branco = ruido.nextDouble() * 2 - 1;
			abafado += (branco - abafado) * 0.08; // ruído filtrado: grave, sem chiado
			double estouro = branco * Math.exp(-t * 45);
			double bum = Math.sin(2 * Math.PI * (55 + 60 * Math.exp(-t * 18)) * t) * Math.exp(-t * 9);
			double eco = abafado * 3.5 * Math.exp(-t * 4.5) * Math.min(1, t * 60);
			double onda = estouro * 0.9 + bum * 0.8 + eco * 0.6;
			pcm[i] = (short) Math.round(Math.max(-1, Math.min(1, onda)) * 31000);
		}
		return pcm;
	}

	/**
	 * Estalos metálicos curtos (ruído agudo com um "plim" de 2 kHz), nos instantes dados em
	 * milissegundos e com o volume de cada um: o engatilhar (dois estalos), o cartucho entrando
	 * e o clique seco da arma vazia.
	 */
	public static short[] estalos(int taxa, int[] instantesMs, double[] volumes) {
		int ultimo = 0;
		for (int ms : instantesMs) ultimo = Math.max(ultimo, ms);
		int total = taxa * (ultimo + 120) / 1000;
		double[] soma = new double[total];
		Random ruido = new Random(31);
		for (int k = 0; k < instantesMs.length; k++) {
			int inicio = taxa * instantesMs[k] / 1000;
			double anterior = 0;
			for (int i = 0; inicio + i < total && i < taxa / 10; i++) {
				double t = (double) i / taxa;
				double branco = ruido.nextDouble() * 2 - 1;
				double agudo = branco - anterior; // a diferença entre amostras realça o agudo
				anterior = branco;
				double plim = Math.sin(2 * Math.PI * 2100 * t) * 0.5;
				soma[inicio + i] += (agudo * 0.7 + plim) * Math.exp(-t * 90) * volumes[k];
			}
		}
		short[] pcm = new short[total];
		for (int i = 0; i < total; i++) {
			pcm[i] = (short) Math.round(Math.max(-1, Math.min(1, soma[i])) * 28000);
		}
		return pcm;
	}

	/**
	 * Um sussurro: ruído "soprado" (agudo, sem tom), em sílabas que crescem e somem em ritmo
	 * irregular, com pausas. Em loop, parece alguém murmurando sem parar. Bom para localizar
	 * a direção (o ouvido se guia pelo agudo).
	 */
	public static short[] sussurro(int taxa, long semente) {
		int total = taxa * 4;
		short[] pcm = new short[total];
		Random r = new Random(semente);
		// sílabas: início (s) e duração, com pausas entre elas
		double[] inicio = new double[12], duracao = new double[12];
		double t0 = 0.1;
		for (int k = 0; k < inicio.length; k++) {
			inicio[k] = t0;
			duracao[k] = 0.12 + r.nextDouble() * 0.25;
			t0 += duracao[k] + 0.04 + r.nextDouble() * (k % 4 == 3 ? 0.6 : 0.12);
		}
		double anterior = 0, grave = 0;
		for (int i = 0; i < total; i++) {
			double t = (double) i / taxa;
			double branco = r.nextDouble() * 2 - 1;
			double agudo = branco - anterior * 0.6; // tira o grave: soa como "ssss" e "hhhh"
			anterior = branco;
			grave += (branco - grave) * 0.2;
			double envelope = 0;
			for (int k = 0; k < inicio.length; k++) {
				double s = (t - inicio[k]) / duracao[k];
				if (s > 0 && s < 1) envelope = Math.max(envelope, Math.sin(Math.PI * s));
			}
			double onda = (agudo * 0.55 + grave * 0.8) * envelope;
			pcm[i] = (short) Math.round(Math.max(-1, Math.min(1, onda)) * 26000);
		}
		return pcm;
	}

	/**
	 * O sinal de fim de aula: uma campainha elétrica (o martelinho batendo no sino muito rápido),
	 * ~2,5 s, com a nota e as harmônicas de um sino e um tremolo de 18 Hz.
	 */
	public static short[] sinal(int taxa) {
		int total = taxa * 2800 / 1000;
		short[] pcm = new short[total];
		for (int i = 0; i < total; i++) {
			double t = (double) i / taxa;
			double sino = Math.sin(2 * Math.PI * 950 * t) + 0.5 * Math.sin(2 * Math.PI * 2380 * t)
					+ 0.3 * Math.sin(2 * Math.PI * 3720 * t);
			double martelo = 0.6 + 0.4 * Math.abs(Math.sin(Math.PI * 18 * t));
			double envelope = Math.min(1, t * 30) * (t < 2.5 ? 1 : Math.max(0, 1 - (t - 2.5) / 0.3));
			pcm[i] = (short) Math.round(Math.max(-1, Math.min(1, sino * 0.45 * martelo * envelope)) * 28000);
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
