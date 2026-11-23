package render;

/**
 * A lanterna do jogador: liga e desliga, treme de leve e segue a câmera com um pequeno atraso.
 *
 * Ela não sai exatamente dos olhos: uma luz saindo do olho esconde todas as sombras (cada
 * sombra fica atrás do objeto que a projeta, do ponto de vista de quem olha) e deixa a cena
 * chapada. Por isso fica num "ponto de montagem" um pouco à direita e abaixo dos olhos,
 * apontando para um ponto à frente, no centro da tela. Mais tarde, esse ponto vira a ponta
 * da escopeta.
 *
 * Tudo em espaço da câmera (x para a direita, y para cima, a câmera olhando para -z).
 * Não usa OpenGL (dá para testar): quem envia os valores ao shader é o Ambiente.
 */
public class Lanterna {

	/** Onde a lanterna fica em relação aos olhos, em metros: à direita, abaixo e à frente. */
	public static final float[] MONTAGEM = { 0.25f, -0.3f, -0.3f };
	/** Para onde ela aponta: um ponto 20 m à frente, no centro da tela. */
	private static final float DISTANCIA_MIRA = 20f;
	/** Quanto maior, menor o atraso ao girar a câmera. */
	private static final float RIGIDEZ = 14f;
	public static final float INTENSIDADE = 1.6f;

	private boolean ligada = true;
	private float tempo = 0;

	/** Direção da luz no mundo (suavizada) e se ela já foi iniciada. */
	private final float[] direcaoMundo = new float[3];
	private boolean iniciada = false;

	private final float[] posicaoVista = MONTAGEM.clone();
	private final float[] direcaoVista = new float[3];

	public void alternar() {
		ligada = !ligada;
	}

	public boolean ligada() {
		return ligada;
	}

	/** Atualiza a direção (com atraso) e o tremor. dt em segundos. */
	public void atualizar(Camera camera, float dt) {
		tempo += dt;

		// direção desejada: do ponto de montagem até a mira, em espaço da câmera...
		float[] alvoVista = normalizar(new float[] { -MONTAGEM[0], -MONTAGEM[1], -DISTANCIA_MIRA - MONTAGEM[2] });
		// ...levada ao mundo (x pela direita, y por cima, -z pela frente da câmera)
		float[] alvoMundo = paraMundo(camera, alvoVista);

		if (!iniciada) {
			System.arraycopy(alvoMundo, 0, direcaoMundo, 0, 3);
			iniciada = true;
		}
		// a luz alcança a câmera aos poucos: ao girar rápido, ela fica um pouco para trás
		float fator = 1 - (float) Math.exp(-RIGIDEZ * dt);
		for (int i = 0; i < 3; i++) {
			direcaoMundo[i] += (alvoMundo[i] - direcaoMundo[i]) * fator;
		}
		float[] d = normalizar(direcaoMundo);
		System.arraycopy(d, 0, direcaoMundo, 0, 3);

		float[] v = paraVista(camera, direcaoMundo);
		System.arraycopy(v, 0, direcaoVista, 0, 3);
	}

	/** Intensidade agora: 0 desligada; ligada, com um tremor sutil (duas ondas fora de compasso). */
	public float intensidade() {
		if (!ligada) {
			return 0;
		}
		float tremor = (float) (Math.sin(tempo * 13.1) * Math.sin(tempo * 7.7 + 1.3));
		return INTENSIDADE * (0.97f + 0.03f * tremor);
	}

	public float[] posicaoVista() {
		return posicaoVista;
	}

	public float[] direcaoVista() {
		return direcaoVista;
	}

	/** Copia os valores da lanterna para o Ambiente, que os envia aos shaders. */
	public void aplicar(Ambiente ambiente) {
		ambiente.intensidadeLanterna = intensidade();
		System.arraycopy(posicaoVista, 0, ambiente.posLanterna, 0, 3);
		System.arraycopy(direcaoVista, 0, ambiente.dirLanterna, 0, 3);
	}

	static float[] paraMundo(Camera c, float[] v) {
		return new float[] {
				c.direita.x * v[0] + c.cima.x * v[1] - c.frente.x * v[2],
				c.direita.y * v[0] + c.cima.y * v[1] - c.frente.y * v[2],
				c.direita.z * v[0] + c.cima.z * v[1] - c.frente.z * v[2] };
	}

	static float[] paraVista(Camera c, float[] m) {
		return new float[] {
				c.direita.x * m[0] + c.direita.y * m[1] + c.direita.z * m[2],
				c.cima.x * m[0] + c.cima.y * m[1] + c.cima.z * m[2],
				-(c.frente.x * m[0] + c.frente.y * m[1] + c.frente.z * m[2]) };
	}

	private static float[] normalizar(float[] v) {
		float t = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
		return new float[] { v[0] / t, v[1] / t, v[2] / t };
	}
}
