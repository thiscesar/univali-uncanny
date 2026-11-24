package jogo;

/**
 * Os primeiros segundos da partida: o jogador acorda com a cabeça na carteira. Só a linha do
 * tempo (testável); a TelaJogo aplica cada valor na câmera, no pós-processamento e no HUD.
 *
 *  0,0 s  tela preta, olhos fechados
 *  0,8 s  os olhos entreabrem, fecham, abrem mais, piscam e abrem de vez (por volta de 3,8 s)
 *  2,2 s  a cabeça começa a levantar da carteira (de 0,75 m mais baixo, olhando para a mesa e
 *         tombada para o lado) até a altura e o olhar normais, em 4,6 s
 *  até 4,5 s a imagem vai do borrado ao nítido
 *  4,6 s  o jogador já pode olhar em volta; 5,2 s, andar; 6 s, fim
 */
public class Despertar {

	public static final float DURACAO = 6f, OLHAR = 4.6f, ANDAR = 5.2f;
	/** Quanto mais baixo começam os olhos (a cabeça deitada na carteira), em metros. */
	public static final float ALTURA_DEITADO = 0.75f;
	/** Para onde a cabeça começa virada, em radianos: olhando para baixo e tombada para o lado. */
	public static final float ARFAGEM_DEITADO = -0.75f, ROLAGEM_DEITADO = 0.38f;

	/** As pálpebras: {tempo, abertura} — a abertura entre um ponto e outro é interpolada. */
	private static final float[][] PALPEBRAS = { { 0, 0 }, { 0.8f, 0 }, { 1.5f, 0.35f }, { 1.75f, 0.3f }, { 1.95f, 0 },
			{ 2.2f, 0 }, { 2.9f, 0.7f }, { 3.05f, 0.05f }, { 3.2f, 0.05f }, { 3.8f, 1 } };

	private float tempo = 0;

	public void atualizar(float dt) {
		tempo += dt;
	}

	/** Pula o resto da abertura. */
	public void pular() {
		tempo = Math.max(tempo, DURACAO);
	}

	public boolean terminou() {
		return tempo >= DURACAO;
	}

	public float tempo() {
		return tempo;
	}

	/** De 0 (olhos fechados) a 1 (abertos). */
	public float palpebras() {
		for (int i = 1; i < PALPEBRAS.length; i++) {
			if (tempo < PALPEBRAS[i][0]) {
				float[] a = PALPEBRAS[i - 1], b = PALPEBRAS[i];
				return a[1] + (b[1] - a[1]) * suave((tempo - a[0]) / (b[0] - a[0]));
			}
		}
		return 1;
	}

	/** De 1 (cabeça na carteira) a 0 (de pé, olhando para a frente). */
	public float deitado() {
		return 1 - suave((tempo - 2.2f) / (OLHAR - 2.2f));
	}

	/** De 1 (tudo borrado) a 0 (nítido). */
	public float desfoque() {
		return 1 - suave((tempo - 0.8f) / 3.7f);
	}

	public boolean podeOlhar() {
		return tempo >= OLHAR;
	}

	public boolean podeAndar() {
		return tempo >= ANDAR;
	}

	private static float suave(float x) {
		x = Math.max(0, Math.min(1, x));
		return x * x * (3 - 2 * x);
	}
}
