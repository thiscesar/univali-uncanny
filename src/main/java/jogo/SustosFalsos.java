package jogo;

import java.util.Random;

/**
 * Sustos que não machucam: de vez em quando, na floresta, uma silhueta parada ao longe (que some
 * quando a lanterna a ilumina) ou um galho quebrando logo atrás do jogador. Raros de propósito:
 * se acontecem toda hora, o jogador aprende a ignorar.
 *
 * Esta classe só decide QUANDO (e qual); onde e como desenhar fica com a TelaJogo.
 */
public class SustosFalsos {

	public enum Tipo { SILHUETA, GALHO }

	/** Intervalo entre dois sustos, em segundos (sorteado nessa faixa). */
	public static final float INTERVALO_MINIMO = 50f, INTERVALO_MAXIMO = 110f;
	/** Antes do primeiro, o jogador fica esse tempo na floresta sem nada. */
	public static final float CARENCIA = 30f;
	/** Chance de o susto ser a silhueta (o resto é o galho). */
	public static final float CHANCE_SILHUETA = 0.45f;

	/** A silhueta: a que distância aparece, e quando some. */
	public static final float SILHUETA_PERTO = 14f, SILHUETA_LONGE = 20f;
	public static final float SILHUETA_SOME_PERTO = 9f, SILHUETA_DURACAO = 25f;
	/**
	 * O facho da lanterna tem luz cheia até 12 graus do centro e se apaga até 24 (Ambiente). A
	 * silhueta aparece nessa borda fraca (meio iluminada, de relance) e some quando entra na luz
	 * cheia, a menos de CONE_LANTERNA graus do centro. ALCANCE_LANTERNA: até onde a luz chega.
	 */
	public static final float CONE_LANTERNA = 11f, ALCANCE_LANTERNA = 30f;
	public static final float BORDA_MINIMA = 17f, BORDA_MAXIMA = 22f;

	private final Random random;
	private float cronometro = CARENCIA;

	public SustosFalsos(long semente) {
		random = new Random(semente);
	}

	/**
	 * Um passo do tempo. Só conta com o jogador na floresta e o monstro fora de cena (um susto
	 * falso no meio de um verdadeiro não assusta ninguém). Devolve o susto da vez, ou null.
	 */
	public Tipo atualizar(float dt, boolean naFloresta, boolean monstroEmCena) {
		if (!naFloresta || monstroEmCena) return null;
		cronometro -= dt;
		if (cronometro > 0) return null;
		cronometro = INTERVALO_MINIMO + random.nextFloat() * (INTERVALO_MAXIMO - INTERVALO_MINIMO);
		return random.nextFloat() < CHANCE_SILHUETA ? Tipo.SILHUETA : Tipo.GALHO;
	}

	/** Para sortear posições com a mesma semente. */
	public Random random() {
		return random;
	}

	/**
	 * A lanterna está iluminando o ponto (x, z)? Com ela ligada, o ponto dentro do cone em volta
	 * da direção da câmera (só no plano do chão) e dentro do alcance.
	 */
	public static boolean iluminado(boolean lanternaLigada, float camX, float camZ, float frenteX, float frenteZ,
			float x, float z) {
		if (!lanternaLigada) return false;
		float dx = x - camX, dz = z - camZ;
		float d = (float) Math.sqrt(dx * dx + dz * dz);
		float f = (float) Math.sqrt(frenteX * frenteX + frenteZ * frenteZ);
		if (d < 1e-3f || f < 1e-6f || d > ALCANCE_LANTERNA) return d < 1e-3f;
		float cos = (dx * frenteX + dz * frenteZ) / (d * f);
		return cos >= Math.cos(Math.toRadians(CONE_LANTERNA));
	}
}
