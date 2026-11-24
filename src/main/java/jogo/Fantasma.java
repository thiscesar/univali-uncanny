package jogo;

/**
 * Um fantasma de passagem: aparece aos poucos, flutua em linha reta devagar (atravessando
 * árvores e paredes) e some aos poucos no fim do caminho, ou antes, se o jogador chegar perto
 * demais. Não machuca: é só para ser visto. Só dados (a TelaJogo desenha com o alfa daqui).
 */
public class Fantasma {

	/** Segundos para aparecer e para sumir. */
	public static final float ENTRADA = 1.0f, SAIDA = 1.2f;
	/** Mais perto que isso, ele some. */
	public static final float SUMIR_PERTO = 7f;

	public float x, z;
	private final float direcaoX, direcaoZ, velocidade, duracao;
	private float tempo = 0;
	/** Tempo desde que começou a sumir (-1 = ainda não). */
	private float sumindo = -1;

	/** Começa em (x, z), andando na direção (direcaoX, direcaoZ) a "velocidade" m/s por "duracao" segundos. */
	public Fantasma(float x, float z, float direcaoX, float direcaoZ, float velocidade, float duracao) {
		this.x = x;
		this.z = z;
		float t = (float) Math.sqrt(direcaoX * direcaoX + direcaoZ * direcaoZ);
		this.direcaoX = t > 1e-6f ? direcaoX / t : 1;
		this.direcaoZ = t > 1e-6f ? direcaoZ / t : 0;
		this.velocidade = velocidade;
		this.duracao = duracao;
	}

	public void atualizar(float dt, float jogadorX, float jogadorZ) {
		tempo += dt;
		x += direcaoX * velocidade * dt;
		z += direcaoZ * velocidade * dt;
		float dx = x - jogadorX, dz = z - jogadorZ;
		if (sumindo < 0 && (dx * dx + dz * dz < SUMIR_PERTO * SUMIR_PERTO || tempo >= duracao - SAIDA)) {
			sumindo = 0;
		}
		if (sumindo >= 0) sumindo += dt;
	}

	/** Some agora (ex.: o monstro de verdade apareceu). */
	public void sumir() {
		if (sumindo < 0) sumindo = 0;
	}

	/** De 0 a 1: aparece em ENTRADA segundos, some em SAIDA. */
	public float alfa() {
		float entrando = Math.min(1, tempo / ENTRADA);
		float saindo = sumindo < 0 ? 1 : Math.max(0, 1 - sumindo / SAIDA);
		return Math.max(0, Math.min(entrando, saindo));
	}

	public boolean acabou() {
		return sumindo >= SAIDA;
	}

	/** Segundos desde que apareceu (para o balanço de flutuar). */
	public float tempo() {
		return tempo;
	}

	/** Para onde ele vai (unitário). */
	public float direcaoX() {
		return direcaoX;
	}

	public float direcaoZ() {
		return direcaoZ;
	}
}
