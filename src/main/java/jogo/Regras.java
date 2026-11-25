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
	/** Depois de cada prova coletada, a próxima aparição vem em no máximo isto (a floresta reage). */
	public static final float APARICAO_DEPOIS_DA_PROVA = 15f;
	/** Com a lanterna ligada, o tempo até a próxima aparição corre essa vez mais rápido (a luz atrai). */
	public static final float LANTERNA_ATRAI = 1.3f;
	/** Margem da borda do mapa onde ele não surge (a muralha de árvores). */
	public static final float MARGEM_BORDA_APARICAO = 20f;

	// ---------- o encontro ----------

	/** Duração do surgimento (tremor e tela escurecendo), em segundos. */
	public static final float DURACAO_SURGIMENTO = 0.4f;
	/** Segundos para matar o Dragon Knight depois que ele surge. */
	public static final float TEMPO_CONTAGEM = 5f;
	/** Dano total para matá-lo (8 bagos de 1,5 de dano: um tiro de perto mata). */
	public static final float VIDA_MONSTRO = 10f;
	/** Depois da animação de morte, quanto tempo o corpo fica no chão antes de sumir. */
	public static final float ESPERA_DEPOIS_DA_MORTE = 2f;
	/** No ataque, a frente dele fica a esta distância dos olhos do jogador, em metros. */
	public static final float DISTANCIA_ATAQUE = 1.5f;
	/** Duração do susto em tela cheia antes da derrota, em segundos. */
	public static final float DURACAO_SUSTO = 0.8f;

	// ---------- o diretor de sustos (o ritmo da partida) ----------

	/**
	 * Um monstro de cada vez, com calmaria entre um e outro. Segundos de calmaria conforme as
	 * provas coletadas (0, 1, 2, 3): o fim é mais tenso, mas nunca sem respiro. A primeira calmaria
	 * é a CARENCIA. Cada calmaria varia ±VARIACAO_INTERVALO.
	 */
	public static final float[] CALMARIA = { 50f, 42f, 36f, 30f };
	/** A chance de a próxima ameaça ser o Dragon Knight (o resto é o Jubilado), conforme as provas. */
	public static final float[] CHANCE_DRAGON_KNIGHT = { 0.3f, 0.4f, 0.5f, 0.5f };
	/** Derrubado essas vezes na mesma vez, o Jubilado desiste (levanta e foge): a caçada não vira um ciclo sem fim. */
	public static final int QUEDAS_PARA_DESISTIR = 2;
	/** A vez do Jubilado dura no máximo isso, se ele não achar o jogador (segundos). */
	public static final float VEZ_JUBILADO = 60f;
	/** Se o Dragon Knight não achar lugar para surgir nesse tempo, a vez dele acaba (segundos). */
	public static final float ESPERA_DRAGON_KNIGHT = 20f;
	/** Uma prova coletada encurta a calmaria: a próxima ameaça vem em no máximo isso (segundos). */
	public static final float AMEACA_DEPOIS_DA_PROVA = 15f;

	// ---------- os Jubilados (os monstros que andam pela floresta) ----------

	/** Quantos Jubilados há no mapa: um caça (na vez dele), os outros rondam de longe. */
	public static final int JUBILADOS = 3;
	/**
	 * Os que rondam: ficam entre OBSERVA_PERTO e OBSERVA_LONGE metros do jogador (no limite do que
	 * a neblina deixa ver) e fogem se ele chegar a OBSERVA_FOGE metros ou os iluminar com a lanterna
	 * a menos de OBSERVA_LUZ metros.
	 */
	public static final float OBSERVA_PERTO = 18f, OBSERVA_LONGE = 34f, OBSERVA_FOGE = 12f, OBSERVA_LUZ = 26f;
	/** Velocidade de quem foge, em m/s, e até onde, em metros do jogador. */
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
	public static final int RESERVA_INICIAL = 8;
	public static final int CARTUCHOS_POR_CAIXA = 6;
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

	/** Intervalo até a próxima aparição: o da tabela, conforme as provas, com ±20% de sorte. */
	public static float intervaloAparicao(int provasColetadas, float sorteio01) {
		int i = Math.max(0, Math.min(INTERVALOS.length - 1, provasColetadas));
		return INTERVALOS[i] * (1 + VARIACAO_INTERVALO * (sorteio01 * 2 - 1));
	}
}
