package render;

/**
 * Uma mola amortecida em uma dimensão: a posição é puxada para o alvo (rigidez) e a
 * velocidade é freada (amortecimento). Dá movimentos que "chutam e voltam" sem programar
 * cada curva: o recuo da arma, o atraso ao girar, o abaixar ao correr.
 *
 *   aceleração = rigidez * (alvo - posição) - amortecimento * velocidade
 *
 * Com amortecimento perto de 2 * raiz(rigidez) a mola volta sem oscilar; com menos, ela
 * passa do alvo e balança um pouco antes de parar. Não usa OpenGL (dá para testar).
 */
public class Mola {

	public float posicao = 0, velocidade = 0, alvo = 0;
	private final float rigidez, amortecimento;

	public Mola(float rigidez, float amortecimento) {
		this.rigidez = rigidez;
		this.amortecimento = amortecimento;
	}

	/** Um tranco: muda a velocidade de uma vez (o tiro chutando a arma). */
	public void empurrar(float impulso) {
		velocidade += impulso;
	}

	/** dt em segundos. Passos de no máximo 5 ms, para a mola não "explodir" num quadro lento. */
	public void atualizar(float dt) {
		while (dt > 0) {
			float passo = Math.min(dt, 0.005f);
			float aceleracao = rigidez * (alvo - posicao) - amortecimento * velocidade;
			velocidade += aceleracao * passo; // Euler semi-implícito: velocidade primeiro, depois posição
			posicao += velocidade * passo;
			dt -= passo;
		}
	}
}
