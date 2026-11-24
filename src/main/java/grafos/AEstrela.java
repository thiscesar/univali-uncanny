package grafos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Busca A* na grade de caminhada: o caminho mais barato entre duas células.
 *
 * - Vizinhança de 8 direções: andar reto custa 1 célula, na diagonal raiz de 2.
 * - Custo de um passo = distância x (1 + k x inclinação + custo extra da célula de chegada):
 *   subir morro "custa mais", então o caminho prefere contornar.
 * - Heurística octile: a menor distância possível andando em 8 direções sem obstáculos
 *   (diagonal enquanto dá, depois reto). Ela nunca passa do custo real (que só pode ser maior),
 *   por isso o A* acha o caminho ótimo explorando bem menos células que uma busca cega.
 *
 * Cada célula aberta vai para uma fila de prioridade ordenada por f = g + h (custo até aqui +
 * estimativa até o fim). Quando o destino sai da fila, o caminho é refeito de trás para a frente
 * pelos "pais". Só dados (dá para testar).
 */
public final class AEstrela {

	/** O caminho achado: as células, do início ao fim, o custo (com a inclinação) e o comprimento em metros. */
	public record Caminho(List<int[]> celulas, float custo, float comprimento) {}

	private static final float RAIZ2 = (float) Math.sqrt(2);

	private AEstrela() {}

	/** O caminho entre duas células, ou null se não existe. k = peso da inclinação no custo. */
	public static Caminho buscar(GradeCaminhada g, int i0, int j0, int i1, int j1, float k) {
		if (!g.caminhavel(i0, j0) || !g.caminhavel(i1, j1) || g.componente(i0, j0) != g.componente(i1, j1)) {
			return null; // nem tenta: as componentes já dizem que não há caminho
		}
		int n = g.lado * g.lado;
		float[] custo = new float[n];
		java.util.Arrays.fill(custo, Float.MAX_VALUE);
		int[] pai = new int[n];
		boolean[] fechado = new boolean[n];
		// na fila vão pares {f, célula}; a mesma célula pode entrar várias vezes (fica a de menor f)
		PriorityQueue<float[]> fila = new PriorityQueue<>((a, b) -> Float.compare(a[0], b[0]));

		int inicio = j0 * g.lado + i0, fim = j1 * g.lado + i1;
		custo[inicio] = 0;
		pai[inicio] = -1;
		fila.add(new float[] { octile(i0, j0, i1, j1), inicio });
		while (!fila.isEmpty()) {
			int atual = (int) fila.poll()[1];
			if (fechado[atual]) continue;
			if (atual == fim) break;
			fechado[atual] = true;
			int i = atual % g.lado, j = atual / g.lado;
			for (int dj = -1; dj <= 1; dj++) {
				for (int di = -1; di <= 1; di++) {
					if (di == 0 && dj == 0) continue;
					int ni = i + di, nj = j + dj;
					if (!g.caminhavel(ni, nj)) continue;
					// na diagonal, as duas células do "canto" precisam estar livres (não corta quina)
					if (di != 0 && dj != 0 && (!g.caminhavel(i + di, j) || !g.caminhavel(i, j + dj))) continue;
					int v = nj * g.lado + ni;
					if (fechado[v]) continue;
					float passo = (di != 0 && dj != 0 ? RAIZ2 : 1) * (1 + k * g.inclinacao(ni, nj) + g.custoExtra(ni, nj));
					float novo = custo[atual] + passo;
					if (novo < custo[v]) {
						custo[v] = novo;
						pai[v] = atual;
						fila.add(new float[] { novo + octile(ni, nj, i1, j1), v });
					}
				}
			}
		}
		if (custo[fim] == Float.MAX_VALUE) return null;

		List<int[]> celulas = new ArrayList<>();
		float comprimento = 0;
		for (int c = fim; c != -1; c = pai[c]) {
			celulas.add(new int[] { c % g.lado, c / g.lado });
			if (pai[c] != -1) {
				int a = pai[c];
				comprimento += (a % g.lado != c % g.lado && a / g.lado != c / g.lado) ? RAIZ2 : 1;
			}
		}
		Collections.reverse(celulas);
		return new Caminho(celulas, custo[fim] * g.celula, comprimento * g.celula);
	}

	/** O mesmo, com as pontas em metros. */
	public static Caminho buscar(GradeCaminhada g, float x0, float z0, float x1, float z1, float k) {
		return buscar(g, g.celulaDe(x0), g.celulaDe(z0), g.celulaDe(x1), g.celulaDe(z1), k);
	}

	/** Distância octile: em 8 direções, diagonal enquanto dá e reto no resto. */
	static float octile(int i0, int j0, int i1, int j1) {
		int dx = Math.abs(i1 - i0), dy = Math.abs(j1 - j0);
		return Math.max(dx, dy) + (RAIZ2 - 1) * Math.min(dx, dy);
	}
}
