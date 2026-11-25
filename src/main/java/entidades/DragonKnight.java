package entidades;

import java.util.List;
import java.util.Random;

import jogo.Regras;
import md2.Animador;
import mundo.ConsultaAltura;
import mundo.GradeColisao;
import obj.Object3D;

/**
 * O Dragon Knight: um cavaleiro montado num dragão que surge perto do jogador, encara,
 * e ataca se não morrer em 5 segundos. Só lógica (não usa OpenGL): o desenho fica em
 * render.DesenhoMonstro, e os sons e efeitos de tela na TelaJogo, avisada pelos Eventos.
 *
 * Máquina de estados:
 *   AUSENTE   invisível; o cronômetro conta até a próxima aparição.
 *   SURGINDO  0,4 s: aparece num lugar sorteado em volta do jogador, gritando.
 *   ENCARANDO parado, sempre virado para o jogador; a contagem de 5 s corre. Ao levar um tiro,
 *             toca a animação de dor por cima ("ferido"), mas a contagem NÃO para.
 *   MORRENDO  animação de morte; depois dela e de mais 2 s, volta a AUSENTE.
 *   ATACANDO  vai para a frente do jogador e ataca; no fim da animação, a partida está perdida.
 *
 * Herda de Object3D (do projeto original): x, y, z, vivo, life e levaDano são reaproveitados.
 * A frente do modelo MD2 é o eixo +x; "guinada" gira o modelo em volta do eixo y.
 */
public class DragonKnight extends Object3D implements Atingivel {

	public enum Estado { AUSENTE, SURGINDO, ENCARANDO, MORRENDO, ATACANDO }

	/** Quem quer saber o que o monstro faz (sons, tremor, susto). Tudo opcional. */
	public interface Eventos {
		default void surgiu(DragonKnight monstro) {}
		default void ferido(DragonKnight monstro) {}
		default void morreu(DragonKnight monstro) {}
		default void atacou(DragonKnight monstro) {}
	}

	/**
	 * A caixa de acerto sem o modelo MD2 (o substituto): o tamanho do dragão do Dragon Knight,
	 * {xMin, yMin, zMin, xMax, yMax, zMax} em metros, com a frente em +x.
	 */
	public static final float[] CAIXA_PADRAO = { -2.85f, 0f, -1.15f, 2.0f, 2.2f, 1.1f };
	/** Sem o MD2, quanto duram a dor, a morte e o ataque (o que seriam as animações). */
	private static final float DURACAO_DOR = 0.3f, DURACAO_MORTE = 1.0f, DURACAO_ATAQUE = 0.8f;
	/** Quanto tempo o tom vermelho do dano leva para sumir, em segundos. */
	private static final float DURACAO_MARCA_DANO = 0.3f;

	private final ConsultaAltura chao;
	private final GradeColisao colisao;
	private final Random random;
	/** As animações do MD2; null quando o modelo não existe (o substituto usa só os tempos). */
	private final Animador animador;
	private final float[] caixa;
	private Eventos eventos = new Eventos() {};

	private Estado estado = Estado.AUSENTE;
	/** Em AUSENTE: segundos até a próxima aparição. Começa com a carência do início da partida. */
	private float cronometro = Regras.CARENCIA;
	private float tempoNoEstado = 0;
	private float tempoDor = 0;
	private float contagem = 0;
	private boolean ferido = false;
	private boolean animacaoTerminou = false;
	private boolean ataqueTerminou = false;
	private float marcaDano = 0;
	private int aparicoesSobrevividas = 0;
	/** Limite para a próxima aparição, pedido por uma prova coletada enquanto ele estava em cena. */
	private float limiteProximaAparicao = Float.MAX_VALUE;

	/** Rotação do modelo em volta do eixo y, em radianos (0 = olhando para +x). */
	public float guinada = 0;

	/** Quantas provas o jogador já tem: as aparições ficam mais frequentes (as provas vêm depois). */
	public int provasColetadas = 0;
	/** A lanterna do jogador está ligada? A luz o atrai: a próxima aparição chega mais cedo. */
	public boolean lanternaLigada = false;
	/** Enquanto falso, ele nem conta o tempo (a partida começa dentro da Univali, onde ele não entra). */
	public boolean ativo = true;
	/** As paredes da Univali: ele não surge a menos de 25 m delas (null = sem zona segura). */
	public GradeColisao.Retangulo zonaSegura = null;

	// o alvo: os olhos do jogador e a direção em que ele olha, no chão
	private float alvoX, alvoY, alvoZ, frenteX = 0, frenteZ = 1;

	public DragonKnight(ConsultaAltura chao, GradeColisao colisao, Animador animador, float[] caixa, long semente) {
		super(0, 0, 0);
		this.chao = chao;
		this.colisao = colisao;
		this.animador = animador;
		this.caixa = caixa != null ? caixa.clone() : CAIXA_PADRAO.clone();
		this.random = new Random(semente);
		vivo = false;
	}

	public void definirEventos(Eventos eventos) {
		this.eventos = eventos;
	}

	/** Onde está o jogador (os olhos) e para onde ele olha. Chamar antes do SimulaSe. */
	public void definirAlvo(float x, float y, float z, float frenteX, float frenteZ) {
		alvoX = x;
		alvoY = y;
		alvoZ = z;
		float t = (float) Math.sqrt(frenteX * frenteX + frenteZ * frenteZ);
		if (t > 1e-4f) {
			this.frenteX = frenteX / t;
			this.frenteZ = frenteZ / t;
		}
	}

	// ---------- simulação ----------

	@Override
	public void SimulaSe(long diftime) {
		if (!ativo) return;
		float dt = diftime / 1000f;
		tempoNoEstado += dt;
		tempoDor += dt;
		if (ferido && animador == null && tempoDor >= DURACAO_DOR) {
			ferido = false; // o substituto não tem animação de dor: só o tempo dela
		}
		marcaDano = Math.max(0, marcaDano - dt / DURACAO_MARCA_DANO);
		if (animador != null) {
			animador.atualizar(dt);
		}

		switch (estado) {
		case AUSENTE -> {
			cronometro -= dt * (lanternaLigada ? Regras.LANTERNA_ATRAI : 1);
			if (cronometro <= 0) {
				float[] lugar = sortearLugar();
				if (lugar == null) {
					cronometro = Regras.ESPERA_SEM_LUGAR; // nenhum lugar serviu: tenta de novo daqui a pouco
				} else {
					surgir(lugar[0], lugar[1]);
				}
			}
		}
		case SURGINDO -> {
			virarParaOAlvo();
			if (tempoNoEstado >= Regras.DURACAO_SURGIMENTO) {
				mudarPara(Estado.ENCARANDO);
				contagem = Regras.TEMPO_CONTAGEM;
				if (!ferido) tocarEncarando();
			}
		}
		case ENCARANDO -> {
			virarParaOAlvo();
			contagem -= dt;
			if (contagem <= 0) {
				contagem = 0;
				atacar();
			}
		}
		case MORRENDO -> {
			if (animador == null && tempoNoEstado >= DURACAO_MORTE) {
				animacaoTerminou = true;
			}
			if (animacaoTerminou) {
				cronometro -= dt; // conta os 2 s com o corpo no chão
				if (cronometro <= 0) {
					sumir();
				}
			}
		}
		case ATACANDO -> {
			if (animador == null && tempoNoEstado >= DURACAO_ATAQUE) {
				ataqueTerminou = true;
			}
		}
		}
	}

	private void mudarPara(Estado novo) {
		estado = novo;
		tempoNoEstado = 0;
	}

	/**
	 * Sorteia até 30 lugares em volta do jogador (8 a 15 m, qualquer ângulo, inclusive atrás)
	 * e devolve o primeiro que serve: dentro do mapa, longe da borda, em terra firme, sem
	 * declive forte, longe da zona segura, sem nada no lugar e com linha livre (nenhum tronco,
	 * parede ou móvel a menos de 1,2 m da reta) até o jogador. Sem zona segura (na volta com as
	 * provas), isso vale dentro da Univali também: ele surge no corredor ou numa sala à vista.
	 */
	float[] sortearLugar() {
		for (int i = 0; i < Regras.TENTATIVAS_APARICAO; i++) {
			double angulo = random.nextDouble() * 2 * Math.PI;
			float distancia = Regras.DISTANCIA_MINIMA_APARICAO
					+ random.nextFloat() * (Regras.DISTANCIA_MAXIMA_APARICAO - Regras.DISTANCIA_MINIMA_APARICAO);
			float x = alvoX + (float) Math.cos(angulo) * distancia;
			float z = alvoZ + (float) Math.sin(angulo) * distancia;
			if (lugarServe(x, z)) {
				return new float[] { x, z };
			}
		}
		return null;
	}

	boolean lugarServe(float x, float z) {
		if (!chao.dentroDoMapa(x, z, Regras.MARGEM_BORDA_APARICAO)) return false;
		if (chao.ehAgua(x, z)) return false;
		if (chao.anguloEm(x, z) > Jogador.DECLIVE_MAXIMO) return false;
		if (zonaSegura != null && zonaSegura.distancia(x, z) < Regras.RAIO_ZONA_SEGURA) return false;
		if (colisao.colide(x, z, 0.6f)) return false; // não surge dentro de um tronco, parede ou móvel
		return colisao.linhaLivre(x, z, alvoX, alvoZ, Regras.FOLGA_LINHA_DE_VISAO);
	}

	private void surgir(float x, float z) {
		this.x = x;
		this.z = z;
		this.y = chao.alturaEm(x, z);
		life = Regras.VIDA_MONSTRO;
		vivo = true;
		ferido = false;
		ataqueTerminou = false;
		virarParaOAlvo();
		mudarPara(Estado.SURGINDO);
		tocar("stand", true, null);
		eventos.surgiu(this);
	}

	/** Gira o modelo para a frente (+x) apontar para o jogador. */
	private void virarParaOAlvo() {
		guinada = guinadaPara(alvoX - x, alvoZ - z);
	}

	/**
	 * A rotação em volta de y que leva o eixo +x do modelo para a direção (dx, dz). Girar o
	 * ponto (1, 0, 0) por um ângulo g em volta de y dá (cos g, 0, -sen g); então g = atan2(-dz, dx).
	 */
	public static float guinadaPara(float dx, float dz) {
		return (float) Math.atan2(-dz, dx);
	}

	private void tocarEncarando() {
		boolean temProvocacao = animador != null && animador.animacao("taunt") != null;
		tocar(temProvocacao ? "taunt" : "stand", true, null);
	}

	/** Leva dano (só enquanto está surgindo ou encarando). A contagem continua correndo. */
	@Override
	public void receberDano(float valor) {
		if (estado != Estado.SURGINDO && estado != Estado.ENCARANDO) {
			return;
		}
		levaDano(valor); // do Object3D: diminui life e desliga vivo quando chega a zero
		marcaDano = 1;
		if (!vivo) {
			morrer();
			return;
		}
		ferido = true;
		tempoDor = 0;
		eventos.ferido(this);
		tocar("pain", false, () -> {
			ferido = false;
			tocarEncarando();
		});
	}

	private void morrer() {
		mudarPara(Estado.MORRENDO);
		ferido = false;
		animacaoTerminou = false;
		cronometro = Regras.ESPERA_DEPOIS_DA_MORTE;
		aparicoesSobrevividas++;
		tocar("death", false, () -> animacaoTerminou = true);
		eventos.morreu(this);
	}

	/** O corpo some; a próxima aparição vem depois do intervalo, que depende das provas. */
	private void sumir() {
		mudarPara(Estado.AUSENTE);
		vivo = false;
		cronometro = Math.min(Regras.intervaloAparicao(provasColetadas, random.nextFloat()), limiteProximaAparicao);
		limiteProximaAparicao = Float.MAX_VALUE;
	}

	/**
	 * Uma prova foi coletada: a floresta reage e a próxima aparição vem em no máximo "segundos".
	 * Se ele já está em cena, vale para a aparição seguinte.
	 */
	public void apressarProximaAparicao(float segundos) {
		if (estado == Estado.AUSENTE) {
			cronometro = Math.min(cronometro, segundos);
		} else {
			limiteProximaAparicao = Math.min(limiteProximaAparicao, segundos);
		}
	}

	/** Segura a próxima aparição por pelo menos "segundos" (o outro monstro está atrás do jogador). */
	public void segurarAparicao(float segundos) {
		if (estado == Estado.AUSENTE) {
			cronometro = Math.max(cronometro, segundos);
		}
	}

	/** O tempo acabou: vai para a frente do jogador (a frente do dragão a 1,5 m dos olhos) e ataca. */
	private void atacar() {
		float distancia = Regras.DISTANCIA_ATAQUE + caixa[3]; // caixa[3]: o focinho, na frente do centro
		x = alvoX + frenteX * distancia;
		z = alvoZ + frenteZ * distancia;
		y = chao.alturaEm(x, z);
		virarParaOAlvo();
		ferido = false;
		mudarPara(Estado.ATACANDO);
		tocar("attack", false, () -> ataqueTerminou = true);
		eventos.atacou(this);
	}

	/**
	 * Toca a primeira animação cujo nome começa com o prefixo (o Dragon Knight tem "standdr",
	 * "painaupp", "deatha"...). Se houver várias (três de dor, três de morte), sorteia uma.
	 */
	private void tocar(String prefixo, boolean loop, Runnable aoTerminar) {
		if (animador == null) return;
		List<String> opcoes = animador.nomes().stream().filter(n -> n.startsWith(prefixo)).toList();
		if (opcoes.isEmpty()) {
			if (aoTerminar != null) aoTerminar.run(); // sem a animação: termina na hora
			return;
		}
		animador.tocar(opcoes.get(random.nextInt(opcoes.size())), loop, aoTerminar);
	}

	// ---------- tiro ----------

	/**
	 * Distância ao longo do raio até a caixa do corpo inteiro (o dragão com o cavaleiro), ou -1
	 * se não acerta. A direção deve ser unitária. Só acerta enquanto ele surge ou encara.
	 *
	 * O raio é levado para o espaço do modelo (desfazendo a posição e a guinada), onde a caixa
	 * fica alinhada aos eixos; lá vale o teste das "fatias" (slabs): para cada eixo, o trecho do
	 * raio entre os dois planos da caixa; o raio acerta se os três trechos se cruzam.
	 */
	@Override
	public float intersectaRaio(float[] origem, float[] direcao) {
		if (estado != Estado.SURGINDO && estado != Estado.ENCARANDO) {
			return -1;
		}
		float c = (float) Math.cos(guinada), s = (float) Math.sin(guinada);
		float rx = origem[0] - x, ry = origem[1] - y, rz = origem[2] - z;
		// girar por -guinada: o inverso do giro do modelo
		float[] o = { c * rx - s * rz, ry, s * rx + c * rz };
		float[] d = { c * direcao[0] - s * direcao[2], direcao[1], s * direcao[0] + c * direcao[2] };
		return raioCaixa(o, d, caixa);
	}

	/** Raio contra caixa alinhada aos eixos: a distância da entrada (0 se começa dentro) ou -1. */
	static float raioCaixa(float[] o, float[] d, float[] caixa) {
		float entrada = -Float.MAX_VALUE, saida = Float.MAX_VALUE;
		for (int k = 0; k < 3; k++) {
			float minimo = caixa[k], maximo = caixa[k + 3];
			if (Math.abs(d[k]) < 1e-8f) {
				if (o[k] < minimo || o[k] > maximo) return -1; // paralelo ao eixo e fora da fatia
				continue;
			}
			float t1 = (minimo - o[k]) / d[k], t2 = (maximo - o[k]) / d[k];
			entrada = Math.max(entrada, Math.min(t1, t2));
			saida = Math.min(saida, Math.max(t1, t2));
		}
		if (saida < Math.max(entrada, 0)) return -1;
		return Math.max(entrada, 0);
	}

	// ---------- depuração ----------

	/** Faz surgir agora (se estiver ausente). */
	public void forcarAparicao() {
		if (estado == Estado.AUSENTE) cronometro = 0;
	}

	/** Mata na hora (se estiver surgindo ou encarando). */
	public void matar() {
		receberDano(life);
	}

	// ---------- consultas ----------

	public Estado estado() {
		return estado;
	}

	/** Aparece na tela? (Em todos os estados, menos AUSENTE.) */
	public boolean visivel() {
		return estado != Estado.AUSENTE;
	}

	/** Segundos que faltam na contagem (só vale em ENCARANDO). */
	public float contagem() {
		return contagem;
	}

	/** Segundos até a próxima aparição (só vale em AUSENTE). */
	public float cronometro() {
		return cronometro;
	}

	public float tempoNoEstado() {
		return tempoNoEstado;
	}

	public boolean ferido() {
		return ferido;
	}

	/** De 1 (acabou de levar o tiro) a 0: o tom vermelho no modelo. */
	public float marcaDano() {
		return marcaDano;
	}

	/** A animação de ataque terminou: hora do susto e da derrota. */
	public boolean ataqueTerminou() {
		return ataqueTerminou;
	}

	public int aparicoesSobrevividas() {
		return aparicoesSobrevividas;
	}

	public Animador animador() {
		return animador;
	}

	public float[] caixa() {
		return caixa.clone();
	}
}
