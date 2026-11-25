package entidades;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import grafos.AEstrela;
import grafos.GradeCaminhada;
import jogo.Regras;
import md2.Animador;
import mundo.ConsultaAltura;
import mundo.GradeColisao;
import obj.Object3D;

/**
 * O Jubilado: um aluno que estourou o prazo do curso e ficou preso na floresta. Ao contrário
 * do Dragon Knight, que surge do nada, ele está sempre lá, andando entre as árvores. Ele não
 * sabe onde o jogador está: precisa vê-lo ou ouvi-lo. Só lógica (não usa OpenGL): o desenho fica
 * em render.DesenhoMonstro e os sons na TelaJogo, avisada pelos Eventos.
 *
 * Máquina de estados:
 *   VAGANDO     anda devagar até um ponto sorteado (quase sempre perto do jogador), para um
 *               pouco e sorteia outro. Grunhe de vez em quando. Longe do jogador (escondido
 *               pela neblina), anda rápido até a região dele: assim ele sempre volta a rondar.
 *   DESCONFIADO ouviu algo (o jogador correndo ou andando perto, um tiro): vai até o barulho.
 *               Quando ganha a vez (o diretor o solta), fareja: vai até onde o jogador está agora,
 *               refazendo o caminho conforme ele anda, até vê-lo ou ouvi-lo.
 *   CACANDO     viu o jogador: corre atrás dele. Correndo ou à vista, o jogador continua sendo
 *               seguido; sem vê-lo nem ouvi-lo por 4 s, ele desiste.
 *   PROCURANDO  no último lugar onde viu ou ouviu o jogador: para e olha em volta por 7 s.
 *               Depois de uma caçada, dá um respiro de 35 s ao jogador (vaga em volta de si).
 *   CAIDO       levou um tiro de perto: cai e fica 12 s no chão. A escopeta não o mata. Ao
 *               levantar, vai direto para onde o jogador está.
 *   ATACANDO    alcançou o jogador: ataca; no fim da animação, a partida está perdida.
 *   FUGINDO     (só quem ronda) o jogador chegou perto ou o iluminou: corre para longe, na neblina.
 *
 * Há vários Jubilados no mapa, e cada um tem um papel, dado pelo diretor de sustos:
 *   CACADOR     o da vez: ronda, ouve, vê e caça, como descrito acima.
 *   OBSERVADOR  os outros: ficam rondando de longe (18 a 34 m), param e encaram o jogador entre as
 *               árvores, e fogem se ele chegar perto ou apontar a lanterna. Nunca atacam.
 *
 * Para andar, usa o A* na grade de caminhada (desvia de morro e de água, contorna a Univali)
 * e, a cada passo, a grade de colisão para escorregar em volta dos troncos, como o jogador.
 * Herda de Object3D (do projeto original): x, y, z e vivo são reaproveitados. Como no Dragon
 * Knight, a frente do modelo MD2 é o eixo +x e "guinada" gira o modelo em volta do eixo y.
 */
public class Jubilado extends Object3D implements Atingivel {

	public enum Estado { VAGANDO, DESCONFIADO, CACANDO, PROCURANDO, CAIDO, ATACANDO, FUGINDO }

	public enum Papel { CACADOR, OBSERVADOR }

	/** Quem quer saber o que ele faz (sons, música, susto). Tudo opcional. */
	public interface Eventos {
		default void passo(Jubilado j) {}
		default void grunhiu(Jubilado j) {}
		default void sussurrou(Jubilado j) {}
		default void viu(Jubilado j) {}
		default void perdeu(Jubilado j) {}
		default void caiu(Jubilado j) {}
		default void levantou(Jubilado j) {}
		default void atacou(Jubilado j) {}
	}

	/** A caixa de acerto sem o modelo MD2: {xMin, yMin, zMin, xMax, yMax, zMax} em metros. */
	public static final float[] CAIXA_PADRAO = { -0.4f, 0f, -0.4f, 0.4f, 2.0f, 0.4f };
	/** O corpo dele, para escorregar em volta dos troncos (o jogador tem 0,35 m). */
	static final float RAIO = 0.45f;
	/** Metros entre dois passos (um som de pisada a cada passada). */
	private static final float PASSADA = 1.3f;
	/** Quão rápido ele vira, em radianos por segundo, e acelera, em m/s². */
	private static final float GIRO = 5f, ACELERACAO = 7f;
	/** Caçando, abaixo dessa distância e com a linha livre, vai reto até o jogador, sem o A*. */
	private static final float PERTO_DIRETO = 10f;
	/** Caçando, refaz o caminho a cada tanto (o jogador se mexe). */
	private static final float REPLANEJAR = 0.6f;
	/** Bagos que precisam acertar no mesmo tiro para ele cair (um bago de longe só o irrita). */
	private static final int BAGOS_PARA_CAIR = 2;
	/** Sem o MD2: quanto duram o ataque e a queda (o que seriam as animações). */
	private static final float DURACAO_ATAQUE = 0.8f, DURACAO_QUEDA = 1.0f;
	private static final float DURACAO_MARCA_DANO = 0.3f;
	/** A animação de corrida do MD2 combina com essa velocidade (acima, ela acelera; abaixo, fica lenta). */
	private static final float VELOCIDADE_DA_CORRIDA = 3.2f;

	private final ConsultaAltura chao;
	private final GradeColisao colisao;
	private final GradeCaminhada grade;
	private final Random random;
	private final Animador animador;
	private final float[] caixa;
	private Eventos eventos = new Eventos() {};

	private Estado estado = Estado.VAGANDO;
	private float tempoNoEstado = 0;

	// o jogador, como a tela informa a cada quadro
	private float alvoX, alvoY, alvoZ;
	/** Para onde o jogador olha (no chão, unitário; 0 = não informado): para saber se a lanterna aponta para ele. */
	private float alvoFrenteX, alvoFrenteZ;
	private boolean alvoCorrendo, alvoAndando, alvoLanterna, alvoEscondido;
	/** Onde ele viu ou ouviu o jogador pela última vez, e há quanto tempo não o vê nem ouve. */
	private float ultimoX, ultimoZ, semNoticia;

	// o caminho do A*, em pontos {x, z}
	private List<float[]> caminho;
	private int proximo;
	private float replanejar;
	/** Vagando: segundos parado antes de sortear o próximo destino. */
	private float parado;
	/** Segundos de respiro que ainda faltam (sem rondar o jogador): a carência do começo e depois de cada caçada. */
	private float respiro = Regras.JUBILADO_CARENCIA;

	private float velocidade;
	private float andouDesdeOPasso;
	/** Para perceber quando ele empaca (preso entre troncos): onde estava e há quanto tempo. */
	private float refX, refZ, tempoRef;
	/** Quantas vezes seguidas ele empacou (na segunda, desatola). */
	private int empacadas;
	private float proximoGrunhido;
	private float marcaDano;
	private boolean animacaoTerminou, ataqueTerminou;
	/** Quantas vezes ele já foi derrubado na partida. */
	private int quedas;
	/** Ganhou a vez e ainda não achou o jogador: vai atrás do cheiro dele (onde ele está agora). */
	private boolean farejando;
	/** Farejando, segundos até o próximo sussurro. */
	private float proximoSussurro;

	/** Rotação do modelo em volta do eixo y, em radianos (0 = olhando para +x). */
	public float guinada = 0;
	/** Caça (o da vez) ou só ronda de longe. Sozinho, sem diretor, ele caça. */
	private Papel papel = Papel.CACADOR;
	/** Enquanto falso, ele fica parado no lugar (a partida começa dentro da Univali). */
	public boolean ativo = true;
	/** Com o Dragon Knight em cena, ele para e espera (os dois juntos seriam injustos). */
	public boolean pausado = false;
	/** As provas que o jogador ainda não pegou, {x, z}: ele ronda por lá, porque o jogador vai ter que passar. */
	public List<float[]> provasQueFaltam = List.of();
	/** A Univali: ele não chega a menos de JUBILADO_LONGE_UNIVALI metros dela (null = sem prédio). */
	public GradeColisao.Retangulo univali = null;

	public Jubilado(ConsultaAltura chao, GradeColisao colisao, GradeCaminhada grade, Animador animador, float[] caixa,
			long semente) {
		super(0, 0, 0);
		this.chao = chao;
		this.colisao = colisao;
		this.grade = grade;
		this.animador = animador;
		this.caixa = caixa != null ? caixaSimetrica(caixa) : CAIXA_PADRAO.clone();
		this.random = new Random(semente);
		proximoGrunhido = 6 + random.nextFloat() * 8;
		tocar("run", true);
	}

	/**
	 * A caixa do modelo é a do quadro 0, com os braços para um lado: aqui ela vira uma caixa
	 * centrada nos pés, com a largura limitada (os braços abertos não contam como corpo).
	 */
	private static float[] caixaSimetrica(float[] c) {
		float r = Math.min(0.55f, Math.max(Math.max(-c[0], c[3]), Math.max(-c[2], c[5])));
		return new float[] { -r, c[1], -r, r, c[4], r };
	}

	public void definirEventos(Eventos eventos) {
		this.eventos = eventos;
	}

	/**
	 * O jogador, a cada quadro, antes do SimulaSe: os pés (x, z) e os olhos (y), se está correndo
	 * ou andando (o barulho), se a lanterna está ligada (a luz se vê de longe) e se está escondido
	 * (dentro da Univali, onde ele não entra).
	 */
	public void definirAlvo(float x, float y, float z, boolean correndo, boolean andando, boolean lanterna,
			boolean escondido) {
		alvoX = x;
		alvoY = y;
		alvoZ = z;
		alvoCorrendo = correndo;
		alvoAndando = andando;
		alvoLanterna = lanterna;
		alvoEscondido = escondido;
	}

	/** Para onde o jogador olha (x, z no chão): com a lanterna ligada, quem ronda foge se a luz o pegar. */
	public void definirOlhar(float frenteX, float frenteZ) {
		float t = (float) Math.hypot(frenteX, frenteZ);
		alvoFrenteX = t > 1e-4f ? frenteX / t : 0;
		alvoFrenteZ = t > 1e-4f ? frenteZ / t : 0;
	}

	/** A vez dele: passa a caçar, e vem logo para perto do jogador (sem respiro). */
	public void liberarCaca() {
		papel = Papel.CACADOR;
		respiro = 0;
		if (estado != Estado.CAIDO && estado != Estado.ATACANDO) {
			farejando = true;
			desconfiar(alvoX, alvoZ); // fareja: vai até onde o jogador está
		}
	}

	/** Acabou a vez dele: volta a só rondar de longe (caído, termina de levantar e foge). */
	public void encerrarCaca() {
		papel = Papel.OBSERVADOR;
		farejando = false;
		if (estado == Estado.CACANDO || estado == Estado.DESCONFIADO || estado == Estado.PROCURANDO) {
			mudarPara(Estado.VAGANDO);
			caminho = null;
		}
	}

	public Papel papel() {
		return papel;
	}

	public void posicionarLongeDe(float px, float pz) {
		posicionarLongeDe(px, pz, List.of());
	}

	/**
	 * Coloca ele no mapa, longe do jogador (x, z), da Univali e dos outros Jubilados (pelo menos
	 * 50 m de cada um em "evitar", {x, z}), num lugar de onde dá para chegar até o jogador. O
	 * jogador começa dentro da Univali, que está fora da grade de caminhada: a ligação é
	 * conferida a partir da célula livre mais perto dele.
	 */
	public void posicionarLongeDe(float px, float pz, List<float[]> evitar) {
		float tamanho = grade.lado * grade.celula;
		int[] perto = celulaCaminhavelPerto(px, pz, grade.lado);
		int componente = perto != null ? grade.componente(perto[0], perto[1]) : -1;
		float borda = 40; // longe da muralha de árvores da borda, onde ele ficaria preso entre os troncos
		for (int tentativa = 0; tentativa < 400; tentativa++) {
			float cx = borda + random.nextFloat() * (tamanho - 2 * borda), cz = borda + random.nextFloat() * (tamanho - 2 * borda);
			boolean longe = Math.hypot(cx - px, cz - pz) >= Regras.JUBILADO_INICIO || tentativa > 300;
			for (float[] o : evitar) {
				if (tentativa <= 300 && Math.hypot(cx - o[0], cz - o[1]) < 50) longe = false;
			}
			if (longe && livre(cx, cz) && (componente < 0 || grade.componenteEm(cx, cz) == componente)) {
				x = cx;
				z = cz;
				y = chao.alturaEm(x, z);
				return;
			}
		}
	}

	// ---------- simulação ----------

	@Override
	public void SimulaSe(long diftime) {
		float dt = diftime / 1000f;
		marcaDano = Math.max(0, marcaDano - dt / DURACAO_MARCA_DANO);
		if (!ativo) return;
		if (animador != null) animador.atualizar(dt);
		if (pausado && estado != Estado.ATACANDO && estado != Estado.CAIDO) {
			velocidade = 0;
			tocar("stand", true);
			return;
		}
		tempoNoEstado += dt;
		replanejar -= dt;
		respiro = Math.max(0, respiro - dt);
		if (papel == Papel.OBSERVADOR && estado != Estado.CAIDO && estado != Estado.ATACANDO) {
			observar(dt);
			animarAndando();
			return;
		}

		boolean percebe = estado != Estado.CAIDO && estado != Estado.ATACANDO;
		boolean ve = percebe && ve();
		boolean ouve = percebe && !ve && ouve();

		switch (estado) {
		case VAGANDO -> {
			if (ve) { cacar(); return; }
			if (ouve) { desconfiar(alvoX, alvoZ); return; }
			grunhir(dt);
			boolean longe = longeDoJogador();
			if (longe && replanejar <= 0) {
				// o jogador anda: de tempos em tempos, refaz o destino para a região onde ele está agora
				replanejar = 4;
				parado = 0;
				sortearDestino();
			}
			if (parado > 0) {
				parado -= dt;
				frear(dt);
				if (parado <= 0) sortearDestino();
			} else if (caminho == null || seguirCaminho(dt, longe ? Regras.JUBILADO_APROXIMANDO : Regras.JUBILADO_VAGANDO)
					|| empacou(dt)) {
				parado = 1.5f + random.nextFloat() * 3; // chegou (ou empacou): para um pouco e escolhe outro lugar
				caminho = null;
			}
		}
		case DESCONFIADO -> {
			if (ve) { cacar(); return; }
			boolean mudou = Math.hypot(alvoX - ultimoX, alvoZ - ultimoZ) > 4;
			if (ouve && mudou || farejando && !alvoEscondido && mudou && replanejar <= 0) {
				replanejar = 3;
				desconfiar(alvoX, alvoZ); // o jogador andou: refaz o caminho até onde ele está
			}
			// farejando: longe (na neblina) vem bem rápido; até chegar perto, mais rápido que o jogador andando
			float dist = (float) Math.hypot(alvoX - x, alvoZ - z);
			if (farejando) sussurrar(dt, dist);
			else grunhir(dt);
			float rapidez = !farejando ? Regras.JUBILADO_DESCONFIADO
					: dist > Regras.JUBILADO_LONGE ? Regras.JUBILADO_APROXIMANDO
					: dist > Regras.FAREJA_PERTO ? Regras.JUBILADO_FAREJANDO : Regras.JUBILADO_DESCONFIADO;
			if (caminho == null || seguirCaminho(dt, rapidez) || empacou(dt)) {
				if (farejando && !alvoEscondido) desconfiar(alvoX, alvoZ); // chegou e ele já foi: continua farejando
				else procurar();
			}
		}
		case CACANDO -> {
			if (ve || ouve) {
				ultimoX = alvoX;
				ultimoZ = alvoZ;
				semNoticia = 0;
			} else {
				semNoticia += dt;
				if (semNoticia >= Regras.JUBILADO_PERDE) {
					eventos.perdeu(this);
					respiro = Regras.JUBILADO_RESPIRO;
					procurar();
					return;
				}
			}
			float dist = (float) Math.hypot(alvoX - x, alvoZ - z);
			if (ve && dist <= Regras.JUBILADO_PEGA) {
				atacar();
				return;
			}
			if (ve && dist <= PERTO_DIRETO && colisao.linhaLivre(x, z, alvoX, alvoZ, 0.1f)) {
				caminho = null;
				andarPara(dt, alvoX, alvoZ, Regras.JUBILADO_CACANDO);
			} else {
				if (caminho == null || replanejar <= 0) {
					replanejar = REPLANEJAR;
					planejar(ultimoX, ultimoZ);
				}
				if (caminho == null || seguirCaminho(dt, Regras.JUBILADO_CACANDO)) {
					frear(dt); // chegou onde o jogador estava e não o vê: os segundos sem notícia correm
				}
			}
		}
		case PROCURANDO -> {
			if (ve) { cacar(); return; }
			if (ouve) { desconfiar(alvoX, alvoZ); return; }
			frear(dt);
			guinada += dt * 0.9f * (float) Math.sin(tempoNoEstado * 0.8f); // olha para um lado e para o outro
			if (tempoNoEstado >= Regras.JUBILADO_PROCURA) {
				mudarPara(Estado.VAGANDO);
				tocar("run", true);
				parado = 0.5f;
			}
		}
		case CAIDO -> {
			velocidade = 0;
			if (tempoNoEstado >= Regras.JUBILADO_CAIDO && papel == Papel.OBSERVADOR) {
				fugir(); // quem só ronda levanta e some na neblina
			} else if (tempoNoEstado >= Regras.JUBILADO_CAIDO) {
				// levanta e vai direto para onde o jogador está
				ultimoX = alvoX;
				ultimoZ = alvoZ;
				semNoticia = Regras.JUBILADO_PERDE * 0.5f;
				mudarPara(Estado.CACANDO);
				caminho = null;
				tocar("run", true);
				eventos.levantou(this);
			}
		}
		case ATACANDO -> {
			velocidade = 0;
			guinada = guinadaPara(alvoX - x, alvoZ - z);
			if (animador == null && tempoNoEstado >= DURACAO_ATAQUE) ataqueTerminou = true;
		}
		}
		if (estado != Estado.ATACANDO && estado != Estado.CAIDO) {
			animarAndando();
		}
	}

	// ---------- quem só ronda ----------

	/**
	 * O observador: anda até um ponto a 28-50 m do jogador, para e fica encarando; depois escolhe
	 * outro. Longe demais, vem rápido (escondido na neblina). Perto demais ou na luz da lanterna, foge.
	 */
	private void observar(float dt) {
		if (estado != Estado.VAGANDO && estado != Estado.FUGINDO) {
			mudarPara(Estado.VAGANDO);
			caminho = null;
		}
		if (estado == Estado.FUGINDO) {
			if (caminho == null || seguirCaminho(dt, Regras.JUBILADO_FUGINDO) || empacou(dt)) {
				mudarPara(Estado.VAGANDO);
				caminho = null;
				parado = 2 + random.nextFloat() * 3;
			}
			return;
		}
		float dist = (float) Math.hypot(alvoX - x, alvoZ - z);
		if (!alvoEscondido && (dist < Regras.OBSERVA_FOGE || iluminado(dist))) {
			fugir();
			return;
		}
		grunhir(dt);
		boolean longe = !alvoEscondido && dist > Regras.OBSERVA_LONGE + 10;
		if (longe && replanejar <= 0) {
			replanejar = 4;
			parado = 0;
			sortearPontoDeObservacao();
		}
		if (parado > 0) {
			parado -= dt;
			frear(dt);
			if (!alvoEscondido) virarPara(guinadaPara(alvoX - x, alvoZ - z), dt); // parado, encara o jogador
			if (parado <= 0) sortearPontoDeObservacao();
		} else if (caminho == null || seguirCaminho(dt, longe ? Regras.JUBILADO_APROXIMANDO : Regras.JUBILADO_VAGANDO)
				|| empacou(dt)) {
			parado = 3 + random.nextFloat() * 5;
			caminho = null;
		}
	}

	/** A lanterna ligada, apontada para ele (até 18° do centro do facho), perto e sem tronco no meio. */
	private boolean iluminado(float dist) {
		if (!alvoLanterna || dist > Regras.OBSERVA_LUZ || dist < 1e-3f) return false;
		float cosseno = ((x - alvoX) * alvoFrenteX + (z - alvoZ) * alvoFrenteZ) / dist;
		return cosseno > (float) Math.cos(Math.toRadians(18)) && colisao.linhaLivre(alvoX, alvoZ, x, z, 0.1f);
	}

	/** Um ponto no anel de 18 a 34 m em volta do jogador (escondido, em volta de si). */
	private void sortearPontoDeObservacao() {
		for (int tentativa = 0; tentativa < 25; tentativa++) {
			double angulo = random.nextDouble() * Math.PI * 2;
			float cx = alvoEscondido ? x : alvoX, cz = alvoEscondido ? z : alvoZ;
			float raio = alvoEscondido ? 15 + random.nextFloat() * 25
					: Regras.OBSERVA_PERTO + random.nextFloat() * (Regras.OBSERVA_LONGE - Regras.OBSERVA_PERTO);
			float px = cx + raio * (float) Math.cos(angulo), pz = cz + raio * (float) Math.sin(angulo);
			if (lugarBom(px, pz) && planejar(px, pz)) return;
		}
		caminho = null;
		parado = 2;
	}

	/** Corre para longe do jogador (uns 60 m, para o lado oposto ao dele), sumindo na neblina. */
	private void fugir() {
		mudarPara(Estado.FUGINDO);
		caminho = null;
		double oposto = Math.atan2(z - alvoZ, x - alvoX);
		for (int tentativa = 0; tentativa < 25; tentativa++) {
			double angulo = oposto + (random.nextDouble() - 0.5) * (1.2 + tentativa * 0.15);
			float px = alvoX + Regras.FOGE_ATE * (float) Math.cos(angulo), pz = alvoZ + Regras.FOGE_ATE * (float) Math.sin(angulo);
			if (lugarBom(px, pz) && planejar(px, pz)) return;
		}
	}

	// ---------- percepção ----------

	/** Vê o jogador: perto demais (sente), ou no cone de visão, ao alcance e sem tronco nem parede no meio. */
	boolean ve() {
		if (alvoEscondido) return false;
		float dx = alvoX - x, dz = alvoZ - z;
		float dist = (float) Math.hypot(dx, dz);
		if (dist <= Regras.JUBILADO_SENTE) return true;
		float alcance = alvoLanterna ? Regras.JUBILADO_VISAO_LANTERNA : Regras.JUBILADO_VISAO;
		if (dist > alcance) return false;
		float fx = (float) Math.cos(guinada), fz = -(float) Math.sin(guinada);
		float cosseno = (dx * fx + dz * fz) / dist;
		if (cosseno < (float) Math.cos(Math.toRadians(Regras.JUBILADO_ANGULO_VISAO))) return false;
		return colisao.linhaLivre(x, z, alvoX, alvoZ, 0.1f);
	}

	/** Ouve o jogador: correndo (de longe) ou andando (só de perto). Parado, não faz barulho. */
	boolean ouve() {
		if (alvoEscondido) return false;
		float dist = (float) Math.hypot(alvoX - x, alvoZ - z);
		return alvoCorrendo && dist <= Regras.JUBILADO_OUVE_CORRIDA || alvoAndando && dist <= Regras.JUBILADO_OUVE_PASSOS;
	}

	/** Um tiro em (tx, tz): se ele ouvir, vai ver o que foi (caçando, continua caçando; na trégua, nem liga). */
	public void ouvirTiro(float tx, float tz) {
		if (!ativo || papel != Papel.CACADOR || estado == Estado.CACANDO || estado == Estado.CAIDO
				|| estado == Estado.ATACANDO) return;
		if (Math.hypot(tx - x, tz - z) <= Regras.JUBILADO_OUVE_TIRO) {
			desconfiar(tx, tz);
		}
	}

	// ---------- mudanças de estado ----------

	private void mudarPara(Estado novo) {
		estado = novo;
		tempoNoEstado = 0;
		refX = x;
		refZ = z;
		tempoRef = 0;
	}

	private void cacar() {
		farejando = false;
		mudarPara(Estado.CACANDO);
		ultimoX = alvoX;
		ultimoZ = alvoZ;
		semNoticia = 0;
		caminho = null;
		eventos.viu(this);
	}

	private void desconfiar(float px, float pz) {
		mudarPara(Estado.DESCONFIADO);
		ultimoX = px;
		ultimoZ = pz;
		if (!planejar(px, pz)) caminho = null;
	}

	private void procurar() {
		mudarPara(Estado.PROCURANDO);
		caminho = null;
		boolean temProvocacao = animador != null && nome("taunt") != null;
		tocar(temProvocacao ? "taunt" : "stand", false);
	}

	private void atacar() {
		mudarPara(Estado.ATACANDO);
		caminho = null;
		guinada = guinadaPara(alvoX - x, alvoZ - z);
		if (animador != null) {
			String ataque = nome("attack");
			if (ataque != null) {
				animador.definirVelocidade(1f);
				animador.tocar(ataque, false, () -> ataqueTerminou = true);
			} else {
				ataqueTerminou = true;
			}
		}
		eventos.atacou(this);
	}

	private void cair() {
		quedas++;
		mudarPara(Estado.CAIDO);
		caminho = null;
		velocidade = 0;
		animacaoTerminou = false;
		if (animador != null) {
			String morte = nome("death");
			if (morte != null) {
				animador.definirVelocidade(1f);
				animador.tocar(morte, false, () -> animacaoTerminou = true);
			}
		}
		eventos.caiu(this);
	}

	/** Leva dano: com bagos suficientes no mesmo tiro, cai; com menos, só fica sabendo de onde veio. */
	@Override
	public void receberDano(float valor) {
		if (!atingivel()) return;
		marcaDano = 1;
		if (valor >= BAGOS_PARA_CAIR * Regras.DANO_BAGO) {
			cair();
		} else if (papel == Papel.OBSERVADOR) {
			fugir();
		} else if (estado != Estado.CACANDO) {
			cacar(); // levou um bago: sabe onde o jogador está
		}
	}

	public boolean atingivel() {
		return ativo && estado != Estado.CAIDO && estado != Estado.ATACANDO;
	}

	@Override
	public float intersectaRaio(float[] origem, float[] direcao) {
		if (!atingivel()) return -1;
		float[] o = { origem[0] - x, origem[1] - y, origem[2] - z };
		return DragonKnight.raioCaixa(o, direcao, caixa); // a caixa é simétrica: o giro não importa
	}

	// ---------- andar ----------

	/** Longe do jogador (a neblina o esconde) e sem respiro: hora de se aproximar rápido. */
	private boolean longeDoJogador() {
		return !alvoEscondido && respiro <= 0 && Math.hypot(alvoX - x, alvoZ - z) > Regras.JUBILADO_LONGE;
	}

	/**
	 * Sorteia o próximo destino: quase sempre perto do jogador (ele ronda; longe, sempre), ou em
	 * volta de si. No respiro depois de uma caçada, e com o jogador escondido, só em volta de si.
	 */
	private void sortearDestino() {
		for (int tentativa = 0; tentativa < 25; tentativa++) {
			boolean pertoDoJogador = !alvoEscondido && respiro <= 0
					&& (longeDoJogador() || random.nextFloat() < Regras.JUBILADO_CHANCE_RONDAR);
			float cx = pertoDoJogador ? alvoX : x, cz = pertoDoJogador ? alvoZ : z;
			float raio = pertoDoJogador ? 12 + random.nextFloat() * 25 : 20 + random.nextFloat() * 40;
			float[] prova = provaMaisPerto();
			if (pertoDoJogador && prova != null && random.nextFloat() < Regras.JUBILADO_CHANCE_EMBOSCADA) {
				cx = prova[0]; // espera perto da prova que o jogador vai buscar
				cz = prova[1];
				raio = 8 + random.nextFloat() * 12;
			}
			double angulo = random.nextDouble() * Math.PI * 2;
			float dx = cx + raio * (float) Math.cos(angulo), dz = cz + raio * (float) Math.sin(angulo);
			if (lugarBom(dx, dz) && planejar(dx, dz)) return;
		}
		caminho = null;
		parado = 2; // nada serviu: tenta de novo daqui a pouco
	}

	/** Das provas que faltam, a mais perto do jogador (null se não falta nenhuma). */
	private float[] provaMaisPerto() {
		float[] melhor = null;
		double menor = Double.MAX_VALUE;
		for (float[] p : provasQueFaltam) {
			double d = Math.hypot(p[0] - alvoX, p[1] - alvoZ);
			if (d < menor) {
				menor = d;
				melhor = p;
			}
		}
		return melhor;
	}

	/** Um lugar onde ele cabe: bom e sem tronco encostado. */
	private boolean livre(float px, float pz) {
		return lugarBom(px, pz) && !colisao.colide(px, pz, RAIO + 0.2f);
	}

	/** Um lugar onde ele pode estar: caminhável e longe da Univali. */
	private boolean lugarBom(float px, float pz) {
		return grade.caminhavelEm(px, pz)
				&& (univali == null || univali.distancia(px, pz) >= Regras.JUBILADO_LONGE_UNIVALI + 2);
	}

	/** O caminho do A* até (px, pz). Devolve false se não há caminho. */
	boolean planejar(float px, float pz) {
		int[] de = celulaCaminhavelPerto(x, z), para = celulaCaminhavelPerto(px, pz);
		if (de == null || para == null) return false;
		AEstrela.Caminho c = AEstrela.buscar(grade, de[0], de[1], para[0], para[1], 2f);
		if (c == null) return false;
		caminho = new ArrayList<>();
		for (int[] celula : c.celulas()) {
			caminho.add(new float[] { grade.centro(celula[0]), grade.centro(celula[1]) });
		}
		if (grade.caminhavelEm(px, pz)) caminho.set(caminho.size() - 1, new float[] { px, pz });
		proximo = Math.min(1, caminho.size() - 1);
		return true;
	}

	/** A célula caminhável mais perto de (px, pz), procurando até 3 células em volta (ou null). */
	private int[] celulaCaminhavelPerto(float px, float pz) {
		return celulaCaminhavelPerto(px, pz, 3);
	}

	/** O mesmo, procurando até "raio" células em volta. */
	private int[] celulaCaminhavelPerto(float px, float pz, int raio) {
		int ci = grade.celulaDe(px), cj = grade.celulaDe(pz);
		for (int r = 0; r <= raio; r++) {
			for (int dj = -r; dj <= r; dj++) {
				for (int di = -r; di <= r; di++) {
					if (Math.max(Math.abs(di), Math.abs(dj)) != r) continue; // só o anel de raio r
					if (grade.caminhavel(ci + di, cj + dj)) return new int[] { ci + di, cj + dj };
				}
			}
		}
		return null;
	}

	/**
	 * Anda pelo caminho. Pula pontos que já dá para ver em linha reta (o caminho do A* faz zigue-
	 * zague de célula em célula). Devolve true quando chega ao fim.
	 */
	private boolean seguirCaminho(float dt, float velocidadeMaxima) {
		for (int k = 0; k < 3 && proximo + 1 < caminho.size(); k++) {
			float[] depois = caminho.get(proximo + 1);
			if (Math.hypot(depois[0] - x, depois[1] - z) > 15 || !colisao.linhaLivre(x, z, depois[0], depois[1], 0.3f)) break;
			proximo++;
		}
		float[] p = caminho.get(proximo);
		if (Math.hypot(p[0] - x, p[1] - z) < 0.8f) {
			proximo++;
			if (proximo >= caminho.size()) {
				caminho = null;
				return true;
			}
			p = caminho.get(proximo);
		}
		andarPara(dt, p[0], p[1], velocidadeMaxima);
		return false;
	}

	/** Um passo na direção de (px, pz), escorregando nos troncos e sem entrar em água funda, morro ou perto da Univali. */
	private void andarPara(float dt, float px, float pz, float velocidadeMaxima) {
		float dx = px - x, dz = pz - z;
		float dist = (float) Math.hypot(dx, dz);
		if (dist < 0.05f) {
			frear(dt);
			return;
		}
		virarPara(guinadaPara(dx, dz), dt);
		velocidade = Math.min(velocidadeMaxima, velocidade + ACELERACAO * dt);
		if (velocidade > velocidadeMaxima) velocidade = velocidadeMaxima;
		float passo = Math.min(dist, velocidade * dt);
		float nx = x + dx / dist * passo, nz = z + dz / dist * passo;
		float[] r = colisao.resolver(nx, nz, RAIO);
		nx = r[0];
		nz = r[1];
		if (!lugarBom(nx, nz) && lugarBom(x, z)) {
			// não pode ir para lá: tenta só em x ou só em z, escorregando ao longo do obstáculo
			if (lugarBom(nx, z)) nz = z;
			else if (lugarBom(x, nz)) nx = x;
			else { nx = x; nz = z; }
		}
		float andou = (float) Math.hypot(nx - x, nz - z);
		x = nx;
		z = nz;
		y = chao.alturaEm(x, z);
		andouDesdeOPasso += andou;
		if (andouDesdeOPasso >= PASSADA) {
			andouDesdeOPasso = 0;
			eventos.passo(this);
		}
	}

	private void frear(float dt) {
		velocidade = Math.max(0, velocidade - ACELERACAO * 2 * dt);
	}

	/** Gira em direção a "alvo" sem passar de GIRO radianos por segundo, pelo lado mais curto. */
	private void virarPara(float alvo, float dt) {
		float diferenca = (float) Math.atan2(Math.sin(alvo - guinada), Math.cos(alvo - guinada));
		float maximo = GIRO * dt;
		guinada += Math.max(-maximo, Math.min(maximo, diferenca));
	}

	/** Em 1,5 s, andou menos de um terço do esperado? Então está preso (entre troncos, numa quina). */
	private boolean empacou(float dt) {
		tempoRef += dt;
		if (tempoRef < 1.5f) return false;
		boolean preso = Math.hypot(x - refX, z - refZ) < 0.3f * Regras.JUBILADO_VAGANDO * tempoRef && velocidade > 0.3f;
		empacadas = preso ? empacadas + 1 : 0;
		if (empacadas >= 2) {
			desatolar();
			empacadas = 0;
		}
		refX = x;
		refZ = z;
		tempoRef = 0;
		return preso;
	}

	/** Preso de vez (entre troncos): passa para um ponto livre a até 8 m. */
	private void desatolar() {
		for (int tentativa = 0; tentativa < 40; tentativa++) {
			double angulo = random.nextDouble() * Math.PI * 2;
			float r = 2 + random.nextFloat() * 6;
			float px = x + r * (float) Math.cos(angulo), pz = z + r * (float) Math.sin(angulo);
			if (livre(px, pz)) {
				x = px;
				z = pz;
				y = chao.alturaEm(x, z);
				caminho = null;
				return;
			}
		}
	}

	/** Farejando e já perto (45 m), sussurra de tempos em tempos: o jogador ouve que ele está vindo. */
	private void sussurrar(float dt, float dist) {
		proximoSussurro -= dt;
		if (proximoSussurro <= 0 && dist <= Regras.JUBILADO_SUSSURRA_DE) {
			proximoSussurro = 5 + random.nextFloat() * 4;
			eventos.sussurrou(this);
		}
	}

	private void grunhir(float dt) {
		proximoGrunhido -= dt;
		if (proximoGrunhido <= 0) {
			proximoGrunhido = 9 + random.nextFloat() * 8;
			eventos.grunhiu(this);
		}
	}

	// ---------- animação ----------

	/** Parado, a animação de ficar em pé; andando, a de correr, mais lenta ou mais rápida conforme a velocidade. */
	private void animarAndando() {
		if (animador == null) return;
		if (estado == Estado.PROCURANDO && !animador.terminou() && !animador.emLoop()) return; // a provocação até o fim
		if (velocidade < 0.2f) {
			tocar("stand", true);
			animador.definirVelocidade(1f);
		} else {
			tocar("run", true);
			animador.definirVelocidade(Math.max(0.35f, velocidade / VELOCIDADE_DA_CORRIDA));
		}
	}

	/** Toca a primeira animação cujo nome começa com o prefixo (se não for a que já está tocando). */
	private void tocar(String prefixo, boolean loop) {
		if (animador == null) return;
		String nome = nome(prefixo);
		if (nome != null && !nome.equals(animador.nomeAtual())) animador.tocar(nome, loop);
	}

	private String nome(String prefixo) {
		if (animador == null) return null;
		for (String n : animador.nomes()) {
			if (n.startsWith(prefixo)) return n;
		}
		return null;
	}

	/** A guinada que aponta a frente (+x) do modelo para a direção (dx, dz). */
	static float guinadaPara(float dx, float dz) {
		return DragonKnight.guinadaPara(dx, dz);
	}

	// ---------- consultas ----------

	public Estado estado() {
		return estado;
	}

	public float tempoNoEstado() {
		return tempoNoEstado;
	}

	public float velocidade() {
		return velocidade;
	}

	/** De 1 (acabou de levar o tiro) a 0: o tom vermelho no modelo. */
	public float marcaDano() {
		return marcaDano;
	}

	/** A animação de ataque terminou: hora do susto e da derrota. */
	public boolean ataqueTerminou() {
		return ataqueTerminou;
	}

	public Animador animador() {
		return animador;
	}

	public float[] caixa() {
		return caixa;
	}

	/** Quantas vezes ele já foi derrubado na partida. */
	public int quedas() {
		return quedas;
	}

	/** Para o painel de depuração: quanto falta para desistir (caçando) ou procurar (procurando). */
	public float semNoticia() {
		return semNoticia;
	}

	/** Segundos de respiro que ainda faltam (0 = rondando o jogador). */
	public float respiro() {
		return respiro;
	}

	/** Depuração: aparece a 20 m do jogador, atrás dele, já desconfiado do lugar onde ele está. */
	public void forcarPerto(float frenteX, float frenteZ) {
		float t = (float) Math.hypot(frenteX, frenteZ);
		if (t < 1e-4f) { frenteX = 1; frenteZ = 0; t = 1; }
		for (int tentativa = 0; tentativa < 30; tentativa++) {
			double angulo = Math.atan2(-frenteZ, -frenteX) + (random.nextDouble() - 0.5) * 1.5;
			float px = alvoX + 20 * (float) Math.cos(angulo), pz = alvoZ + 20 * (float) Math.sin(angulo);
			if (lugarBom(px, pz)) {
				x = px;
				z = pz;
				y = chao.alturaEm(x, z);
				desconfiar(alvoX, alvoZ);
				return;
			}
		}
	}
}
