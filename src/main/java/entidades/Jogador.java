package entidades;

import mundo.ConsultaAltura;
import mundo.GradeColisao;
import obj.Object3D;
import render.Camera;

/**
 * O jogador em primeira pessoa.
 *
 * Herda do Object3D do projeto original: x, y, z são a posição dos pés (y = altura do chão)
 * e o SimulaSe(diftime) faz um passo da simulação. A câmera fica nos olhos, 1,7 m acima,
 * com o balanço de cabeça somado.
 *
 * A entrada (teclado e mouse) não é lida aqui: a tela chama comandar() e girar() antes
 * do SimulaSe. Assim toda a física pode ser testada sem janela.
 *
 * Regras do movimento:
 *  - anda a 3,5 m/s e corre a 6 m/s, com aceleração e frenagem suaves;
 *  - correr gasta estamina (5 s de corrida); ela volta em 8 s depois de 1 s sem correr;
 *  - não entra em água com mais de 0,4 m de fundo nem sobe declives acima de 40 graus:
 *    nesses casos tenta andar só em x ou só em z, e assim desliza ao longo do obstáculo;
 *  - fica a pelo menos 10 m da borda do mapa;
 *  - é um círculo de 0,35 m que desliza em volta dos troncos (GradeColisao).
 */
public class Jogador extends Object3D {

	public static final float VELOCIDADE_ANDANDO = 3.5f;   // m/s
	public static final float VELOCIDADE_CORRENDO = 6.0f;  // m/s
	private static final float ACELERACAO = 10f;           // quanto maior, mais rápido chega à velocidade
	private static final float FRENAGEM = 12f;

	public static final float ALTURA_OLHOS = 1.7f;
	private static final float SUAVIZACAO_OLHOS = 10f;     // os olhos acompanham o chão sem tremer
	public static final float RAIO = 0.35f;

	public static final float PROFUNDIDADE_MAXIMA = 0.4f;  // m de água
	public static final float DECLIVE_MAXIMO = 40f;        // graus
	public static final float MARGEM_BORDA = 10f;          // m

	public static final float DURACAO_ESTAMINA = 5f;       // s de corrida com estamina cheia
	public static final float RECUPERACAO_ESTAMINA = 8f;   // s para encher de novo
	public static final float ESPERA_RECUPERACAO = 1f;     // s sem correr antes de começar a recuperar
	public static final float ESTAMINA_OFEGANTE = 0.3f;    // abaixo disso, respiração ofegante
	private static final float ESTAMINA_PARA_VOLTAR = 0.3f; // depois de esgotar, só corre de novo com isso

	private static final float PASSO_ANDANDO = 0.6f;       // m entre dois passos
	private static final float PASSO_CORRENDO = 0.8f;
	private static final float BALANCO_ALTURA = 0.045f;    // m, na velocidade de corrida
	private static final float BALANCO_LADO = 0.03f;

	private final ConsultaAltura chao;
	private final GradeColisao colisao;
	private final Camera camera;

	// comandos do quadro (vindos da tela)
	private float comandoFrente, comandoLado;
	private boolean querCorrer;

	// estado
	private float vx, vz;              // velocidade no plano, m/s
	private float alturaOlhos;         // altura suavizada dos olhos
	private float estamina = 1;        // de 0 a 1
	private float semCorrer = 0;       // s desde a última corrida
	private boolean esgotado = false;
	private boolean correndo = false;
	private float velocidadeReal = 0;  // m/s, pelo deslocamento de fato
	private float fasePasso = 0;       // avança pi a cada passo
	private int passosNoQuadro = 0;

	public Jogador(float x, float z, ConsultaAltura chao, GradeColisao colisao) {
		super(x, chao.alturaEm(x, z), z);
		this.chao = chao;
		this.colisao = colisao;
		this.raio = RAIO;
		this.alturaOlhos = y + ALTURA_OLHOS;
		this.camera = new Camera(x, alturaOlhos, z, 0, 0);
	}

	/**
	 * Comandos de movimento do quadro, de -1 a 1: frente (W = 1, S = -1) e lado
	 * (D = 1, A = -1). correr = Shift segurado.
	 */
	public void comandar(float frente, float lado, boolean correr) {
		comandoFrente = frente;
		comandoLado = lado;
		querCorrer = correr;
	}

	/** Gira a visão (radianos). A arfagem fica limitada a 85 graus pela câmera. */
	public void girar(float dGuinada, float dArfagem) {
		camera.girar(dGuinada, dArfagem);
	}

	@Override
	public void SimulaSe(long diftime) {
		float dt = diftime / 1000f;
		if (dt <= 0) {
			return;
		}
		atualizarEstamina(dt);
		atualizarVelocidade(dt);

		float antesX = x, antesZ = z;
		mover(dt);
		float andou = (float) Math.hypot(x - antesX, z - antesZ);
		velocidadeReal = andou / dt;

		// olhos 1,7 m acima do chão, chegando aos poucos (subidas não dão solavanco)
		y = chao.alturaEm(x, z);
		alturaOlhos += (y + ALTURA_OLHOS - alturaOlhos) * (1 - (float) Math.exp(-SUAVIZACAO_OLHOS * dt));

		// passos e balanço de cabeça: a fase avança pi a cada passo dado
		float comprimentoPasso = correndo ? PASSO_CORRENDO : PASSO_ANDANDO;
		float faseAntes = fasePasso;
		fasePasso += andou / comprimentoPasso * (float) Math.PI;
		passosNoQuadro = (int) (Math.floor(fasePasso / Math.PI) - Math.floor(faseAntes / Math.PI));

		atualizarCamera();
	}

	private void atualizarEstamina(float dt) {
		boolean movendo = comandoFrente != 0 || comandoLado != 0;
		if (esgotado && estamina >= ESTAMINA_PARA_VOLTAR) {
			esgotado = false;
		}
		correndo = querCorrer && movendo && !esgotado && estamina > 0;
		if (correndo) {
			estamina = Math.max(0, estamina - dt / DURACAO_ESTAMINA);
			semCorrer = 0;
			if (estamina == 0) {
				esgotado = true;
			}
		} else {
			semCorrer += dt;
			if (semCorrer >= ESPERA_RECUPERACAO) {
				estamina = Math.min(1, estamina + dt / RECUPERACAO_ESTAMINA);
			}
		}
	}

	/** Aproxima a velocidade atual da desejada, suavemente (aceleração e frenagem). */
	private void atualizarVelocidade(float dt) {
		// frente e direita no plano, a partir só da guinada (olhar para cima não faz voar)
		float fx = (float) Math.sin(camera.guinada), fz = (float) -Math.cos(camera.guinada);
		float dx = -fz, dz = fx;

		float mx = fx * comandoFrente + dx * comandoLado;
		float mz = fz * comandoFrente + dz * comandoLado;
		float tamanho = (float) Math.hypot(mx, mz);
		float alvoX = 0, alvoZ = 0;
		if (tamanho > 0) {
			float velocidade = correndo ? VELOCIDADE_CORRENDO : VELOCIDADE_ANDANDO;
			alvoX = mx / tamanho * velocidade; // na diagonal não anda mais rápido
			alvoZ = mz / tamanho * velocidade;
		}
		float taxa = tamanho > 0 ? ACELERACAO : FRENAGEM;
		float fator = 1 - (float) Math.exp(-taxa * dt);
		vx += (alvoX - vx) * fator;
		vz += (alvoZ - vz) * fator;
	}

	/**
	 * Tenta ir para a nova posição. Se não puder, tenta só o movimento em x e depois só em z:
	 * é isso que faz o jogador deslizar ao longo da margem de um lago ou de um barranco.
	 */
	private void mover(float dt) {
		float novoX = x + vx * dt, novoZ = z + vz * dt;

		// troncos: empurra para fora (desliza em volta)
		float[] p = colisao.resolver(novoX, novoZ, RAIO);
		novoX = p[0];
		novoZ = p[1];

		// borda do mapa: simplesmente não passa do limite
		float limite = chao.tamanho() - MARGEM_BORDA;
		novoX = Math.max(MARGEM_BORDA, Math.min(limite, novoX));
		novoZ = Math.max(MARGEM_BORDA, Math.min(limite, novoZ));

		if (podeIr(novoX, novoZ)) {
			x = novoX;
			z = novoZ;
		} else if (podeIr(novoX, z)) {
			x = novoX;
			vz = 0;
		} else if (podeIr(x, novoZ)) {
			z = novoZ;
			vx = 0;
		} else {
			vx = 0;
			vz = 0;
		}
	}

	/** O chão em (nx, nz) aceita o jogador vindo da posição atual? */
	boolean podeIr(float nx, float nz) {
		if (chao.profundidadeAgua(nx, nz) > PROFUNDIDADE_MAXIMA) {
			return false;
		}
		boolean subindo = chao.alturaEm(nx, nz) > chao.alturaEm(x, z);
		if (subindo && chao.anguloEm(nx, nz) > DECLIVE_MAXIMO) {
			return false; // descer um barranco pode; subir não
		}
		return !colisao.colide(nx, nz, RAIO * 0.98f);
	}

	/** Câmera nos olhos, com o balanço de cabeça: desce a cada passo e oscila de um lado ao outro. */
	private void atualizarCamera() {
		float intensidade = Math.min(1, velocidadeReal / VELOCIDADE_CORRENDO);
		float sobe = -Math.abs((float) Math.sin(fasePasso)) * BALANCO_ALTURA * intensidade;
		float lado = (float) Math.sin(fasePasso) * BALANCO_LADO * intensidade;
		camera.x = x + camera.direita.x * lado;
		camera.y = alturaOlhos + sobe;
		camera.z = z + camera.direita.z * lado;
	}

	// ---------- consultas ----------

	public Camera camera() {
		return camera;
	}

	/** Estamina de 0 (vazia) a 1 (cheia). */
	public float estamina() {
		return estamina;
	}

	public boolean correndo() {
		return correndo;
	}

	/** Estamina baixa: a tela toca a respiração ofegante. */
	public boolean ofegante() {
		return estamina < ESTAMINA_OFEGANTE;
	}

	/** Quantos passos foram dados neste quadro (normalmente 0 ou 1): a tela toca o som. */
	public int passosNoQuadro() {
		return passosNoQuadro;
	}

	/** Velocidade real no plano, em m/s. */
	public float velocidade() {
		return velocidadeReal;
	}

	/** Procura, perto de (x, z), um ponto bom para começar: terra firme, quase plano, longe da borda. */
	public static float[] pontoDeInicio(ConsultaAltura chao, GradeColisao colisao, float x, float z) {
		for (float raio = 0; raio < chao.tamanho() / 2; raio += 2) {
			int tentativas = Math.max(1, (int) (raio * 2));
			for (int i = 0; i < tentativas; i++) {
				double angulo = 2 * Math.PI * i / tentativas;
				float px = x + raio * (float) Math.cos(angulo);
				float pz = z + raio * (float) Math.sin(angulo);
				if (chao.dentroDoMapa(px, pz, MARGEM_BORDA + 1) && chao.alturaEm(px, pz) > 0.2f
						&& chao.anguloEm(px, pz) < 20 && !colisao.colide(px, pz, RAIO * 2)) {
					return new float[] { px, pz };
				}
			}
		}
		return new float[] { x, z };
	}
}
