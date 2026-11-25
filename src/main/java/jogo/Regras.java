package jogo;

/**
 * Os números que balanceiam a partida, num lugar só: para deixar o jogo mais fácil ou mais
 * difícil, muda-se aqui, sem procurar constantes espalhadas pelo código.
 */
public final class Regras {

	private Regras() {}

	// ---------- o começo da floresta ----------

	/** Segundos sem nenhuma caçada no começo (depois de sair da Univali), para o jogador se situar. */
	public static final float CARENCIA = 45f;
	/** Variação aleatória das calmarias, para mais ou para menos (0,2 = ±20%). */
	public static final float VARIACAO_INTERVALO = 0.2f;

	// ---------- o Dragon Knight: o chefão da volta, dentro da Univali ----------

	/** Tiros bons para derrubá-lo, e quanto dano um tiro precisa dar para contar (3 bagos no corpo). */
	public static final int GOLPES_PARA_MATAR = 3;
	public static final float DANO_DE_GOLPE = 3 * 1.5f;
	/** Velocidades em m/s: avançando (o jogador corre a 6), recuando depois de um golpe e no pulo para o lado. */
	public static final float CAVALEIRO_AVANCA = 4.2f, CAVALEIRO_RECUA = 6f, CAVALEIRO_DESVIA = 7f;
	/** O zigue-zague: quanto ele vai para cada lado (metros) e quantas vezes por segundo troca de lado. */
	public static final float CAVALEIRO_ZIGUE = 1.1f, CAVALEIRO_ZIGUE_RITMO = 0.45f;
	/** Mirando a menos disso (graus) do centro dele, ele pula para o lado; e espera isso (s) para pular de novo. */
	public static final float CAVALEIRO_MIRA = 7f, CAVALEIRO_ESPERA_DESVIO = 1.8f;
	/** Daqui (metros, do centro dele) ele alcança o jogador e ataca. */
	public static final float CAVALEIRO_ALCANCE = 2.6f;
	/** Quanto ele provoca ao surgir e quanto fica escondido depois de recuar, em segundos. */
	public static final float CAVALEIRO_SURGINDO = 1.4f, CAVALEIRO_ESCONDIDO = 2.5f;
	/** A tela escurece e treme no começo do surgimento, por isso (segundos). */
	public static final float DURACAO_SURGIMENTO = 0.4f;

	/** Duração do susto em tela cheia antes da derrota, em segundos. */
	public static final float DURACAO_SUSTO = 0.8f;

	// ---------- o diretor de sustos (o ritmo da partida) ----------

	/**
	 * Na floresta, caçadas dos Jubilados com calmaria entre uma e outra. Segundos de calmaria
	 * conforme as provas coletadas (0, 1, 2, 3): o fim é mais tenso, mas nunca sem respiro. A
	 * primeira calmaria é a CARENCIA. Cada calmaria varia ±VARIACAO_INTERVALO.
	 */
	public static final float[] CALMARIA = { 50f, 42f, 36f, 30f };
	/** A chance de uma caçada ter dois Jubilados caçando juntos, conforme as provas (o resto é um só). */
	public static final float[] CHANCE_DOIS_CACADORES = { 0f, 0.15f, 0.25f, 0.35f };
	/** Derrubado essas vezes na mesma vez, o Jubilado desiste (levanta e foge): a caçada não vira um ciclo sem fim. */
	public static final int QUEDAS_PARA_DESISTIR = 2;
	/** A vez do Jubilado dura no máximo isso, se ele não achar o jogador (segundos). */
	public static final float VEZ_JUBILADO = 60f;
	/** Uma prova coletada encurta a calmaria: a próxima ameaça vem em no máximo isso (segundos). */
	public static final float AMEACA_DEPOIS_DA_PROVA = 15f;

	// ---------- os Jubilados (os monstros que andam pela floresta) ----------

	/** Quantos Jubilados há no mapa: um (às vezes dois) caça na caçada, os outros rondam de longe. */
	public static final int JUBILADOS = 4;
	/**
	 * Os que rondam: ficam entre OBSERVA_PERTO e OBSERVA_LONGE metros do jogador (no limite do que
	 * a neblina deixa ver). Se ele chegar a OBSERVA_RECUA metros ou os iluminar com a lanterna a menos
	 * de OBSERVA_LUZ metros, param, encaram e recuam de costas, devagar.
	 */
	public static final float OBSERVA_PERTO = 18f, OBSERVA_LONGE = 34f, OBSERVA_RECUA = 12f, OBSERVA_LUZ = 26f;
	/** Recuando de costas, encarando o jogador, em m/s. */
	public static final float JUBILADO_RECUANDO = 0.8f;
	/**
	 * Provocar quem ronda: a lanterna nele por JUBILADO_PROVOCA segundos (somados; sem a luz, o
	 * incômodo baixa pela metade desse ritmo) ou o jogador a menos de OBSERVA_PROVOCA_PERTO metros.
	 * Provocado, ele vira caçador na hora.
	 */
	public static final float JUBILADO_PROVOCA = 2f, OBSERVA_PROVOCA_PERTO = 6f;
	/** Velocidade de quem foge (o derrubado que desistiu), em m/s, e até onde, em metros do jogador. */
	public static final float JUBILADO_FUGINDO = 5f, FOGE_ATE = 60f;

	/** Velocidades em m/s: vagando, indo ver um barulho e caçando (o jogador anda a 3,5 e corre a 6). */
	public static final float JUBILADO_VAGANDO = 1.4f, JUBILADO_DESCONFIADO = 2.4f, JUBILADO_CACANDO = 5.2f;
	/** Até onde ele enxerga, em metros: normal, com a lanterna ligada (a luz denuncia) e "sente" atrás de si. */
	public static final float JUBILADO_VISAO = 16f, JUBILADO_VISAO_LANTERNA = 30f, JUBILADO_SENTE = 4f;
	/** Metade do ângulo de visão, em graus (70 = enxerga 140° à frente). */
	public static final float JUBILADO_ANGULO_VISAO = 70f;
	/** Até onde ele ouve, em metros: o jogador correndo, andando e um tiro. */
	public static final float JUBILADO_OUVE_CORRIDA = 28f, JUBILADO_OUVE_PASSOS = 7f, JUBILADO_OUVE_TIRO = 70f;
	/** Segundos sem ver o jogador até desistir da caçada, e procurando no último lugar antes de voltar a vagar. */
	public static final float JUBILADO_PERDE = 4f, JUBILADO_PROCURA = 7f;
	/** A que distância ele pega o jogador (e a partida está perdida), em metros. */
	public static final float JUBILADO_PEGA = 1.3f;
	/** Segundos caído depois de levar um tiro (a escopeta não o mata: só derruba). */
	public static final float JUBILADO_CAIDO = 12f;
	/**
	 * O "diretor": longe do jogador (mais que JUBILADO_LONGE metros, onde a neblina já o esconde),
	 * ele se aproxima rápido; perto, volta a vagar devagar, rondando (a chance de cada destino ser
	 * perto do jogador). Depois de uma caçada, um respiro em segundos, em que ele deixa o jogador em paz.
	 */
	public static final float JUBILADO_LONGE = 50f, JUBILADO_APROXIMANDO = 5.5f, JUBILADO_CHANCE_RONDAR = 0.75f,
			JUBILADO_RESPIRO = 35f;
	/** Rondando, a chance de o destino ser perto da prova que falta mais perto do jogador (uma emboscada). */
	public static final float JUBILADO_CHANCE_EMBOSCADA = 0.35f;
	/** No começo (depois que o jogador sai da Univali), segundos em que ele só vaga, sem vir atrás. */
	public static final float JUBILADO_CARENCIA = 25f;
	/**
	 * Na vez dele, farejando o jogador: vem a essa velocidade (m/s) até chegar a FAREJA_PERTO metros
	 * (mais rápido que o jogador andando e mais lento que correndo: dá para fugir, mas correr faz barulho).
	 */
	public static final float JUBILADO_FAREJANDO = 4.5f, FAREJA_PERTO = 20f;
	/** Farejando, ele sussurra (dá para ouvir de que lado ele vem) a partir dessa distância, em metros. */
	public static final float JUBILADO_SUSSURRA_DE = 45f;
	/** Distância mínima do jogador onde ele começa, e da Univali (ele não chega perto do prédio), em metros. */
	public static final float JUBILADO_INICIO = 70f, JUBILADO_LONGE_UNIVALI = 12f;

	// ---------- escopeta ----------

	/** Bagos por tiro, dano de cada um e o espalhamento (ângulo entre o centro e a borda do cone). */
	public static final int BAGOS = 8;
	public static final float DANO_BAGO = 1.5f;
	public static final float ESPALHAMENTO_GRAUS = 6f;
	/** Até onde um bago vai, em metros. */
	public static final float ALCANCE_TIRO = 40f;
	/** Cartuchos: quantos cabem no cano, quantos de reserva no começo e quantos uma caixa dá. */
	public static final int CAPACIDADE_CANO = 2;
	public static final int RESERVA_INICIAL = 6;
	public static final int CARTUCHOS_POR_CAIXA = 8;
	/**
	 * Mais munição, porque os Jubilados ficaram mais comuns e há o Dragon Knight na volta: uma
	 * caixinha ao lado de cada prova (o aluno da carta deixou) e uma caixa no saguão que só aparece
	 * na volta com as provas, para a luta final. Ao todo, uns 50 cartuchos.
	 */
	public static final int CARTUCHOS_PERTO_DA_PROVA = 4, CARTUCHOS_NA_VOLTA = 8;
	/** Caixas de munição no mapa e a distância para pegar uma (tecla E). */
	public static final int CAIXAS_MUNICAO = 3;
	public static final float DISTANCIA_PEGAR = 1.5f;
	/** Tempos da arma, em segundos: o tiro com a telha, e cada cartucho na recarga. */
	public static final float DURACAO_TIRO = 0.3f;
	public static final float DURACAO_CARTUCHO = 0.5f;

	// ---------- AG da fase ----------

	/** Pesos de cada termo do fitness da fase (somam 1). */
	public static final double PESO_PROVAS_ESPALHADAS = 0.30, PESO_PROGRESSAO = 0.35, PESO_UNIVALI_BORDA = 0.15,
			PESO_CAIXAS = 0.20;
	/** Distâncias de caminho (A*) desejadas da Univali até as provas: uma perto, uma média, uma longe. */
	public static final float[] PROGRESSAO_PROVAS = { 70f, 130f, 190f };
	/** Distância mínima entre as provas, e de cada uma até a Univali. */
	public static final float DISTANCIA_PROVAS = 60f;
	/** A Univali fica a pelo menos isto da borda. */
	public static final float UNIVALI_BORDA = 40f;
	/** Uma caixa de munição boa fica entre 20 e 50 m de alguma prova. */
	public static final float CAIXA_MINIMA = 20f, CAIXA_MAXIMA = 50f;
	/** Peso da inclinação no custo do A* (subir morro custa mais). */
	public static final float CUSTO_INCLINACAO = 2f;
}
