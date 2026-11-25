package entidades;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import fase.AndarUnivali;
import fase.Ponto;
import mundo.GradeColisao;
import obj.Object3D;

/**
 * O prédio da Univali, onde a partida começa e termina: um bloco de 48 x 12 x 28 m com marquise,
 * colunas, letreiro, uma grade de janelas na fachada (umas acesas, outras apagadas, uma piscando,
 * e um vulto que muda de janela) e, dentro, o térreo (AndarUnivali): saguão, corredor e 12 salas,
 * com a Sala dos Professores trancada até o jogador achar o crachá. Só dados (dá para testar); o desenho fica em render.DesenhoPredio.
 *
 * O prédio fica sempre alinhado aos eixos (gira só em múltiplos de 90°), com a frente (a porta,
 * o +z do modelo) virada para o centro do mapa, onde fica a floresta. Assim, cada parede é um
 * retângulo simples na GradeColisao.
 */
public class PredioUnivali extends Object3D {

	public static final float LARGURA = AndarUnivali.LARGURA;           // 48 m
	public static final float PROFUNDIDADE = AndarUnivali.PROFUNDIDADE; // 28 m
	public static final float ALTURA = 12f, PE_DIREITO = 3.8f, ESPESSURA = 0.3f;
	/** Andares das janelas da fachada (centro, em metros do chão), largura e altura de cada janela. */
	public static final float[] ANDARES = { 2.2f, 6.0f, 9.8f };
	public static final float JANELA_LARGURA = 2.0f, JANELA_ALTURA = 1.6f;
	/** Distância para usar algo (a porta, a mesa do professor, a chave) com a tecla E. */
	public static final float ALCANCE_USO = 2.2f;

	/** Uma janela: o centro no modelo (x, y, z), para onde ela olha (a normal, no modelo) e se está acesa. */
	public record Janela(float x, float y, float z, float nx, float nz, boolean acesa) {}

	/** Rotação em volta do eixo y, múltiplo de 90°: a frente (+z do modelo) aponta para (sen g, cos g). */
	public final float giro;
	public final AndarUnivali andar;
	private final float frenteX, frenteZ;
	private final List<Janela> janelas = new ArrayList<>();
	private final int janelaPiscando;

	// o vulto nas janelas de cima
	private final Random random;
	private int janelaVulto = -1;
	private float tempoVulto = 0;

	public PredioUnivali(float x, float y, float z, float giro, long seed) {
		super(x, y, z);
		this.giro = giro;
		frenteX = Math.round(Math.sin(giro));
		frenteZ = Math.round(Math.cos(giro));
		random = new Random(seed);
		andar = new AndarUnivali(seed);
		montarJanelas();
		List<Integer> acesas = acesas();
		janelaPiscando = acesas.isEmpty() ? -1 : acesas.get(random.nextInt(acesas.size()));
		janelaVulto = sortearJanelaVulto();
		tempoVulto = 8 + random.nextFloat() * 7;
	}

	/** O giro que deixa a frente virada para o centro do mapa (arredondado para um dos 4 lados). */
	public static float giroParaOCentro(Ponto univali, float tamanhoMapa) {
		float dx = tamanhoMapa / 2 - univali.x(), dz = tamanhoMapa / 2 - univali.z();
		if (Math.abs(dx) < 1e-3f && Math.abs(dz) < 1e-3f) dz = 1;
		return (float) (Math.round(Math.atan2(dx, dz) / (Math.PI / 2)) * (Math.PI / 2));
	}

	/** O ponto na frente da porta, do lado de fora: de onde saem as trilhas para a floresta. */
	public static Ponto saida(Ponto univali, float giro) {
		float fx = Math.round(Math.sin(giro)), fz = Math.round(Math.cos(giro));
		float d = PROFUNDIDADE / 2 + 6;
		return new Ponto(univali.x() + fx * d, univali.z() + fz * d);
	}

	/** Janelas nas 4 fachadas, em 3 andares; na frente, o térreo fica livre perto da porta. ~45% acesas. */
	private void montarJanelas() {
		float meiaL = LARGURA / 2, meiaP = PROFUNDIDADE / 2, saliencia = 0.06f;
		for (float y : ANDARES) {
			for (float x = -meiaL + 2.5f; x <= meiaL - 2.5f; x += 4.2f) {
				if (!(y == ANDARES[0] && Math.abs(x) < 7)) {
					janelas.add(new Janela(x, y, meiaP + saliencia, 0, 1, random.nextFloat() < 0.45f));
				}
				janelas.add(new Janela(x, y, -meiaP - saliencia, 0, -1, random.nextFloat() < 0.45f));
			}
			for (float z = -meiaP + 3f; z <= meiaP - 3f; z += 4.5f) {
				janelas.add(new Janela(meiaL + saliencia, y, z, 1, 0, random.nextFloat() < 0.45f));
				janelas.add(new Janela(-meiaL - saliencia, y, z, -1, 0, random.nextFloat() < 0.45f));
			}
		}
	}

	private List<Integer> acesas() {
		List<Integer> lista = new ArrayList<>();
		for (int i = 0; i < janelas.size(); i++) {
			if (janelas.get(i).acesa()) lista.add(i);
		}
		return lista;
	}

	/** Uma janela acesa dos andares de cima, de frente ou de trás, que não a que pisca. */
	private int sortearJanelaVulto() {
		List<Integer> candidatas = new ArrayList<>();
		for (int i : acesas()) {
			Janela j = janelas.get(i);
			if (i != janelaPiscando && j.nz() != 0 && j.y() > ANDARES[0]) candidatas.add(i);
		}
		return candidatas.isEmpty() ? -1 : candidatas.get(random.nextInt(candidatas.size()));
	}

	/** O vulto some de vez em quando (1,5 s) e volta noutra janela; fica 8 a 15 s em cada uma. */
	@Override
	public void SimulaSe(long diftime) {
		tempoVulto -= diftime / 1000f;
		if (tempoVulto <= 0) {
			if (janelaVulto >= 0) {
				janelaVulto = -1;
				tempoVulto = 1.5f;
			} else {
				janelaVulto = sortearJanelaVulto();
				tempoVulto = 8 + random.nextFloat() * 7;
			}
		}
	}

	// ---------- do modelo para o mundo ----------

	/** Um ponto do modelo (x para a direita da fachada, z para a frente) levado ao mundo. */
	public float[] noMundo(float lx, float ly, float lz) {
		// girar (lx, lz) por g em volta de y: x' = lx cos g + lz sen g, z' = -lx sen g + lz cos g
		return new float[] { x + lx * frenteZ + lz * frenteX, y + ly, z - lx * frenteX + lz * frenteZ };
	}

	/** Um retângulo do modelo levado ao mundo (com o giro de 90° em 90°, continua alinhado aos eixos). */
	/** O contrário do noMundo (só no chão): {x, z} no modelo de um ponto (x, z) do mundo. */
	public float[] paraModelo(float wx, float wz) {
		float dx = wx - x, dz = wz - z; // a rotação é um múltiplo de 90 graus: a inversa é a transposta
		return new float[] { dx * frenteZ - dz * frenteX, dx * frenteX + dz * frenteZ };
	}

	/** Uma direção do mundo (x, z) levada para o modelo (sem a posição). */
	public float[] direcaoParaModelo(float dx, float dz) {
		return new float[] { dx * frenteZ - dz * frenteX, dx * frenteX + dz * frenteZ };
	}

	public GradeColisao.Retangulo noMundo(AndarUnivali.Caixa c) {
		float[] a = noMundo(c.xMin(), 0, c.zMin()), b = noMundo(c.xMax(), 0, c.zMax());
		return new GradeColisao.Retangulo(Math.min(a[0], b[0]), Math.min(a[2], b[2]), Math.max(a[0], b[0]),
				Math.max(a[2], b[2]));
	}

	/** O retângulo do prédio inteiro no chão (a zona segura e o aplainamento usam este). */
	public GradeColisao.Retangulo retangulo() {
		return noMundo(new AndarUnivali.Caixa(-LARGURA / 2, -PROFUNDIDADE / 2, LARGURA / 2, PROFUNDIDADE / 2));
	}

	/**
	 * As paredes no modelo: a casca (fundos, laterais e a frente com o vão da porta), as
	 * paredes internas (corredor, saguão e salas), os móveis e as portas que nunca abrem.
	 */
	public List<AndarUnivali.Caixa> paredesNoModelo() {
		float L = LARGURA / 2, P = PROFUNDIDADE / 2, e = ESPESSURA, vao = AndarUnivali.VAO_ENTRADA / 2;
		List<AndarUnivali.Caixa> lista = new ArrayList<>();
		lista.add(new AndarUnivali.Caixa(-L, -P, L, -P + e));            // fundos
		lista.add(new AndarUnivali.Caixa(-L, -P, -L + e, P));            // esquerda
		lista.add(new AndarUnivali.Caixa(L - e, -P, L, P));              // direita
		lista.add(new AndarUnivali.Caixa(-L, P - e, -vao, P));           // frente, à esquerda da porta
		lista.add(new AndarUnivali.Caixa(vao, P - e, L, P));             // frente, à direita da porta
		lista.addAll(andar.paredes());
		lista.addAll(moveis());
		for (AndarUnivali.Sala sala : andar.salas()) {
			if (sala.trancada() && sala.tipo() != AndarUnivali.Tipo.PROFESSORES) lista.add(andar.porta(sala)); // nunca abrem
		}
		return lista;
	}

	/**
	 * A escopeta deitada no balcão do saguão (o cano para a TV) e a carta ao lado dela, no modelo
	 * {x, y, z}: quem tentou fugir antes deixou as duas ali.
	 */
	public static final float[] ESCOPETA = { 0.35f, 1.15f, 9.0f }, CARTA = { -0.75f, 1.12f, 9.0f };

	/** Onde o jogador pega a escopeta e a carta (o meio entre as duas, no chão do modelo: {x, z}). */
	public float[] escopetaNoModelo() {
		return new float[] { (ESCOPETA[0] + CARTA[0]) / 2, ESCOPETA[2] };
	}

	/** Os móveis que o jogador não atravessa: a mesa do professor, o balcão do saguão e as fileiras de carteiras. */
	public List<AndarUnivali.Caixa> moveis() {
		List<AndarUnivali.Caixa> lista = new ArrayList<>();
		lista.add(mesaEProfessor());
		lista.add(new AndarUnivali.Caixa(-3, 8.6f, 3, 9.4f)); // o balcão de atendimento
		for (AndarUnivali.Sala sala : andar.salas()) {
			if (sala.tipo() != AndarUnivali.Tipo.AULA) continue;
			List<float[]> c = AndarUnivali.carteiras(sala);
			for (int fila = 0; fila < 3; fila++) { // cada fileira de 4 carteiras é um bloco só
				float[] a = c.get(fila * 4), b = c.get(fila * 4 + 3);
				lista.add(new AndarUnivali.Caixa(a[0] - 0.35f, a[1] - 0.3f, b[0] + 0.35f, b[1] + 0.3f));
			}
		}
		return lista;
	}

	/** A mesa com o professor e a cadeira (no espaço dele: x de -0,85 a 0,85, z de -0,35 a 1,35), no modelo. */
	private AndarUnivali.Caixa mesaEProfessor() {
		float[] p = professorNoModelo();
		float dx = (float) Math.sin(p[2]), dz = (float) Math.cos(p[2]); // para onde ele olha (um eixo)
		float[][] cantos = { { -0.85f, -0.35f }, { 0.85f, -0.35f }, { -0.85f, 1.35f }, { 0.85f, 1.35f } };
		float xMin = Float.MAX_VALUE, zMin = Float.MAX_VALUE, xMax = -Float.MAX_VALUE, zMax = -Float.MAX_VALUE;
		for (float[] c : cantos) {
			// lado (x do professor) e frente (z do professor) levados ao modelo do prédio
			float x = p[0] + c[0] * dz + c[1] * dx, z = p[1] - c[0] * dx + c[1] * dz;
			xMin = Math.min(xMin, x);
			xMax = Math.max(xMax, x);
			zMin = Math.min(zMin, z);
			zMax = Math.max(zMax, z);
		}
		return new AndarUnivali.Caixa(xMin, zMin, xMax, zMax);
	}

	public List<GradeColisao.Retangulo> paredes() {
		List<GradeColisao.Retangulo> lista = new ArrayList<>();
		List<AndarUnivali.Caixa> moveis = moveis();
		for (AndarUnivali.Caixa c : paredesNoModelo()) {
			GradeColisao.Retangulo r = noMundo(c);
			// um móvel só vai até a altura dele: o tiro e a vista passam por cima
			if (moveis.contains(c)) r = new GradeColisao.Retangulo(r.xMin(), r.zMin(), r.xMax(), r.zMax(), y + ALTURA_MOVEIS);
			lista.add(r);
		}
		return lista;
	}

	/** A altura dos móveis (o balcão, as carteiras, a mesa), em metros acima do piso. */
	public static final float ALTURA_MOVEIS = 1.2f;

	/** O vão da porta de entrada (bloqueado enquanto ela está trancada). */
	public AndarUnivali.Caixa portaEntradaNoModelo() {
		float P = PROFUNDIDADE / 2, vao = AndarUnivali.VAO_ENTRADA / 2;
		return new AndarUnivali.Caixa(-vao, P - ESPESSURA, vao, P);
	}

	public GradeColisao.Retangulo portaEntrada() {
		return noMundo(portaEntradaNoModelo());
	}

	public GradeColisao.Retangulo portaProfessor() {
		return noMundo(andar.portaProfessor());
	}

	/** O ponto do modelo onde o jogador começa: o meio da sala de início. */
	public float[] inicioJogador() {
		float[] c = andar.inicio().centro();
		return noMundo(c[0], 0, c[1]);
	}

	/** O professor, sentado no fundo da Sala dos Professores, de frente para a porta: {x, z, giro}, no modelo. */
	public float[] professorNoModelo() {
		AndarUnivali.Sala s = andar.professores();
		float[] c = s.centro(), d = s.direcaoDaPorta();
		float recuo = Math.abs(s.zMax() - s.zMin()) / 2 - 1.6f;
		return new float[] { c[0] - d[0] * recuo, c[1] - d[1] * recuo, (float) Math.atan2(d[0], d[1]) };
	}

	/** O crachá, na mesa perto da lousa da sala dele (no fundo dela, longe da porta): {x, z} no modelo. */
	public float[] chaveNoModelo() {
		AndarUnivali.Sala s = andar.cracha();
		float[] c = s.centro(), d = s.direcaoDaPorta();
		float recuo = Math.abs(s.zMax() - s.zMin()) / 2 - 2.2f;
		return new float[] { c[0] - d[0] * recuo, c[1] - d[1] * recuo };
	}

	/** O jogador (os pés em px, pz) está perto o bastante de um ponto do modelo para usar algo ali? */
	public boolean perto(float px, float pz, float[] noModelo) {
		float[] w = noMundo(noModelo[0], 0, noModelo[1]);
		float dx = px - w[0], dz = pz - w[2];
		return dx * dx + dz * dz <= ALCANCE_USO * ALCANCE_USO;
	}

	/** O centro do vão da porta de entrada e da sala do professor, no modelo. */
	public float[] centroPortaEntrada() {
		return new float[] { 0, PROFUNDIDADE / 2 };
	}

	public float[] centroPortaProfessor() {
		return andar.professores().porta();
	}

	/** A sala trancada para sempre cuja porta está ao alcance (ou null). */
	public AndarUnivali.Sala salaTrancadaPerto(float px, float pz) {
		for (AndarUnivali.Sala s : andar.salas()) {
			if (s.trancada() && s.tipo() != AndarUnivali.Tipo.PROFESSORES && perto(px, pz, s.porta())) return s;
		}
		return null;
	}

	/** O ponto está dentro do prédio? */
	public boolean dentro(float px, float pz) {
		return retangulo().distancia(px, pz) == 0;
	}

	/** O meio da porta, no chão (do lado de fora). */
	public float[] porta() {
		return noMundo(0, 0, PROFUNDIDADE / 2);
	}

	/** Os dois postes de luz, 4 m na frente da fachada e 6 m para cada lado da porta: {x, z} no mundo. */
	public float[][] postes() {
		float[] a = noMundo(-6, 0, PROFUNDIDADE / 2 + 4), b = noMundo(6, 0, PROFUNDIDADE / 2 + 4);
		return new float[][] { { a[0], a[2] }, { b[0], b[2] } };
	}

	public List<Janela> janelas() {
		return janelas;
	}

	public int janelaPiscando() {
		return janelaPiscando;
	}

	/** A janela onde o vulto está agora (-1 = ele sumiu por um instante). */
	public int janelaVulto() {
		return janelaVulto;
	}

	public float frenteX() {
		return frenteX;
	}

	public float frenteZ() {
		return frenteZ;
	}

	// ---------- o chão embaixo do prédio ----------

	/**
	 * Aplaina o terreno embaixo do prédio, antes de montar a malha: dentro do retângulo (com uma
	 * folga de 6 m para a calçada e os postes), tudo fica na altura média do lugar (e pelo menos
	 * 0,6 m acima da água); numa faixa de 10 m em volta, uma rampa suave liga essa altura ao chão
	 * original. Assim o prédio não flutua nem afunda. Altera a grade de alturas (centímetros,
	 * um ponto a cada "passo" metros) e devolve a altura do piso, em metros.
	 */
	public static float aplainar(int[][] cm, float passo, GradeColisao.Retangulo r) {
		float folga = 6, rampa = 10;
		long soma = 0;
		int n = 0;
		for (int j = 0; j < cm.length; j++) {
			for (int i = 0; i < cm[0].length; i++) {
				if (r.distancia(i * passo, j * passo) <= folga) {
					soma += cm[j][i];
					n++;
				}
			}
		}
		int alvo = Math.max(60, n == 0 ? 100 : (int) (soma / n));
		for (int j = 0; j < cm.length; j++) {
			for (int i = 0; i < cm[0].length; i++) {
				float d = r.distancia(i * passo, j * passo);
				if (d >= folga + rampa) continue;
				float t = Math.max(0, Math.min(1, (d - folga) / rampa));
				float peso = 1 - t * t * (3 - 2 * t); // 1 no plano, descendo suave até 0 no fim da rampa
				cm[j][i] = Math.round(cm[j][i] + (alvo - cm[j][i]) * peso);
			}
		}
		return alvo / 100f;
	}
}
