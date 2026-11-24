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
	 * irregular, com pausas: alguém murmurando (o jogo toca uma vez, de tempos em tempos). Bom para localizar
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

	/**
	 * O zumbido de lâmpada fluorescente velha: 120 Hz com harmônicas (o reator) e um chiado fraco.
	 * 2 s, para tocar em loop. (Tinha estalos a cada 1/3 de segundo: em loop, viravam um ritmo.)
	 */
	public static short[] zumbido(int taxa) {
		int total = taxa * 2;
		short[] pcm = new short[total];
		Random r = new Random(71);
		for (int i = 0; i < total; i++) {
			double t = (double) i / taxa;
			double onda = 0.5 * Math.sin(2 * Math.PI * 120 * t) + 0.25 * Math.sin(2 * Math.PI * 240 * t)
					+ 0.12 * Math.sin(2 * Math.PI * 360 * t) + 0.05 * (r.nextDouble() * 2 - 1);
			pcm[i] = (short) Math.round(Math.max(-1, Math.min(1, onda * 0.5)) * 30000);
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
	// ---------- voz ----------

	/** Formantes (F1, F2 em Hz) das vogais, numa voz masculina: o que faz um "a" soar como "a". */
	private static final double[][] FORMANTES = { { 730, 1090 }, { 530, 1840 }, { 270, 2290 }, { 570, 840 }, { 300, 870 } };

	/**
	 * Uma voz que fala sem dizer nada (como nos jogos antigos): cada vogal do texto vira uma
	 * sílaba com o timbre daquela vogal, cada consoante um sopro ou estalo curto. A fonte é um
	 * zumbido de cordas vocais (dente de serra num tom grave que oscila um pouco) passando por dois
	 * filtros ressonantes nas frequências da vogal (síntese por formantes). Pergunta termina
	 * subindo; o resto vai caindo de leve, como uma frase. Dá para entender que é fala, não as palavras.
	 *
	 * @param tom frequência média da voz em Hz (ex.: 110 para um professor de voz grave)
	 */
	public static short[] voz(int taxa, String texto, double tom, long semente) {
		Random r = new Random(semente ^ texto.hashCode());
		String t = texto.toLowerCase();
		boolean pergunta = t.trim().endsWith("?");
		// as sílabas: {vogal (0 a 4), consoante antes (0 nenhuma, 1 sopro, 2 estalo, 3 sonora), pausa depois em ms}
		java.util.List<int[]> silabas = new java.util.ArrayList<>();
		int consoante = 0;
		for (int k = 0; k < t.length(); k++) {
			char c = t.charAt(k);
			int v = vogal(c);
			if (v >= 0) {
				if (!silabas.isEmpty() && consoante == 0 && vogal(t.charAt(k - 1)) >= 0) continue; // ditongo: uma sílaba só
				silabas.add(new int[] { v, consoante, 0 });
				consoante = 0;
			} else if ("sfxzçjh".indexOf(c) >= 0) {
				consoante = 1;
			} else if ("ptkcqbdg".indexOf(c) >= 0) {
				consoante = 2;
			} else if (Character.isLetter(c)) {
				consoante = 3;
			} else if (!silabas.isEmpty()) {
				int[] ultima = silabas.get(silabas.size() - 1);
				ultima[2] = Math.max(ultima[2], ".,!?:;".indexOf(c) >= 0 ? 220 : c == ' ' ? 45 : 0);
			}
		}
		if (silabas.isEmpty()) return new short[taxa / 10];

		// a duração de cada sílaba (com um pouco de sorte) e o total
		int[] duracao = new int[silabas.size()];
		int total = 0;
		for (int k = 0; k < duracao.length; k++) {
			duracao[k] = (int) ((0.10 + r.nextDouble() * 0.06) * taxa);
			total += duracao[k] + silabas.get(k)[2] * taxa / 1000;
		}
		double[] s = new double[total + taxa / 10];
		int pos = 0;
		double fase = 0;
		double[] f1 = filtro(FORMANTES[silabas.get(0)[0]][0], 6, taxa), f2 = filtro(FORMANTES[silabas.get(0)[0]][1], 9, taxa);
		double[] e1 = new double[4], e2 = new double[4]; // memória dos dois filtros
		double atualF1 = FORMANTES[silabas.get(0)[0]][0], atualF2 = FORMANTES[silabas.get(0)[0]][1];
		for (int k = 0; k < silabas.size(); k++) {
			int[] sil = silabas.get(k);
			double alvoF1 = FORMANTES[sil[0]][0] * (0.95 + r.nextDouble() * 0.1), alvoF2 = FORMANTES[sil[0]][1] * (0.95 + r.nextDouble() * 0.1);
			double progresso = (double) k / Math.max(1, silabas.size() - 1);
			// entonação: cai ao longo da frase; numa pergunta, sobe no fim
			double tomSilaba = tom * (1.08 - 0.15 * progresso + (pergunta && progresso > 0.7 ? 0.35 * (progresso - 0.7) / 0.3 : 0))
					* (0.97 + r.nextDouble() * 0.06);
			int n = duracao[k];
			for (int i = 0; i < n; i++) {
				if (i % 64 == 0) { // os formantes deslizam de uma vogal para a outra
					atualF1 += (alvoF1 - atualF1) * 0.25;
					atualF2 += (alvoF2 - atualF2) * 0.25;
					f1 = filtro(atualF1, 6, taxa);
					f2 = filtro(atualF2, 9, taxa);
				}
				double tt = (double) i / taxa;
				double vibrato = 1 + 0.015 * Math.sin(2 * Math.PI * 5.5 * (pos + i) / taxa);
				fase += tomSilaba * vibrato / taxa;
				double fonte = 2 * (fase - Math.floor(fase)) - 1 + (r.nextDouble() * 2 - 1) * 0.08; // cordas vocais + ar
				double y = aplicar(f1, e1, fonte) + 0.6 * aplicar(f2, e2, fonte);
				double env = Math.min(1, tt / 0.015) * Math.min(1, (n - i) / (0.03 * taxa));
				// a consoante: nos primeiros 30 ms, um sopro (chiado) ou um estalo (ruído curtinho)
				double ruido = 0;
				if (sil[1] == 1 && tt < 0.05) ruido = (r.nextDouble() * 2 - 1) * 0.35 * (1 - tt / 0.05);
				if (sil[1] == 2 && tt < 0.012) ruido = (r.nextDouble() * 2 - 1) * 0.6;
				double abre = sil[1] == 3 ? Math.min(1, tt / 0.04) : 1; // m, n, l, r: a vogal entra devagar
				s[pos + i] += y * env * abre + ruido;
			}
			pos += n + sil[2] * taxa / 1000;
		}
		return normalizar(s, 0.75);
	}

	private static int vogal(char c) {
		return switch (c) {
		case 'a', 'á', 'à', 'â', 'ã' -> 0;
		case 'e', 'é', 'ê' -> 1;
		case 'i', 'í', 'y' -> 2;
		case 'o', 'ó', 'ô', 'õ' -> 3;
		case 'u', 'ú' -> 4;
		default -> -1;
		};
	}

	/** Coeficientes de um filtro passa-faixa ressonante (biquad, do "Audio EQ Cookbook"): {b0, b2, a1, a2}. */
	private static double[] filtro(double frequencia, double q, int taxa) {
		double w = 2 * Math.PI * frequencia / taxa, alfa = Math.sin(w) / (2 * q), a0 = 1 + alfa;
		return new double[] { alfa / a0, -alfa / a0, -2 * Math.cos(w) / a0, (1 - alfa) / a0 };
	}

	/** Um passo do filtro; m guarda {x1, x2, y1, y2}. */
	private static double aplicar(double[] f, double[] m, double x) {
		double y = f[0] * x + f[1] * m[1] - f[2] * m[2] - f[3] * m[3];
		m[1] = m[0];
		m[0] = x;
		m[3] = m[2];
		m[2] = y;
		return y;
	}

	// ---------- passos e galho ----------

	/** Passo em folhas secas e grama: vários estalinhos agudos espalhados em 0,1 s e um baque abafado. */
	public static short[] passoFolhas(int taxa, long semente) {
		Random r = new Random(semente);
		double[] s = new double[taxa * 180 / 1000];
		for (int k = 0; k < 9; k++) {
			int inicio = (int) (r.nextDouble() * taxa * 0.11);
			double volume = 0.3 + r.nextDouble() * 0.5, anterior = 0;
			for (int i = 0; inicio + i < s.length && i < taxa / 40; i++) {
				double branco = r.nextDouble() * 2 - 1;
				s[inicio + i] += (branco - anterior) * volume * Math.exp(-i / (double) taxa * 350);
				anterior = branco;
			}
		}
		baque(s, taxa, 80, 0.12, 60);
		return normalizar(s, 0.7);
	}

	/** Passo em terra e lama: ruído grave (filtrado) e um baque curto. */
	public static short[] passoTerra(int taxa, long semente) {
		Random r = new Random(semente);
		double[] s = new double[taxa * 140 / 1000];
		double grave = 0;
		for (int i = 0; i < s.length; i++) {
			double t = (double) i / taxa;
			grave += 0.08 * ((r.nextDouble() * 2 - 1) - grave); // filtro passa-baixa de um polo
			s[i] = grave * 3 * Math.exp(-t * 32);
		}
		baque(s, taxa, 70, 0.2, 55);
		return normalizar(s, 0.75);
	}

	/** Passo na água rasa: um chape (ruído médio que cresce e some) e bolhas que sobem de tom. */
	public static short[] passoAgua(int taxa, long semente) {
		Random r = new Random(semente);
		double[] s = new double[taxa * 320 / 1000];
		double a = 0, b = 0;
		for (int i = 0; i < s.length; i++) {
			double t = (double) i / taxa;
			double branco = r.nextDouble() * 2 - 1;
			a += 0.35 * (branco - a);
			b += 0.04 * (branco - b);
			double envelope = Math.min(1, t / 0.012) * Math.exp(-t * 11);
			s[i] = (a - b) * 1.6 * envelope; // a diferença de dois passa-baixas: só os médios
		}
		for (int k = 0; k < 4; k++) { // bolhas: senoides curtas subindo de tom
			int inicio = (int) ((0.03 + r.nextDouble() * 0.15) * taxa);
			double f0 = 350 + r.nextDouble() * 300, fase = 0;
			for (int i = 0; inicio + i < s.length && i < taxa * 35 / 1000; i++) {
				double t = (double) i / taxa;
				fase += 2 * Math.PI * f0 * (1 + t * 25) / taxa;
				s[inicio + i] += Math.sin(fase) * 0.25 * Math.sin(Math.PI * t / 0.035);
			}
		}
		return normalizar(s, 0.7);
	}

	/** Passo no piso da Univali: o salto batendo (estalo agudo com um "tom" de 1,4 kHz) e o eco do corredor. */
	public static short[] passoPiso(int taxa, long semente) {
		Random r = new Random(semente);
		double[] seco = new double[taxa * 90 / 1000];
		double anterior = 0, tom = 1300 + r.nextDouble() * 250;
		for (int i = 0; i < seco.length; i++) {
			double t = (double) i / taxa;
			double branco = r.nextDouble() * 2 - 1;
			seco[i] = (branco - anterior) * 0.6 * Math.exp(-t * 260) + Math.sin(2 * Math.PI * tom * t) * 0.25 * Math.exp(-t * 70);
			anterior = branco;
		}
		baque(seco, taxa, 110, 0.15, 70);
		double[] s = new double[taxa * 330 / 1000];
		int[] atrasos = { 0, taxa * 75 / 1000, taxa * 160 / 1000 };
		double[] volumes = { 1, 0.28, 0.1 };
		for (int e = 0; e < atrasos.length; e++) {
			for (int i = 0; i < seco.length && atrasos[e] + i < s.length; i++) s[atrasos[e] + i] += seco[i] * volumes[e];
		}
		return normalizar(s, 0.65);
	}

	/**
	 * Um galho quebrando: um estalo forte (ruído com ataque seco), depois dois a quatro estalos
	 * menores (as fibras se partindo) e um farfalhar de folhas.
	 */
	public static short[] galho(int taxa, long semente) {
		Random r = new Random(semente);
		double[] s = new double[taxa * 450 / 1000];
		int estalos = 3 + r.nextInt(3);
		int inicio = 0;
		for (int k = 0; k < estalos; k++) {
			double volume = k == 0 ? 1 : 0.25 + r.nextDouble() * 0.35, anterior = 0;
			double decaimento = k == 0 ? 70 : 140;
			for (int i = 0; inicio + i < s.length && i < taxa / 12; i++) {
				double t = (double) i / taxa;
				double branco = r.nextDouble() * 2 - 1;
				s[inicio + i] += (branco * 0.6 + (branco - anterior) * 0.6) * volume * Math.exp(-t * decaimento);
				anterior = branco;
			}
			inicio += (int) ((0.03 + r.nextDouble() * 0.06) * taxa);
		}
		double farfalhar = 0;
		for (int i = 0; i < s.length; i++) {
			double t = (double) i / taxa;
			farfalhar += 0.5 * ((r.nextDouble() * 2 - 1) - farfalhar);
			s[i] += farfalhar * 0.12 * Math.sin(Math.PI * Math.min(1, t / 0.45));
		}
		return normalizar(s, 0.85);
	}

	/** Soma um baque grave (senoide que cai de tom) no começo do som. */
	private static void baque(double[] s, int taxa, double frequencia, double volume, double decaimento) {
		double fase = 0;
		for (int i = 0; i < s.length; i++) {
			double t = (double) i / taxa;
			fase += 2 * Math.PI * frequencia * (1 - 0.4 * Math.min(1, t * 10)) / taxa;
			s[i] += Math.sin(fase) * volume * Math.exp(-t * decaimento);
		}
	}

	/** Ajusta o pico para "pico" (de 0 a 1) e converte para 16 bits. */
	private static short[] normalizar(double[] s, double pico) {
		double maior = 1e-9;
		for (double v : s) maior = Math.max(maior, Math.abs(v));
		short[] pcm = new short[s.length];
		for (int i = 0; i < s.length; i++) pcm[i] = (short) Math.round(s[i] / maior * pico * 32000);
		return pcm;
	}
}
