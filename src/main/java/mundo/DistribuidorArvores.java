package mundo;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import terreno.RuidoFractal;

/**
 * Decide onde fica cada árvore. Só dados: roda na thread de carregamento.
 *
 * 1. Amostragem de Poisson em disco (algoritmo de Bridson): pontos espalhados ao acaso,
 *    mas nunca a menos de 2,5 m uns dos outros. Fica natural, sem fileiras e sem montes.
 * 2. Cada ponto fica ou não conforme um mapa de densidade feito de ruído fractal: bosques
 *    fechados em alguns lugares, áreas abertas em outros.
 * 3. Na faixa da borda, a densidade é máxima: uma muralha que esconde o fim do mapa.
 * 4. Nada de árvores na água, na beira dos lagos, em declives fortes nem nas clareiras
 *    pedidas (o início do jogador e, depois, as provas e a Univali).
 *
 * A mesma seed dá sempre as mesmas árvores.
 */
public final class DistribuidorArvores {

	/** Uma árvore: posição (y = chão), tamanho, giro em radianos, brilho e qual das texturas. */
	public record Arvore(float x, float y, float z, float altura, float largura, float rotacao, float brilho,
			int textura) {}

	/** Um círculo onde não pode haver árvore. */
	public record Clareira(float x, float z, float raio) {}

	public static final float DISTANCIA_MINIMA = 2.5f;
	/** Largura, em metros a partir da borda, da faixa com densidade máxima. */
	public static final float FAIXA_MURALHA = 24f;
	public static final float RAIO_TRONCO = 0.35f;
	/** Quantas texturas de árvore existem (pinheiro, abeto, árvore seca, copa redonda). */
	public static final int VARIACOES = 4;

	/**
	 * Para cada variação (pinheiro, abeto, seca, copa redonda): a proporção largura / altura
	 * da imagem texturas/arvore_N.png e a faixa de altura em metros. A imagem preenche o
	 * pedaço do atlas inteiro; é a largura do plano no mundo que devolve a forma certa.
	 */
	private static final float[] PROPORCAO = { 0.61f, 0.35f, 0.70f, 0.98f };
	private static final float[] ALTURA_MINIMA = { 8f, 9f, 6f, 6f };
	private static final float[] ALTURA_MAXIMA = { 15f, 16f, 11f, 10f };
	private static final float DECLIVE_MAXIMO = 32f;  // graus
	private static final float ALTURA_MINIMA_CHAO = 0.25f; // m acima da água (nada na margem dos lagos)

	private DistribuidorArvores() {}

	public static List<Arvore> distribuir(ConsultaAltura chao, long seed, List<Clareira> clareiras) {
		return distribuir(chao, seed, clareiras, null);
	}

	/**
	 * O mesmo, com as regiões da floresta (ou null, para uma floresta só): cada região muda a
	 * densidade e quais árvores são mais comuns.
	 */
	public static List<Arvore> distribuir(ConsultaAltura chao, long seed, List<Clareira> clareiras, Regioes regioes) {
		return distribuir(chao, seed, clareiras, regioes, 1f);
	}

	/**
	 * O mesmo, com a densidade escolhida nas opções (0,5 a 1,5): multiplica a chance de cada
	 * árvore no interior; a muralha da borda continua cheia (é ela que esconde o fim do mapa).
	 */
	public static List<Arvore> distribuir(ConsultaAltura chao, long seed, List<Clareira> clareiras, Regioes regioes,
			float densidadeOpcao) {
		Random random = new Random(seed * 31 + 7);
		RuidoFractal ruido = new RuidoFractal(seed + 99);
		float tamanho = chao.tamanho();

		List<Arvore> arvores = new ArrayList<>();
		for (float[] p : poisson(tamanho, DISTANCIA_MINIMA, random)) {
			float x = p[0], z = p[1];
			// o sorteio da densidade vem antes dos filtros, para a sequência de números
			// aleatórios não depender deles (mesma seed, mesmo resultado)
			Regioes.Tipo regiao = regioes != null ? regioes.em(x, z) : null;
			float distanciaBorda = Math.min(Math.min(x, z), Math.min(tamanho - x, tamanho - z));
			float opcao = distanciaBorda < FAIXA_MURALHA ? 1 : densidadeOpcao;
			boolean fica = random.nextFloat() < densidade(x, z, tamanho, ruido) * fatorDensidade(regiao, x, z, tamanho) * opcao;
			if (!fica || dentroDeClareira(x, z, clareiras)) continue;
			if (chao.alturaEm(x, z) < ALTURA_MINIMA_CHAO) continue;  // água e margem
			if (chao.anguloEm(x, z) > DECLIVE_MAXIMO) continue;

			int textura = regiao != null ? sortearTextura(regiao, random) : sortearTextura(random);
			float altura = ALTURA_MINIMA[textura] + random.nextFloat() * (ALTURA_MAXIMA[textura] - ALTURA_MINIMA[textura]);
			float largura = altura * PROPORCAO[textura] * (0.9f + random.nextFloat() * 0.2f);
			float rotacao = random.nextFloat() * (float) Math.PI;
			float brilho = 0.75f + random.nextFloat() * 0.35f;
			arvores.add(new Arvore(x, chao.alturaEm(x, z) - 0.15f, z, altura, largura, rotacao, brilho, textura));
		}
		return arvores;
	}

	/** Coloca um tronco na grade de colisão para cada árvore. */
	public static void registrarTroncos(List<Arvore> arvores, GradeColisao grade) {
		for (Arvore a : arvores) {
			grade.adicionar(a.x(), a.z(), RAIO_TRONCO);
		}
	}

	/**
	 * Densidade de 0 a 1 num ponto: ruído fractal em manchas de ~70 m (bosques e áreas
	 * abertas), e 1 na faixa da borda.
	 */
	static float densidade(float x, float z, float tamanho, RuidoFractal ruido) {
		float manchas = (float) ruido.fbm(x / 70.0, z / 70.0, 4);
		float interior = 0.06f + 0.86f * suave(-0.20f, 0.30f, manchas);
		float distanciaBorda = Math.min(Math.min(x, z), Math.min(tamanho - x, tamanho - z));
		float muralha = 1 - suave(FAIXA_MURALHA - 6, FAIXA_MURALHA, distanciaBorda);
		return Math.max(interior, muralha);
	}

	/** Pinheiros são os mais comuns; árvores secas e de copa redonda aparecem de vez em quando. */
	private static int sortearTextura(Random random) {
		float r = random.nextFloat();
		if (r < 0.40f) return 0;
		if (r < 0.72f) return 1;
		if (r < 0.86f) return 2;
		return 3;
	}

	/** A região muda a densidade no interior; na muralha da borda, nada muda (ela esconde o fim do mapa). */
	private static float fatorDensidade(Regioes.Tipo regiao, float x, float z, float tamanho) {
		if (regiao == null) return 1;
		float distanciaBorda = Math.min(Math.min(x, z), Math.min(tamanho - x, tamanho - z));
		if (distanciaBorda < FAIXA_MURALHA) return 1;
		return switch (regiao) {
		case PINHEIRAL -> 1.15f;
		case BOSQUE -> 0.85f;
		case MATA_MORTA -> 0.55f;
		case PEDREGAL -> 0.3f;
		};
	}

	/** Árvores de cada região: 0 pinheiro, 1 abeto, 2 seca, 3 copa redonda. */
	private static int sortearTextura(Regioes.Tipo regiao, Random random) {
		float[] chances = switch (regiao) {
		case PINHEIRAL -> new float[] { 0.55f, 0.40f, 0.04f, 0.01f };
		case MATA_MORTA -> new float[] { 0.12f, 0.06f, 0.75f, 0.07f };
		case BOSQUE -> new float[] { 0.18f, 0.12f, 0.05f, 0.65f };
		case PEDREGAL -> new float[] { 0.35f, 0.30f, 0.25f, 0.10f };
		};
		float r = random.nextFloat(), soma = 0;
		for (int i = 0; i < chances.length; i++) {
			soma += chances[i];
			if (r < soma) return i;
		}
		return 0;
	}

	private static boolean dentroDeClareira(float x, float z, List<Clareira> clareiras) {
		for (Clareira c : clareiras) {
			float dx = x - c.x(), dz = z - c.z();
			if (dx * dx + dz * dz < c.raio() * c.raio()) return true;
		}
		return false;
	}

	/**
	 * Amostragem de Poisson em disco (Bridson, 2007). Uma grade de células de lado r/√2
	 * (cabe no máximo um ponto em cada) torna rápida a pergunta "tem alguém perto demais?".
	 * Começa de um ponto; para cada ponto "ativo", tenta até 30 vizinhos no anel entre r e 2r;
	 * se nenhum couber, o ponto deixa de ser ativo.
	 */
	static List<float[]> poisson(float tamanho, float r, Random random) {
		float celula = r / (float) Math.sqrt(2);
		int n = (int) Math.ceil(tamanho / celula);
		int[] grade = new int[n * n];
		java.util.Arrays.fill(grade, -1);
		List<float[]> pontos = new ArrayList<>();
		List<Integer> ativos = new ArrayList<>();

		float[] primeiro = { random.nextFloat() * tamanho, random.nextFloat() * tamanho };
		pontos.add(primeiro);
		ativos.add(0);
		grade[(int) (primeiro[1] / celula) * n + (int) (primeiro[0] / celula)] = 0;

		while (!ativos.isEmpty()) {
			int k = random.nextInt(ativos.size());
			float[] base = pontos.get(ativos.get(k));
			boolean achou = false;
			for (int tentativa = 0; tentativa < 30; tentativa++) {
				double angulo = random.nextDouble() * 2 * Math.PI;
				double distancia = r * (1 + random.nextDouble());
				float x = base[0] + (float) (Math.cos(angulo) * distancia);
				float z = base[1] + (float) (Math.sin(angulo) * distancia);
				if (x < 0 || z < 0 || x >= tamanho || z >= tamanho) continue;
				int ci = (int) (x / celula), cj = (int) (z / celula);
				if (temVizinhoPerto(x, z, ci, cj, n, grade, pontos, r)) continue;
				grade[cj * n + ci] = pontos.size();
				ativos.add(pontos.size());
				pontos.add(new float[] { x, z });
				achou = true;
				break;
			}
			if (!achou) {
				ativos.remove(k);
			}
		}
		return pontos;
	}

	private static boolean temVizinhoPerto(float x, float z, int ci, int cj, int n, int[] grade, List<float[]> pontos,
			float r) {
		for (int j = Math.max(0, cj - 2); j <= Math.min(n - 1, cj + 2); j++) {
			for (int i = Math.max(0, ci - 2); i <= Math.min(n - 1, ci + 2); i++) {
				int indice = grade[j * n + i];
				if (indice >= 0) {
					float[] p = pontos.get(indice);
					float dx = p[0] - x, dz = p[1] - z;
					if (dx * dx + dz * dz < r * r) return true;
				}
			}
		}
		return false;
	}

	private static float suave(float inicio, float fim, float x) {
		float t = Math.max(0, Math.min(1, (x - inicio) / (fim - inicio)));
		return t * t * (3 - 2 * t);
	}
}
