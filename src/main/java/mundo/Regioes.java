package mundo;

import terreno.RuidoFractal;

/**
 * As regiões da floresta, para o jogador reconhecer onde está ("a prova era perto da mata
 * morta"). Quatro mapas de ruído fractal, um por região, disputam cada ponto: vence o maior.
 * Isso forma manchas de 80 a 150 m, com bordas irregulares.
 *
 *  - PINHEIRAL: pinheiros e abetos, bem fechado;
 *  - MATA_MORTA: árvores secas, mais espaçadas, chão seco e acinzentado;
 *  - BOSQUE: árvores de copa redonda;
 *  - PEDREGAL: aberto, com muitas pedras e pedras grandes.
 * Só dados; a mesma seed dá as mesmas regiões.
 */
public class Regioes {

	public enum Tipo { PINHEIRAL, MATA_MORTA, BOSQUE, PEDREGAL }

	/** Tamanho das manchas: o ruído é lido com a posição dividida por isto (metros). */
	private static final double ESCALA = 85;
	/** Quanto a transição entre regiões é suave (menor = borda mais dura). */
	private static final float SUAVIDADE = 0.06f;

	private final RuidoFractal[] ruidos = new RuidoFractal[Tipo.values().length];

	public Regioes(long seed) {
		for (int i = 0; i < ruidos.length; i++) {
			ruidos[i] = new RuidoFractal(seed * 31 + 200 + i);
		}
	}

	private float valor(int i, float x, float z) {
		// o pinheiral e o bosque um pouco mais comuns; o pedregal, mais raro
		float vies = switch (Tipo.values()[i]) {
		case PINHEIRAL -> 0.06f;
		case BOSQUE -> 0.02f;
		case MATA_MORTA -> 0f;
		case PEDREGAL -> -0.01f;
		};
		return (float) ruidos[i].fbm(x / ESCALA, z / ESCALA, 3) + vies;
	}

	/** A região que vence no ponto. */
	public Tipo em(float x, float z) {
		int melhor = 0;
		float maior = -Float.MAX_VALUE;
		for (int i = 0; i < ruidos.length; i++) {
			float v = valor(i, x, z);
			if (v > maior) {
				maior = v;
				melhor = i;
			}
		}
		return Tipo.values()[melhor];
	}

	/**
	 * O "peso" de cada região no ponto, de 0 a 1, somando 1 (softmax): 1 no meio da região e
	 * metade-metade na borda entre duas. O chão usa isto para trocar de cor sem uma linha dura.
	 */
	public float[] pesos(float x, float z) {
		float[] p = new float[ruidos.length];
		float maior = -Float.MAX_VALUE, soma = 0;
		for (int i = 0; i < p.length; i++) {
			p[i] = valor(i, x, z);
			maior = Math.max(maior, p[i]);
		}
		for (int i = 0; i < p.length; i++) {
			p[i] = (float) Math.exp((p[i] - maior) / SUAVIDADE);
			soma += p[i];
		}
		for (int i = 0; i < p.length; i++) p[i] /= soma;
		return p;
	}
}
