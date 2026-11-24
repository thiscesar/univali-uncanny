package jogo;

import java.util.ArrayList;
import java.util.List;

/**
 * Portas de sala que abrem sozinhas. Começam fechadas (bloqueando o vão) e, num momento, abrem:
 * primeiro entreabrem um pouco, param um instante e então escancaram, como se alguém tivesse
 * empurrado. A primeira é a da sala onde o jogador acorda: abre alguns segundos depois do começo
 * (ou antes, se ele chegar perto dela); as outras abrem quando ele passa perto, no corredor.
 * Só dados (posições no modelo do prédio): a TelaJogo tira a colisão e toca o som; o
 * DesenhoPredio gira cada porta pelo ângulo daqui.
 */
public class PortasSozinhas {

	/** Quanto a porta abre (graus) e quanto tempo leva ao todo, em segundos. */
	public static final float ABERTURA = 100f, DURACAO = 2.2f;
	/** A porta do início: abre a partir deste tempo de jogo, ou antes, com o jogador a esta distância. */
	public static final float TEMPO_DO_INICIO = 7.5f, PERTO_DO_INICIO = 3f; // 1,5 s depois de ele acordar
	/** As outras abrem com o jogador a esta distância. */
	public static final float PERTO = 7f;

	private final List<float[]> centros;
	/** Segundos desde que cada porta começou a abrir (-1 = fechada). */
	private final float[] abrindo;

	/** centros: o meio do vão de cada porta, {x, z} no modelo; a primeira é a do início. */
	public PortasSozinhas(List<float[]> centros) {
		this.centros = centros;
		abrindo = new float[centros.size()];
		java.util.Arrays.fill(abrindo, -1);
	}

	/**
	 * Um passo do tempo. tempoDeJogo: segundos desde o começo da partida; (jx, jz): o jogador, no
	 * modelo. Devolve as portas que começaram a abrir agora (para o som e para tirar a colisão).
	 */
	public List<Integer> atualizar(float dt, float tempoDeJogo, float jx, float jz) {
		List<Integer> comecaram = new ArrayList<>();
		for (int i = 0; i < abrindo.length; i++) {
			if (abrindo[i] >= 0) {
				abrindo[i] += dt;
				continue;
			}
			float[] c = centros.get(i);
			float d = (float) Math.hypot(c[0] - jx, c[1] - jz);
			boolean abre = i == 0 ? tempoDeJogo >= TEMPO_DO_INICIO || (tempoDeJogo >= 1.5f && d < PERTO_DO_INICIO) : d < PERTO;
			if (abre) {
				abrindo[i] = 0;
				comecaram.add(i);
			}
		}
		return comecaram;
	}

	/** O ângulo da porta agora, em graus: entreabre até 12° em 0,5 s, para meio segundo e escancara. */
	public float angulo(int i) {
		float t = abrindo[i];
		if (t < 0) return 0;
		if (t < 0.5f) return 12 * suave(t / 0.5f);
		if (t < 1.0f) return 12;
		return 12 + (ABERTURA - 12) * suave(Math.min(1, (t - 1.0f) / (DURACAO - 1.0f)));
	}

	public boolean fechada(int i) {
		return abrindo[i] < 0;
	}

	public int quantidade() {
		return abrindo.length;
	}

	private static float suave(float x) {
		return x * x * (3 - 2 * x);
	}
}
