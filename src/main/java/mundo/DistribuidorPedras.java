package mundo;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Decide onde ficam as pedras. Só dados: roda na thread de carregamento, antes das árvores
 * (as pedras grandes pedem uma clareira, para nenhuma árvore nascer dentro delas).
 *
 * Três tipos:
 *  - grandes (2 a 7 m), poucas e espalhadas: servem de ponto de referência, e têm colisão;
 *  - na beira dos lagos: marcam a margem e deixam a água mais fácil de notar;
 *  - pedrinhas soltas pelo chão, só para enfeitar.
 * A mesma seed dá sempre as mesmas pedras.
 */
public final class DistribuidorPedras {

	/** Uma pedra: posição (y já um pouco enterrado), escala, giro, qual modelo e se tem colisão. */
	public record Pedra(float x, float y, float z, float escala, float giro, int modelo, boolean solida) {}

	public static final int GRANDES = 28, MARGEM_LAGO = 140, SOLTAS = 320;

	private DistribuidorPedras() {}

	/**
	 * @param larguraModelo largura de cada modelo de pedra em metros (para a colisão e o espaço entre elas)
	 * @param clareiras     lugares onde não pode haver pedra (o início, as provas, as placas)
	 */
	public static List<Pedra> distribuir(ConsultaAltura chao, float[] larguraModelo, List<DistribuidorArvores.Clareira> clareiras,
			long seed) {
		Random random = new Random(seed * 13 + 5);
		List<Pedra> pedras = new ArrayList<>();
		float t = chao.tamanho();

		// grandes: longe umas das outras, em terra firme, fora da muralha da borda
		for (int tentativa = 0; tentativa < 3000 && contar(pedras, true) < GRANDES; tentativa++) {
			float x = 30 + random.nextFloat() * (t - 60), z = 30 + random.nextFloat() * (t - 60);
			int modelo = random.nextInt(larguraModelo.length);
			float escala = 2.2f + random.nextFloat() * 2.4f;
			float raio = larguraModelo[modelo] * escala / 2;
			if (chao.ehAgua(x, z) || livre(x, z, raio + 2, clareiras) == false) continue;
			if (pertoDeOutra(pedras, x, z, 25)) continue;
			pedras.add(nova(chao, x, z, escala, modelo, random, true));
		}

		// beira dos lagos: terra firme baixa, com água a até 2,5 m
		for (int tentativa = 0; tentativa < 20000 && pedras.size() < contar(pedras, true) + MARGEM_LAGO; tentativa++) {
			float x = 15 + random.nextFloat() * (t - 30), z = 15 + random.nextFloat() * (t - 30);
			float h = chao.alturaEm(x, z);
			if (h < 0.03f || h > 0.5f || !aguaPerto(chao, x, z, 2.5f)) continue;
			if (!livre(x, z, 1, clareiras) || pertoDeOutra(pedras, x, z, 2.2f)) continue;
			float escala = 0.6f + random.nextFloat() * 1.0f;
			pedras.add(nova(chao, x, z, escala, random.nextInt(larguraModelo.length), random, escala > 1.2f));
		}

		// soltas pelo chão
		int antes = pedras.size();
		for (int tentativa = 0; tentativa < 5000 && pedras.size() < antes + SOLTAS; tentativa++) {
			float x = 15 + random.nextFloat() * (t - 30), z = 15 + random.nextFloat() * (t - 30);
			if (chao.alturaEm(x, z) < 0.05f || !livre(x, z, 1, clareiras) || pertoDeOutra(pedras, x, z, 3)) continue;
			pedras.add(nova(chao, x, z, 0.35f + random.nextFloat() * 0.6f, random.nextInt(larguraModelo.length), random,
					false));
		}
		return pedras;
	}

	/** Um pouco enterrada (15% da escala), para não flutuar no lado baixo de um declive. */
	private static Pedra nova(ConsultaAltura chao, float x, float z, float escala, int modelo, Random random,
			boolean solida) {
		return new Pedra(x, chao.alturaEm(x, z) - 0.15f * escala, z, escala, random.nextFloat() * 6.283f, modelo, solida);
	}

	/** Clareira para as árvores em volta de cada pedra sólida (o tronco não nasce dentro dela). */
	public static List<DistribuidorArvores.Clareira> clareiras(List<Pedra> pedras, float[] larguraModelo) {
		List<DistribuidorArvores.Clareira> lista = new ArrayList<>();
		for (Pedra p : pedras) {
			if (p.solida()) lista.add(new DistribuidorArvores.Clareira(p.x(), p.z(), raio(p, larguraModelo) + 0.5f));
		}
		return lista;
	}

	/** O raio de colisão de uma pedra: um pouco menos que a metade da largura (a pedra é arredondada). */
	public static float raio(Pedra p, float[] larguraModelo) {
		return larguraModelo[p.modelo()] * p.escala() * 0.42f;
	}

	/** Os círculos das pedras sólidas na grade de colisão. */
	public static void registrar(List<Pedra> pedras, float[] larguraModelo, GradeColisao grade) {
		for (Pedra p : pedras) {
			if (p.solida()) grade.adicionar(p.x(), p.z(), raio(p, larguraModelo));
		}
	}

	private static boolean aguaPerto(ConsultaAltura chao, float x, float z, float r) {
		return chao.ehAgua(x + r, z) || chao.ehAgua(x - r, z) || chao.ehAgua(x, z + r) || chao.ehAgua(x, z - r);
	}

	private static boolean livre(float x, float z, float folga, List<DistribuidorArvores.Clareira> clareiras) {
		for (DistribuidorArvores.Clareira c : clareiras) {
			float r = c.raio() + folga;
			if ((x - c.x()) * (x - c.x()) + (z - c.z()) * (z - c.z()) < r * r) return false;
		}
		return true;
	}

	private static boolean pertoDeOutra(List<Pedra> pedras, float x, float z, float distancia) {
		for (Pedra p : pedras) {
			if ((x - p.x()) * (x - p.x()) + (z - p.z()) * (z - p.z()) < distancia * distancia) return true;
		}
		return false;
	}

	private static int contar(List<Pedra> pedras, boolean grandes) {
		int n = 0;
		for (Pedra p : pedras) {
			if (grandes && p.escala() >= 2.2f) n++;
		}
		return n;
	}
}
