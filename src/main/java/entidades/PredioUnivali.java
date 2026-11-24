package entidades;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import fase.Ponto;
import mundo.GradeColisao;
import obj.Object3D;

/**
 * O prédio da Univali, escondido na floresta: um bloco de 40 x 12 x 15 m com marquise, colunas,
 * porta, letreiro e uma grade de janelas, umas acesas, outras apagadas, uma piscando, e a
 * silhueta do professor mudando de janela de vez em quando. Só dados (dá para testar); o
 * desenho fica em render.DesenhoPredio.
 *
 * O prédio fica sempre alinhado aos eixos (gira só em múltiplos de 90°), com a frente (a porta,
 * o +z do modelo) virada para a trilha que chega até ele. Assim, a colisão das paredes é um
 * retângulo simples na GradeColisao.
 */
public class PredioUnivali extends Object3D {

	public static final float LARGURA = 40f, PROFUNDIDADE = 15f, ALTURA = 12f;
	/** Andares das janelas (centro, em metros do chão), largura e altura de cada janela. */
	public static final float[] ANDARES = { 2.2f, 6.0f, 9.8f };
	public static final float JANELA_LARGURA = 2.0f, JANELA_ALTURA = 1.6f;
	/** A área da entrega: um círculo de 3 m centrado 2 m na frente da porta. */
	public static final float RAIO_ENTREGA = 3f;
	/** Quanto o prédio fica atrás do fim da trilha (o fim da trilha fica na frente da porta). */
	private static final float RECUO = 10f;

	/** Uma janela: o centro no modelo (x, y, z), para onde ela olha (a normal, no modelo) e se está acesa. */
	public record Janela(float x, float y, float z, float nx, float nz, boolean acesa) {}

	/** Rotação em volta do eixo y, múltiplo de 90°: a frente (+z do modelo) aponta para (sen g, cos g). */
	public final float giro;
	private final float frenteX, frenteZ;
	private final List<Janela> janelas = new ArrayList<>();
	private final int janelaPiscando;

	// o professor
	private final Random random;
	private int janelaProfessor = -1;
	private float tempoProfessor = 0;

	public PredioUnivali(float x, float y, float z, float giro, long seed) {
		super(x, y, z);
		this.giro = giro;
		frenteX = Math.round(Math.sin(giro));
		frenteZ = Math.round(Math.cos(giro));
		random = new Random(seed);
		montarJanelas();
		List<Integer> acesas = acesas();
		janelaPiscando = acesas.isEmpty() ? -1 : acesas.get(random.nextInt(acesas.size()));
		janelaProfessor = sortearJanelaProfessor();
		tempoProfessor = 8 + random.nextFloat() * 7;
	}

	/**
	 * O prédio para a Univali da fase: a frente virada para quem chega pela trilha (a direção
	 * do penúltimo ponto da trilha até o último, invertida e arredondada para um dos 4 lados),
	 * o centro recuado 10 m atrás do fim da trilha.
	 */
	public static PredioUnivali naChegada(Ponto univali, List<Ponto> trilha, float y, long seed) {
		float dx = 0, dz = 1;
		if (trilha.size() >= 2) {
			Ponto a = trilha.get(Math.max(0, trilha.size() - 6)), b = trilha.get(trilha.size() - 1);
			dx = a.x() - b.x();
			dz = a.z() - b.z(); // do prédio para quem chega
		}
		float giro = (float) (Math.round(Math.atan2(dx, dz) / (Math.PI / 2)) * (Math.PI / 2));
		float fx = Math.round(Math.sin(giro)), fz = Math.round(Math.cos(giro));
		return new PredioUnivali(univali.x() - fx * RECUO, y, univali.z() - fz * RECUO, giro, seed);
	}

	/** Janelas nas 4 fachadas, em 3 andares; na frente, o térreo fica livre perto da porta. ~45% acesas. */
	private void montarJanelas() {
		float meiaL = LARGURA / 2, meiaP = PROFUNDIDADE / 2, saliencia = 0.06f;
		for (float y : ANDARES) {
			for (float x = -meiaL + 2.5f; x <= meiaL - 2.5f; x += 4.2f) {
				if (!(y == ANDARES[0] && Math.abs(x) < 5)) {
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

	/** Uma janela acesa qualquer, de frente ou de trás (as maiores fachadas), que não a que pisca. */
	private int sortearJanelaProfessor() {
		List<Integer> candidatas = new ArrayList<>();
		for (int i : acesas()) {
			if (i != janelaPiscando && janelas.get(i).nz() != 0) candidatas.add(i);
		}
		return candidatas.isEmpty() ? -1 : candidatas.get(random.nextInt(candidatas.size()));
	}

	/** O professor some de vez em quando (1,5 s) e volta noutra janela; fica 8 a 15 s em cada uma. */
	@Override
	public void SimulaSe(long diftime) {
		tempoProfessor -= diftime / 1000f;
		if (tempoProfessor <= 0) {
			if (janelaProfessor >= 0) {
				janelaProfessor = -1;           // sumiu
				tempoProfessor = 1.5f;
			} else {
				janelaProfessor = sortearJanelaProfessor(); // apareceu noutra janela
				tempoProfessor = 8 + random.nextFloat() * 7;
			}
		}
	}

	// ---------- do modelo para o mundo ----------

	/** Um ponto do modelo (x para a direita da fachada, z para a frente) levado ao mundo. */
	public float[] noMundo(float lx, float ly, float lz) {
		// girar (lx, lz) por g em volta de y: x' = lx cos g + lz sen g, z' = -lx sen g + lz cos g
		return new float[] { x + lx * frenteZ + lz * frenteX, y + ly, z - lx * frenteX + lz * frenteZ };
	}

	/** O retângulo das paredes no chão (para a colisão). */
	public GradeColisao.Retangulo retangulo() {
		float meiaX = frenteZ != 0 ? LARGURA / 2 : PROFUNDIDADE / 2;
		float meiaZ = frenteZ != 0 ? PROFUNDIDADE / 2 : LARGURA / 2;
		return new GradeColisao.Retangulo(x - meiaX, z - meiaZ, x + meiaX, z + meiaZ);
	}

	/** O meio da porta, no chão. */
	public float[] porta() {
		return noMundo(0, 0, PROFUNDIDADE / 2);
	}

	/** O jogador (os pés em px, pz) está na área da entrega, na frente da porta? */
	public boolean naEntrega(float px, float pz) {
		float[] centro = noMundo(0, 0, PROFUNDIDADE / 2 + 2);
		float dx = px - centro[0], dz = pz - centro[2];
		return dx * dx + dz * dz <= RAIO_ENTREGA * RAIO_ENTREGA;
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

	/** A janela onde o professor está agora (-1 = ele sumiu por um instante). */
	public int janelaProfessor() {
		return janelaProfessor;
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
