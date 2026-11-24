package mundo;

import java.util.ArrayList;
import java.util.List;

/**
 * Obstáculos do chão (troncos de árvore) para colisão, guardados numa grade de células.
 *
 * Testar o jogador contra milhares de troncos a cada quadro seria lento. Com a grade,
 * cada tronco fica na lista da célula de 4 x 4 m onde está, e uma consulta só olha as
 * células em volta do ponto. Cada obstáculo é um círculo no plano XZ.
 *
 * Também aceita retângulos alinhados aos eixos (as paredes da Univali). Como são poucos,
 * ficam numa lista à parte, testada inteira em toda consulta.
 */
public class GradeColisao {

	/** Um círculo no chão: centro (x, z) e raio, em metros. */
	public record Circulo(float x, float z, float raio) {}

	/** Um retângulo no chão, alinhado aos eixos, em metros. */
	public record Retangulo(float xMin, float zMin, float xMax, float zMax) {

		/** Distância do ponto até o retângulo (0 se estiver dentro). */
		public float distancia(float x, float z) {
			float dx = Math.max(Math.max(xMin - x, 0), x - xMax);
			float dz = Math.max(Math.max(zMin - z, 0), z - zMax);
			return (float) Math.sqrt(dx * dx + dz * dz);
		}
	}

	public static final float TAMANHO_CELULA = 4f;
	private final List<Retangulo> retangulos = new ArrayList<>();

	private final int celulas;
	private final List<List<Circulo>> grade;
	private int quantidade = 0;

	/** @param tamanhoMapa lado do mapa em metros (o mapa vai de 0 a este valor em x e z) */
	public GradeColisao(float tamanhoMapa) {
		celulas = (int) Math.ceil(tamanhoMapa / TAMANHO_CELULA) + 1;
		grade = new ArrayList<>(celulas * celulas);
		for (int i = 0; i < celulas * celulas; i++) {
			grade.add(new ArrayList<>());
		}
	}

	public void adicionar(float x, float z, float raio) {
		grade.get(indice(celula(x), celula(z))).add(new Circulo(x, z, raio));
		quantidade++;
	}

	public void adicionarRetangulo(float xMin, float zMin, float xMax, float zMax) {
		retangulos.add(new Retangulo(xMin, zMin, xMax, zMax));
		quantidade++;
	}

	public int quantidade() {
		return quantidade;
	}

	/** Um círculo de raio "raio" em (x, z) encosta em algum obstáculo? */
	public boolean colide(float x, float z, float raio) {
		for (Circulo c : proximos(x, z)) {
			float dx = x - c.x(), dz = z - c.z(), soma = raio + c.raio();
			if (dx * dx + dz * dz < soma * soma) {
				return true;
			}
		}
		for (Retangulo r : retangulos) {
			if (r.distancia(x, z) < raio || (raio == 0 && r.distancia(x, z) == 0)) return true;
		}
		return false;
	}

	/**
	 * Empurra um círculo para fora dos obstáculos em que ele estiver entrando, na direção
	 * do centro do obstáculo para o círculo. Andando contra um tronco, o resultado é
	 * deslizar em volta dele em vez de parar seco. Devolve {x, z} corrigidos.
	 */
	public float[] resolver(float x, float z, float raio) {
		for (int volta = 0; volta < 3; volta++) { // algumas voltas resolvem troncos encostados
			boolean mexeu = false;
			for (Circulo c : proximos(x, z)) {
				float dx = x - c.x(), dz = z - c.z(), soma = raio + c.raio();
				float d2 = dx * dx + dz * dz;
				if (d2 < soma * soma) {
					float d = (float) Math.sqrt(d2);
					if (d < 1e-5f) { dx = 1; dz = 0; d = 1; } // exatamente no centro: sai para qualquer lado
					x = c.x() + dx / d * soma;
					z = c.z() + dz / d * soma;
					mexeu = true;
				}
			}
			for (Retangulo r : retangulos) {
				float[] fora = empurrarParaFora(r, x, z, raio);
				if (fora != null) {
					x = fora[0];
					z = fora[1];
					mexeu = true;
				}
			}
			if (!mexeu) {
				break;
			}
		}
		return new float[] { x, z };
	}

	/**
	 * O segmento de (x1, z1) a (x2, z2) passa sem tocar em nenhum obstáculo?
	 * Usado para saber se o monstro enxerga o jogador e se um tiro passa entre os troncos.
	 */
	public boolean linhaLivre(float x1, float z1, float x2, float z2) {
		return linhaLivre(x1, z1, x2, z2, 0);
	}

	/**
	 * O mesmo, exigindo que a linha passe a pelo menos "folga" metros além do raio de cada
	 * obstáculo (o tronco desenhado é mais grosso que o círculo de colisão, e a copa mais ainda).
	 */
	public boolean linhaLivre(float x1, float z1, float x2, float z2, float folga) {
		// os retângulos: o segmento é percorrido a cada 0,5 m (são poucos e grandes)
		float comprimento = (float) Math.hypot(x2 - x1, z2 - z1);
		int passos = Math.max(1, (int) Math.ceil(comprimento / 0.5f));
		for (Retangulo r : retangulos) {
			for (int k = 0; k <= passos; k++) {
				float t = (float) k / passos;
				if (r.distancia(x1 + (x2 - x1) * t, z1 + (z2 - z1) * t) <= folga) return false;
			}
		}
		int i0 = celula(Math.min(x1, x2)) - 1, i1 = celula(Math.max(x1, x2)) + 1;
		int j0 = celula(Math.min(z1, z2)) - 1, j1 = celula(Math.max(z1, z2)) + 1;
		for (int j = Math.max(0, j0); j <= Math.min(celulas - 1, j1); j++) {
			for (int i = Math.max(0, i0); i <= Math.min(celulas - 1, i1); i++) {
				for (Circulo c : grade.get(indice(i, j))) {
					if (distanciaAoSegmento(c.x(), c.z(), x1, z1, x2, z2) < c.raio() + folga) {
						return false;
					}
				}
			}
		}
		return true;
	}

	/**
	 * Tira o círculo de dentro do retângulo: se o centro está fora, afasta do ponto mais
	 * próximo da borda; se está dentro, leva para a borda mais perto. null se não encosta.
	 */
	private static float[] empurrarParaFora(Retangulo r, float x, float z, float raio) {
		float d = r.distancia(x, z);
		if (d >= raio) return null;
		if (d > 1e-5f) {
			float px = Math.max(r.xMin(), Math.min(r.xMax(), x)), pz = Math.max(r.zMin(), Math.min(r.zMax(), z));
			return new float[] { px + (x - px) / d * raio, pz + (z - pz) / d * raio };
		}
		float esquerda = x - r.xMin(), direita = r.xMax() - x, baixo = z - r.zMin(), cima = r.zMax() - z;
		float menor = Math.min(Math.min(esquerda, direita), Math.min(baixo, cima));
		if (menor == esquerda) return new float[] { r.xMin() - raio, z };
		if (menor == direita) return new float[] { r.xMax() + raio, z };
		if (menor == baixo) return new float[] { x, r.zMin() - raio };
		return new float[] { x, r.zMax() + raio };
	}

	/** Distância do ponto (px, pz) ao segmento de A a B: projeta o ponto na reta e limita ao segmento. */
	static float distanciaAoSegmento(float px, float pz, float ax, float az, float bx, float bz) {
		float abx = bx - ax, abz = bz - az;
		float comprimento2 = abx * abx + abz * abz;
		float t = comprimento2 == 0 ? 0 : ((px - ax) * abx + (pz - az) * abz) / comprimento2;
		t = Math.max(0, Math.min(1, t));
		float cx = ax + abx * t - px, cz = az + abz * t - pz;
		return (float) Math.sqrt(cx * cx + cz * cz);
	}

	/** Os obstáculos das 9 células em volta do ponto (suficiente para raios de até 4 m). */
	private List<Circulo> proximos(float x, float z) {
		List<Circulo> lista = new ArrayList<>();
		int cx = celula(x), cz = celula(z);
		for (int j = cz - 1; j <= cz + 1; j++) {
			for (int i = cx - 1; i <= cx + 1; i++) {
				if (i >= 0 && j >= 0 && i < celulas && j < celulas) {
					lista.addAll(grade.get(indice(i, j)));
				}
			}
		}
		return lista;
	}

	private int celula(float v) {
		return Math.max(0, Math.min(celulas - 1, (int) Math.floor(v / TAMANHO_CELULA)));
	}

	private int indice(int i, int j) {
		return j * celulas + i;
	}
}
