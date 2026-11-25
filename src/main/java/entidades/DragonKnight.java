package entidades;

import java.util.List;
import java.util.Random;

import grafos.PlantaInterna;
import jogo.Regras;
import md2.Animador;
import obj.Object3D;

/**
 * O Dragon Knight: um cavaleiro montado num dragão, o chefão da volta. Ele dorme até o jogador
 * voltar para a Univali com as três provas; aí acorda lá dentro e vem atrás dele pelos corredores.
 * Para vencer, é preciso acertá-lo três vezes com tiros bons (3 bagos ou mais no corpo), e ele não
 * facilita: corre em zigue-zague e, quando percebe a mira, pula para o lado. Só lógica (não usa
 * OpenGL): o desenho fica em render.DesenhoMonstro, e os sons e o sangue na TelaJogo, avisada
 * pelos Eventos.
 *
 * Máquina de estados:
 *   ADORMECIDO  antes da volta com as provas: não existe.
 *   SURGINDO    aparece numa das pontas (o fundo do saguão, as pontas do corredor), grita e
 *               provoca por 1,4 s. Já dá para atirar.
 *   AVANCANDO   corre até o jogador pelo caminho do A* (dentro do prédio), em zigue-zague.
 *   DESVIANDO   o jogador mirou nele: um pulo rápido para o lado (0,35 s).
 *   CAMBALEANDO levou um golpe: a animação de dor, empurrado para trás, sangrando (os tiros
 *               agora acertam, mas não contam como golpe, nem recuando: ele precisa voltar).
 *   RECUANDO    corre para longe, para a ponta mais distante, até sair da vista do jogador.
 *   ESCONDIDO   sumiu; daqui a 2,5 s surge de novo, por outro lado (outra ponta, fora da vista).
 *   ATACANDO    alcançou o jogador: ataca; no fim da animação, a partida está perdida.
 *   MORRENDO    o terceiro golpe: a animação de morte; depois fica no chão (MORTO).
 *
 * Herda de Object3D (do projeto original): x, y, z e vivo são reaproveitados. A frente do modelo
 * MD2 é o eixo +x; "guinada" gira o modelo em volta do eixo y.
 */
public class DragonKnight extends Object3D implements Atingivel {

	public enum Estado { ADORMECIDO, SURGINDO, AVANCANDO, DESVIANDO, CAMBALEANDO, RECUANDO, ESCONDIDO, ATACANDO, MORRENDO, MORTO }

	/** Quem quer saber o que o monstro faz (sons, sangue, música, susto). Tudo opcional. */
	public interface Eventos {
		default void surgiu(DragonKnight monstro) {}
		/** Levou um golpe (o número dele: 1, 2 ou 3). */
		default void golpeado(DragonKnight monstro, int golpes) {}
		default void desviou(DragonKnight monstro) {}
		default void galope(DragonKnight monstro) {}
		default void morreu(DragonKnight monstro) {}
		default void atacou(DragonKnight monstro) {}
	}

	/**
	 * A caixa de acerto sem o modelo MD2 (o substituto): o tamanho do dragão do Dragon Knight,
	 * {xMin, yMin, zMin, xMax, yMax, zMax} em metros, com a frente em +x.
	 */
	public static final float[] CAIXA_PADRAO = { -2.85f, 0f, -1.15f, 2.0f, 2.2f, 1.1f };
	/** Sem o MD2, quanto duram a dor, a morte e o ataque (o que seriam as animações). */
	private static final float DURACAO_DOR = 0.7f, DURACAO_MORTE = 1.0f, DURACAO_ATAQUE = 0.8f;
	private static final float DURACAO_DESVIO = 0.35f, DURACAO_MARCA_DANO = 0.3f;
	/** Empurrado para trás pelo golpe (m/s), procurando ficar fora da vista (s) e o máximo recuando (s). */
	private static final float EMPURRAO = 2.5f, RECUO_MINIMO = 1.0f, RECUO_MAXIMO = 6f;
	/** De quanto em quanto ele refaz o caminho até o jogador, e a distância entre duas batidas de casco. */
	private static final float REPLANEJAR = 0.5f, PASSADA = 2.2f;

	private final Animador animador;
	private final float[] caixa;
	private final Random random;
	private Eventos eventos = new Eventos() {};
	private PlantaInterna planta;

	private Estado estado = Estado.ADORMECIDO;
	private float tempoNoEstado = 0;
	private int golpes = 0;
	private float marcaDano = 0;
	private boolean ataqueTerminou = false, animacaoTerminou = false;

	// o jogador (os olhos) e para onde ele olha (no chão, unitário)
	private float alvoX, alvoY, alvoZ, frenteX = 1, frenteZ = 0;

	// andar
	private List<float[]> caminho;
	private int proximo;
	private float replanejar, fase, andouDesdeOGalope;
	private float esperaDesvio, duracaoDesvio;
	private float ladoX, ladoZ; // a direção do pulo para o lado, ou do empurrão do golpe
	private float[] ultimaPonta;

	/** Rotação do modelo em volta do eixo y, em radianos (0 = olhando para +x). */
	public float guinada = 0;
	/** A altura do chão onde ele anda (o piso da Univali). */
	public float alturaChao = 0;

	public DragonKnight(Animador animador, float[] caixa, long semente) {
		super(0, 0, 0);
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
		float t = (float) Math.hypot(frenteX, frenteZ);
		if (t > 1e-4f) {
			this.frenteX = frenteX / t;
			this.frenteZ = frenteZ / t;
		}
	}

	/**
	 * Acorda dentro do prédio: a planta diz por onde ele anda e de onde ele surge. Ele aparece na
	 * primeira ponta (o fundo do saguão, de frente para quem entra).
	 */
	public void despertar(PlantaInterna planta, float alturaChao) {
		if (estado != Estado.ADORMECIDO) return;
		this.planta = planta;
		this.alturaChao = alturaChao;
		surgir(planta.pontas().get(0));
	}

	// ---------- simulação ----------

	@Override
	public void SimulaSe(long diftime) {
		if (estado == Estado.ADORMECIDO) return;
		float dt = diftime / 1000f;
		tempoNoEstado += dt;
		replanejar -= dt;
		esperaDesvio -= dt;
		marcaDano = Math.max(0, marcaDano - dt / DURACAO_MARCA_DANO);
		if (animador != null) animador.atualizar(dt);

		switch (estado) {
		case SURGINDO -> {
			virarParaOAlvo();
			if (tempoNoEstado >= Regras.CAVALEIRO_SURGINDO) avancar();
		}
		case AVANCANDO -> {
			float dist = (float) Math.hypot(alvoX - x, alvoZ - z);
			if (dist <= Regras.CAVALEIRO_ALCANCE) {
				atacar();
				return;
			}
			if (esperaDesvio <= 0 && dist > 4 && dist < 25 && mirandoNele(dist) && desviar()) return;
			if (caminho == null || replanejar <= 0) {
				replanejar = REPLANEJAR;
				planejar(alvoX, alvoZ);
			}
			fase += dt * Regras.CAVALEIRO_ZIGUE_RITMO * 2 * (float) Math.PI;
			seguirCaminho(dt, Regras.CAVALEIRO_AVANCA, true);
		}
		case DESVIANDO -> {
			mover(ladoX * Regras.CAVALEIRO_DESVIA * dt, ladoZ * Regras.CAVALEIRO_DESVIA * dt);
			virarParaOAlvo();
			if (tempoNoEstado >= duracaoDesvio) avancar();
		}
		case CAMBALEANDO -> {
			float empurrao = EMPURRAO * Math.max(0, 1 - tempoNoEstado / DURACAO_DOR);
			mover(ladoX * empurrao * dt, ladoZ * empurrao * dt);
			if (animador == null ? tempoNoEstado >= DURACAO_DOR : animacaoTerminou) recuar();
		}
		case RECUANDO -> {
			boolean chegou = caminho == null || seguirCaminho(dt, Regras.CAVALEIRO_RECUA, false);
			boolean foraDaVista = !planta.ve(alvoX, alvoZ, x, z);
			if (tempoNoEstado >= RECUO_MINIMO && (foraDaVista || chegou) || tempoNoEstado >= RECUO_MAXIMO) {
				mudarPara(Estado.ESCONDIDO); // sumiu na esquina: vai surgir por outro lado
				vivo = false;
			}
		}
		case ESCONDIDO -> {
			if (tempoNoEstado >= Regras.CAVALEIRO_ESCONDIDO) surgir(outraPonta());
		}
		case ATACANDO -> {
			virarParaOAlvo();
			if (animador == null && tempoNoEstado >= DURACAO_ATAQUE) ataqueTerminou = true;
		}
		case MORRENDO -> {
			if (animador == null ? tempoNoEstado >= DURACAO_MORTE : animacaoTerminou) mudarPara(Estado.MORTO);
		}
		default -> { }
		}
	}

	private void mudarPara(Estado novo) {
		estado = novo;
		tempoNoEstado = 0;
	}

	// ---------- as ações ----------

	private void surgir(float[] ponta) {
		ultimaPonta = ponta;
		x = ponta[0];
		z = ponta[1];
		y = alturaChao;
		vivo = true;
		caminho = null;
		virarParaOAlvo();
		mudarPara(Estado.SURGINDO);
		tocar(temAnimacao("taunt") ? "taunt" : "stand", true, null);
		eventos.surgiu(this);
	}

	private void avancar() {
		mudarPara(Estado.AVANCANDO);
		caminho = null;
		tocar("run", true, null);
	}

	/** A mira do jogador está nele (a poucos graus do centro) e não há parede no meio. */
	boolean mirandoNele(float dist) {
		float cosseno = ((x - alvoX) * frenteX + (z - alvoZ) * frenteZ) / dist;
		return cosseno > (float) Math.cos(Math.toRadians(Regras.CAVALEIRO_MIRA)) && planta.ve(alvoX, alvoZ, x, z);
	}

	/**
	 * Pula para o lado onde houver espaço (perpendicular à direção do jogador): o pulo inteiro, ou
	 * mais curto se não couber (no corredor de 4 m sobra pouco mais de 1 m para cada lado).
	 * Devolve false se não há espaço nenhum.
	 */
	private boolean desviar() {
		float dx = alvoX - x, dz = alvoZ - z, d = (float) Math.hypot(dx, dz);
		float px = -dz / d, pz = dx / d;
		float alcance = Regras.CAVALEIRO_DESVIA * DURACAO_DESVIO;
		for (float fracao : new float[] { 1f, 0.65f, 0.4f }) {
			float lado = random.nextBoolean() ? 1 : -1;
			for (int tentativa = 0; tentativa < 2; tentativa++, lado = -lado) {
				if (planta.livre(x + px * lado * alcance * fracao, z + pz * lado * alcance * fracao)) {
					ladoX = px * lado;
					ladoZ = pz * lado;
					duracaoDesvio = DURACAO_DESVIO * fracao;
					esperaDesvio = Regras.CAVALEIRO_ESPERA_DESVIO;
					mudarPara(Estado.DESVIANDO);
					eventos.desviou(this);
					return true;
				}
			}
		}
		esperaDesvio = 0.4f; // encurralado: tenta de novo daqui a pouco
		return false;
	}

	private void atacar() {
		caminho = null;
		virarParaOAlvo();
		mudarPara(Estado.ATACANDO);
		tocar("attack", false, () -> ataqueTerminou = true);
		eventos.atacou(this);
	}

	/** Depois do golpe: corre para a ponta mais longe do jogador, para sair da vista dele. */
	private void recuar() {
		float[] longe = null;
		double maior = -1;
		for (float[] p : planta.pontas()) {
			double d = Math.hypot(p[0] - alvoX, p[1] - alvoZ);
			if (d > maior) {
				maior = d;
				longe = p;
			}
		}
		mudarPara(Estado.RECUANDO);
		planejar(longe[0], longe[1]);
		tocar("run", true, null);
	}

	/**
	 * Por onde surgir de novo: uma ponta diferente da última, fora da vista do jogador e a pelo
	 * menos 12 m dele; se nenhuma serve, a mais longe dele que não seja a última.
	 */
	private float[] outraPonta() {
		float[] melhor = null;
		double maior = -1;
		for (float[] p : planta.pontas()) {
			if (p == ultimaPonta && planta.pontas().size() > 1) continue;
			double d = Math.hypot(p[0] - alvoX, p[1] - alvoZ);
			if (d >= 12 && !planta.ve(alvoX, alvoZ, p[0], p[1])) return p;
			if (d > maior) {
				maior = d;
				melhor = p;
			}
		}
		return melhor;
	}

	private void cambalear() {
		float dx = x - alvoX, dz = z - alvoZ, d = Math.max(1e-3f, (float) Math.hypot(dx, dz));
		ladoX = dx / d; // empurrado para longe do jogador
		ladoZ = dz / d;
		animacaoTerminou = false;
		mudarPara(Estado.CAMBALEANDO);
		tocar("pain", false, () -> animacaoTerminou = true);
	}

	private void morrer() {
		animacaoTerminou = false;
		mudarPara(Estado.MORRENDO);
		tocar("death", false, () -> animacaoTerminou = true);
		eventos.morreu(this);
	}

	// ---------- andar ----------

	private void planejar(float px, float pz) {
		caminho = planta.caminho(x, z, px, pz);
		proximo = caminho != null ? Math.min(1, caminho.size() - 1) : 0;
	}

	/**
	 * Anda pelo caminho, pulando pontos que já dá para ver em linha reta. Avançando, soma o
	 * zigue-zague: uma velocidade para o lado que vai e volta. Devolve true quando chega ao fim.
	 */
	private boolean seguirCaminho(float dt, float velocidade, boolean zigue) {
		if (caminho == null) return true;
		while (proximo + 1 < caminho.size()) {
			float[] depois = caminho.get(proximo + 1);
			if (!planta.linhaLivre(x, z, depois[0], depois[1])) break;
			proximo++;
		}
		float[] p = caminho.get(proximo);
		float dx = p[0] - x, dz = p[1] - z, d = (float) Math.hypot(dx, dz);
		if (d < 0.5f) {
			if (++proximo >= caminho.size()) {
				caminho = null;
				return true;
			}
			return false;
		}
		dx /= d;
		dz /= d;
		guinada = guinadaPara(dx, dz);
		float passo = Math.min(d, velocidade * dt);
		float mx = dx * passo, mz = dz * passo;
		if (zigue) { // o lado: perpendicular ao caminho, indo e voltando
			float lateral = (float) Math.cos(fase) * Regras.CAVALEIRO_ZIGUE * Regras.CAVALEIRO_ZIGUE_RITMO * 2
					* (float) Math.PI * dt;
			mx += -dz * lateral;
			mz += dx * lateral;
		}
		mover(mx, mz);
		return false;
	}

	/** Anda (mx, mz), se couber; senão tenta só em x ou só em z (escorrega na parede). */
	private void mover(float mx, float mz) {
		float nx = x + mx, nz = z + mz;
		if (!planta.livre(nx, nz)) {
			if (planta.livre(nx, z)) nz = z;
			else if (planta.livre(x, nz)) nx = x;
			else return;
		}
		andouDesdeOGalope += (float) Math.hypot(nx - x, nz - z);
		x = nx;
		z = nz;
		if (andouDesdeOGalope >= PASSADA) {
			andouDesdeOGalope = 0;
			eventos.galope(this);
		}
	}

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

	private boolean temAnimacao(String prefixo) {
		return animador != null && animador.nomes().stream().anyMatch(n -> n.startsWith(prefixo));
	}

	// ---------- tiro ----------

	/** Dá para acertá-lo: em cena, e não atacando nem morrendo. */
	public boolean atingivel() {
		return estado == Estado.SURGINDO || estado == Estado.AVANCANDO || estado == Estado.DESVIANDO
				|| estado == Estado.CAMBALEANDO || estado == Estado.RECUANDO;
	}

	/**
	 * Pode levar um golpe agora: surgindo ou vindo para cima. Cambaleando e recuando, os tiros
	 * acertam (e ele sangra), mas não contam: cada golpe exige que ele volte e venha de novo.
	 */
	public boolean vulneravel() {
		return estado == Estado.SURGINDO || estado == Estado.AVANCANDO || estado == Estado.DESVIANDO;
	}

	/** Um tiro com 3 bagos ou mais no corpo, vindo ele para cima, é um golpe: o terceiro o derruba. */
	@Override
	public void receberDano(float valor) {
		if (!atingivel()) return;
		marcaDano = 1;
		if (valor < Regras.DANO_DE_GOLPE || !vulneravel()) return;
		golpes++;
		eventos.golpeado(this, golpes);
		if (golpes >= Regras.GOLPES_PARA_MATAR) morrer();
		else cambalear();
	}

	/**
	 * Distância ao longo do raio até a caixa do corpo inteiro (o dragão com o cavaleiro), ou -1
	 * se não acerta. A direção deve ser unitária.
	 *
	 * O raio é levado para o espaço do modelo (desfazendo a posição e a guinada), onde a caixa
	 * fica alinhada aos eixos; lá vale o teste das "fatias" (slabs): para cada eixo, o trecho do
	 * raio entre os dois planos da caixa; o raio acerta se os três trechos se cruzam.
	 */
	@Override
	public float intersectaRaio(float[] origem, float[] direcao) {
		if (!atingivel()) return -1;
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

	/** Derruba na hora (depuração): conta os golpes que faltam. */
	public void matar() {
		if (!atingivel()) return;
		golpes = Regras.GOLPES_PARA_MATAR;
		morrer();
	}

	// ---------- consultas ----------

	public Estado estado() {
		return estado;
	}

	/** Aparece na tela? (Acordado e não escondido; morto, o corpo fica.) */
	public boolean visivel() {
		return estado != Estado.ADORMECIDO && estado != Estado.ESCONDIDO;
	}

	/** Acordou e ainda não foi derrubado: a luta está acontecendo. */
	public boolean lutando() {
		return estado != Estado.ADORMECIDO && estado != Estado.MORRENDO && estado != Estado.MORTO;
	}

	public float tempoNoEstado() {
		return tempoNoEstado;
	}

	public int golpes() {
		return golpes;
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
		return caixa.clone();
	}
}
