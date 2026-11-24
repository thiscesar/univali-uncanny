package jogo;

/**
 * A nota final de quem foi aprovado (vencer sempre aprova): começa em 10, perde 0,5 por minuto
 * acima de 8 minutos de partida e ganha 0,25 por aparição do Dragon Knight sobrevivida (no
 * máximo +1). Fica sempre entre 6,0 e 10,0, com uma casa decimal.
 */
public final class Nota {

	public static final float INICIAL = 10f, MINIMA = 6f, MAXIMA = 10f;
	public static final float MINUTOS_SEM_DESCONTO = 8f, DESCONTO_POR_MINUTO = 0.5f;
	public static final float BONUS_POR_APARICAO = 0.25f, BONUS_MAXIMO = 1f;

	private Nota() {}

	public static float calcular(float segundos, int aparicoesSobrevividas) {
		float minutos = segundos / 60f;
		float desconto = Math.max(0, minutos - MINUTOS_SEM_DESCONTO) * DESCONTO_POR_MINUTO;
		float bonus = Math.min(BONUS_MAXIMO, aparicoesSobrevividas * BONUS_POR_APARICAO);
		float nota = Math.max(MINIMA, Math.min(MAXIMA, INICIAL - desconto + bonus));
		return Math.round(nota * 10) / 10f; // uma casa decimal
	}

	/** O comentário da tela final, pela faixa da nota. */
	public static String comentario(float nota) {
		if (nota < 7.5f) return "Passou raspando.";
		if (nota < 9f) return "Nada mal.";
		return "Aluno destaque!";
	}

	/** "8,7": com vírgula, como no boletim. */
	public static String formatar(float nota) {
		return String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.1f", nota);
	}
}
