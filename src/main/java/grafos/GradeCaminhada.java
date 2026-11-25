package grafos;

import java.util.ArrayDeque;

import mundo.ConsultaAltura;

/**
 * O mapa visto como um grafo: o chão é dividido em células quadradas (4 m para o AG da fase,
 * 2 m para os caminhos finais). Cada célula caminhável é um vértice; as 8 vizinhas
 * caminháveis são as arestas.
 *
 * Célula caminhável: não é água, a inclinação é menor que 40° (o que o jogador sobe) e fica
 * fora da faixa da borda (a muralha de árvores). As árvores ainda não existem quando a grade
 * é feita: elas são postas depois, longe das trilhas, e o espaço entre elas deixa passar.
 *
 * Também guarda as componentes conexas (flood fill): duas células com o mesmo rótulo estão
 * ligadas por algum caminho. "Dá para chegar lá?" vira uma comparação de números, sem A*.
 * Só dados (dá para testar).
 */
public class GradeCaminhada {

	public static final float DECLIVE_MAXIMO = 40f;  // graus
	public static final float MARGEM_BORDA = 26f;    // metros (a muralha de árvores tem 24)

	public final float celula;
	public final int lado;
	private final boolean[] caminhavel;
	private final float[] inclinacao;   // 0 = plano, 1 = 45° (tangente do ângulo, limitada)
	private final int[] componente;     // -1 = não caminhável
	private final float[] custoExtra;   // custo a mais de passar pela célula (mato fechado, raízes...), 0 = nenhum

	/** A grade do mapa inteiro, com células de "celula" metros. */
	public GradeCaminhada(ConsultaAltura chao, float celula) {
		this(celula, (int) Math.floor(chao.tamanho() / celula));
		for (int j = 0; j < lado; j++) {
			for (int i = 0; i < lado; i++) {
				float x = centro(i), z = centro(j);
				int k = j * lado + i;
				float angulo = chao.anguloEm(x, z);
				inclinacao[k] = (float) Math.min(1, Math.tan(Math.toRadians(angulo)));
				caminhavel[k] = chao.dentroDoMapa(x, z, MARGEM_BORDA) && chao.alturaEm(x, z) > 0.05f
						&& angulo < DECLIVE_MAXIMO;
			}
		}
		rotularComponentes();
	}

	/** Uma grade quadrada montada à mão (para os testes): "#" é obstáculo, "." é livre, dígitos são a inclinação em décimos. */
	public static GradeCaminhada deTexto(float celula, String... linhas) {
		GradeCaminhada g = new GradeCaminhada(celula, linhas.length);
		for (int j = 0; j < g.lado; j++) {
			for (int i = 0; i < g.lado; i++) {
				char c = linhas[j].charAt(i);
				int k = j * g.lado + i;
				g.caminhavel[k] = c != '#';
				g.inclinacao[k] = Character.isDigit(c) ? (c - '0') / 10f : 0;
			}
		}
		g.rotularComponentes();
		return g;
	}

	/**
	 * Uma grade quadrada de "lado" células em que a célula é livre quando livre(x, z) diz que sim,
	 * no centro dela (x e z a partir de 0, em metros). Plana. Usada dentro da Univali, onde quem
	 * diz o que está livre são as paredes e os móveis (ver PlantaInterna).
	 */
	public static GradeCaminhada deLivres(float celula, int lado, java.util.function.BiPredicate<Float, Float> livre) {
		GradeCaminhada g = new GradeCaminhada(celula, lado);
		for (int j = 0; j < lado; j++) {
			for (int i = 0; i < lado; i++) {
				g.caminhavel[j * lado + i] = livre.test(g.centro(i), g.centro(j));
			}
		}
		g.rotularComponentes();
		return g;
	}

	private GradeCaminhada(float celula, int lado) {
		this.celula = celula;
		this.lado = lado;
		caminhavel = new boolean[lado * lado];
		inclinacao = new float[lado * lado];
		componente = new int[lado * lado];
		custoExtra = new float[lado * lado];
	}

	/**
	 * Um custo a mais em cada célula, vindo de um ruído fractal (de 0 a "forca"): o A* passa a
	 * desviar das manchas "difíceis" e o caminho ótimo faz curvas, como uma trilha de verdade,
	 * em vez de linhas retas e diagonais perfeitas.
	 */
	public void adicionarIrregularidade(long seed, float forca) {
		terreno.RuidoFractal ruido = new terreno.RuidoFractal(seed);
		for (int j = 0; j < lado; j++) {
			for (int i = 0; i < lado; i++) {
				double v = ruido.fbm(centro(i) / 16.0, centro(j) / 16.0, 3) * 0.5 + 0.5;
				custoExtra[j * lado + i] = forca * (float) Math.max(0, Math.min(1, v));
			}
		}
	}

	/** Marca como obstáculo as células com o centro dentro do retângulo (um prédio) e refaz as componentes. */
	public void bloquear(float xMin, float zMin, float xMax, float zMax) {
		for (int j = 0; j < lado; j++) {
			for (int i = 0; i < lado; i++) {
				float x = centro(i), z = centro(j);
				if (x >= xMin && x <= xMax && z >= zMin && z <= zMax) caminhavel[j * lado + i] = false;
			}
		}
		rotularComponentes();
	}

	public float custoExtra(int i, int j) {
		return custoExtra[j * lado + i];
	}

	/** Flood fill: cada grupo de células ligadas recebe um número. */
	private void rotularComponentes() {
		java.util.Arrays.fill(componente, -1);
		int rotulo = 0;
		ArrayDeque<Integer> fila = new ArrayDeque<>();
		for (int inicio = 0; inicio < componente.length; inicio++) {
			if (!caminhavel[inicio] || componente[inicio] >= 0) continue;
			componente[inicio] = rotulo;
			fila.add(inicio);
			while (!fila.isEmpty()) {
				int k = fila.poll();
				int i = k % lado, j = k / lado;
				for (int dj = -1; dj <= 1; dj++) {
					for (int di = -1; di <= 1; di++) {
						int ni = i + di, nj = j + dj;
						if (!dentro(ni, nj)) continue;
						int v = nj * lado + ni;
						if (caminhavel[v] && componente[v] < 0) {
							componente[v] = rotulo;
							fila.add(v);
						}
					}
				}
			}
			rotulo++;
		}
	}

	// ---------- consultas ----------

	public boolean dentro(int i, int j) {
		return i >= 0 && j >= 0 && i < lado && j < lado;
	}

	public boolean caminhavel(int i, int j) {
		return dentro(i, j) && caminhavel[j * lado + i];
	}

	public float inclinacao(int i, int j) {
		return inclinacao[j * lado + i];
	}

	/** Rótulo da componente conexa (-1 se a célula não é caminhável). */
	public int componente(int i, int j) {
		return dentro(i, j) ? componente[j * lado + i] : -1;
	}

	/** A célula que contém a coordenada x (ou z), em metros. */
	public int celulaDe(float metros) {
		return (int) Math.floor(metros / celula);
	}

	/** O centro da célula i, em metros. */
	public float centro(int i) {
		return (i + 0.5f) * celula;
	}

	public boolean caminhavelEm(float x, float z) {
		return caminhavel(celulaDe(x), celulaDe(z));
	}

	public int componenteEm(float x, float z) {
		return componente(celulaDe(x), celulaDe(z));
	}

	/** Existe caminho entre os dois pontos? (Os dois caminháveis e na mesma componente.) */
	public boolean ligados(float x1, float z1, float x2, float z2) {
		int a = componenteEm(x1, z1);
		return a >= 0 && a == componenteEm(x2, z2);
	}
}
