package mundo;

/**
 * Perguntas sobre o chão em qualquer ponto (x, z) do mapa, em metros: altura, inclinação,
 * água. Usada pelo jogador, pelo monstro, pelas provas, pelas árvores e pelo prédio,
 * para nada flutuar nem afundar.
 *
 * A altura entre os pontos da grade é calculada por interpolação bilinear: uma média
 * ponderada dos quatro pontos em volta, que muda suavemente (sem degraus) ao andar.
 *
 * Não usa OpenGL: pode ser criada na thread de carregamento e testada.
 */
public class ConsultaAltura {

	private final int[][] alturas;   // [linha z][coluna x], na unidade da grade
	private final int pontos;        // pontos por lado
	private final float passo;       // metros entre dois pontos
	private final float metrosPorUnidade;

	/**
	 * @param alturas          grade quadrada, alturas[z][x], com a água em altura negativa
	 * @param passo            metros entre dois pontos vizinhos
	 * @param metrosPorUnidade quantos metros vale cada unidade da grade (0.01 para centímetros)
	 */
	public ConsultaAltura(int[][] alturas, float passo, float metrosPorUnidade) {
		this.alturas = alturas;
		this.pontos = alturas.length;
		this.passo = passo;
		this.metrosPorUnidade = metrosPorUnidade;
	}

	/** Lado do mapa em metros (de 0 até este valor, em x e em z). */
	public float tamanho() {
		return (pontos - 1) * passo;
	}

	/** Altura do chão em metros (negativa no fundo dos lagos). Fora do mapa, vale a borda mais próxima. */
	public float alturaEm(float x, float z) {
		float gx = limitar(x / passo, 0, pontos - 1);
		float gz = limitar(z / passo, 0, pontos - 1);
		int i0 = Math.min((int) gx, pontos - 2);
		int j0 = Math.min((int) gz, pontos - 2);
		float tx = gx - i0;
		float tz = gz - j0;

		float a00 = alturas[j0][i0], a10 = alturas[j0][i0 + 1];
		float a01 = alturas[j0 + 1][i0], a11 = alturas[j0 + 1][i0 + 1];
		float emCima = a00 + (a10 - a00) * tx;
		float embaixo = a01 + (a11 - a01) * tx;
		return (emCima + (embaixo - emCima) * tz) * metrosPorUnidade;
	}

	/**
	 * Vetor normal do chão (x, y, z), unitário, apontando para cima. Calculado pela
	 * diferença de altura entre os vizinhos à esquerda e à direita, à frente e atrás.
	 */
	public float[] normalEm(float x, float z) {
		float dx = (alturaEm(x + passo, z) - alturaEm(x - passo, z)) / (2 * passo);
		float dz = (alturaEm(x, z + passo) - alturaEm(x, z - passo)) / (2 * passo);
		float nx = -dx, ny = 1, nz = -dz;
		float tamanho = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		return new float[] { nx / tamanho, ny / tamanho, nz / tamanho };
	}

	/**
	 * Inclinação de 0 (plano) a 1 (parede vertical): 1 menos o componente vertical da normal,
	 * a mesma medida que o shader do terreno usa. 40 graus correspondem a cerca de 0,23.
	 */
	public float inclinacaoEm(float x, float z) {
		return 1 - normalEm(x, z)[1];
	}

	/** Ângulo da inclinação em graus (mais fácil de pensar ao ajustar regras). */
	public float anguloEm(float x, float z) {
		return (float) Math.toDegrees(Math.acos(normalEm(x, z)[1]));
	}

	public boolean ehAgua(float x, float z) {
		return alturaEm(x, z) < 0;
	}

	/** Profundidade da água em metros (0 em terra firme). */
	public float profundidadeAgua(float x, float z) {
		return Math.max(0, -alturaEm(x, z));
	}

	/** O ponto está dentro do mapa, a pelo menos "margem" metros de cada borda? */
	public boolean dentroDoMapa(float x, float z, float margem) {
		float t = tamanho();
		return x >= margem && x <= t - margem && z >= margem && z <= t - margem;
	}

	private static float limitar(float v, float min, float max) {
		return Math.max(min, Math.min(max, v));
	}
}
