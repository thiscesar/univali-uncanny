package entidades;

/** Algo que os bagos da escopeta podem acertar (os monstros). */
public interface Atingivel {

	/** Distância ao longo do raio (direção unitária) até a entrada no corpo, ou -1 se não acerta. */
	float intersectaRaio(float[] origem, float[] direcao);

	/** Leva o dano dos bagos que acertaram neste tiro. */
	void receberDano(float valor);
}
