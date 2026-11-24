package jogo;

import mundo.ConsultaAltura;

/**
 * Do que é o chão num ponto, para o som dos passos. Segue as mesmas regras do shader do terreno:
 * água abaixo do nível zero, terra nos declives, na lama da beira dos lagos e nas trilhas, folhas
 * e grama no resto; dentro da Univali, o piso.
 */
public enum MaterialChao {
	GRAMA, TERRA, AGUA, PISO;

	/** Inclinação (1 - normal.y) a partir da qual o shader pinta terra exposta. */
	static final float INCLINACAO_TERRA = 0.08f;
	/** Até essa altura acima da água, a margem é lama (terra). */
	static final float MARGEM_LAMA = 0.5f;

	public static MaterialChao em(ConsultaAltura chao, float x, float z, boolean dentroDoPredio, boolean naTrilha) {
		if (dentroDoPredio) return PISO;
		float altura = chao.alturaEm(x, z);
		if (altura < 0.02f) return AGUA;
		if (naTrilha || altura < MARGEM_LAMA || chao.inclinacaoEm(x, z) > INCLINACAO_TERRA) return TERRA;
		return GRAMA;
	}
}
