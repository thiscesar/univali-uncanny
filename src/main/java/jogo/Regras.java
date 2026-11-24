package jogo;

/**
 * Os números que balanceiam a partida, num lugar só: para deixar o jogo mais fácil ou mais
 * difícil, muda-se aqui, sem procurar constantes espalhadas pelo código.
 */
public final class Regras {

	private Regras() {}

	// ---------- aparições do Dragon Knight ----------

	/** Segundos sem nenhuma aparição no começo da partida (para o jogador se situar). */
	public static final float CARENCIA = 45f;
	/** Segundos entre aparições, conforme as provas já coletadas (0, 1, 2 ou 3). */
	public static final float[] INTERVALOS = { 60f, 40f, 25f, 15f };
	/** Variação aleatória do intervalo, para mais ou para menos (0,2 = ±20%). */
	public static final float VARIACAO_INTERVALO = 0.2f;

	/** Distância do jogador em que ele surge, em metros. */
	public static final float DISTANCIA_MINIMA_APARICAO = 8f;
	public static final float DISTANCIA_MAXIMA_APARICAO = 15f;
	/** Quantos lugares sorteados testar antes de desistir e tentar de novo mais tarde. */
	public static final int TENTATIVAS_APARICAO = 30;
	/** Sem lugar válido, quantos segundos esperar para sortear de novo. */
	public static final float ESPERA_SEM_LUGAR = 2f;
	/** Folga da linha de visão até o jogador, além do raio dos troncos (o dragão é largo). */
	public static final float FOLGA_LINHA_DE_VISAO = 1.2f;
	/** Não surge a menos disso do prédio da Univali, em metros. */
	public static final float RAIO_ZONA_SEGURA = 25f;
	/** Margem da borda do mapa onde ele não surge (a muralha de árvores). */
	public static final float MARGEM_BORDA_APARICAO = 20f;

	// ---------- o encontro ----------

	/** Duração do surgimento (tremor e tela escurecendo), em segundos. */
	public static final float DURACAO_SURGIMENTO = 0.4f;
	/** Segundos para matar o Dragon Knight depois que ele surge. */
	public static final float TEMPO_CONTAGEM = 3f;
	/** Dano total para matá-lo (8 bagos de 1,5 de dano: um tiro de perto mata). */
	public static final float VIDA_MONSTRO = 10f;
	/** Depois da animação de morte, quanto tempo o corpo fica no chão antes de sumir. */
	public static final float ESPERA_DEPOIS_DA_MORTE = 2f;
	/** No ataque, a frente dele fica a esta distância dos olhos do jogador, em metros. */
	public static final float DISTANCIA_ATAQUE = 1.5f;
	/** Duração do susto em tela cheia antes da derrota, em segundos. */
	public static final float DURACAO_SUSTO = 0.8f;

	/** Intervalo até a próxima aparição: o da tabela, conforme as provas, com ±20% de sorte. */
	public static float intervaloAparicao(int provasColetadas, float sorteio01) {
		int i = Math.max(0, Math.min(INTERVALOS.length - 1, provasColetadas));
		return INTERVALOS[i] * (1 + VARIACAO_INTERVALO * (sorteio01 * 2 - 1));
	}
}
