package md2;

import java.util.ArrayList;
import java.util.List;

/**
 * O MD2 pronto para a placa de vídeo, ainda sem OpenGL (dá para testar):
 *
 * 1. DESDOBRADO: no arquivo, posição e textura têm índices separados (o mesmo canto pode
 *    ter uma posição compartilhada e coordenadas de textura diferentes). Então cada canto
 *    de cada triângulo vira um vértice próprio: triângulos x 3 vértices, na mesma ordem
 *    em todos os quadros. É isso que permite interpolar o quadro A com o B vértice a vértice.
 * 2. Eixos convertidos: o Quake usa Z para cima; aqui Y é para cima. (x, y, z) vira (x, z, -y),
 *    uma rotação (não espelha, então a ordem dos vértices dos triângulos se mantém).
 * 3. Em metros, com os pés no y = 0 (o Ajuste, calculado pelo primeiro quadro).
 * 4. Normais calculadas pela geometria de cada quadro.
 */
public final class MalhaMd2 {

	/**
	 * Escala e deslocamento aplicados depois de converter os eixos. Calculado a partir do
	 * corpo e reaproveitado na arma, para as duas peças continuarem encaixadas.
	 */
	public record Ajuste(float escala, float deslocamentoY) {}

	public final int vertices;            // triângulos x 3
	public final List<String> nomes = new ArrayList<>();
	public final List<float[]> posicoes = new ArrayList<>(); // por quadro: vertices x 3
	public final List<float[]> normais = new ArrayList<>();  // por quadro: vertices x 3
	public final float[] uvs;                                 // vertices x 2 (igual em todos os quadros)
	public final List<Animacao> animacoes;

	/** O ajuste que deixa o modelo com "altura" metros e os pés no chão, medido no primeiro quadro. */
	public static Ajuste ajustePara(ArquivoMd2 arquivo, float altura) {
		float[] p = arquivo.quadros().get(0).posicoes();
		float minimo = Float.MAX_VALUE, maximo = -Float.MAX_VALUE;
		for (int i = 2; i < p.length; i += 3) { // o z do Quake é a altura
			minimo = Math.min(minimo, p[i]);
			maximo = Math.max(maximo, p[i]);
		}
		float escala = altura / (maximo - minimo);
		return new Ajuste(escala, -minimo * escala);
	}

	public MalhaMd2(ArquivoMd2 arquivo, Ajuste ajuste) {
		int triangulos = arquivo.numeroTriangulos();
		vertices = triangulos * 3;
		int[] tv = arquivo.verticesTriangulo();
		int[] tt = arquivo.texturaTriangulo();

		// coordenadas de textura: de pixels para 0..1. O t do MD2 conta a partir do topo da
		// imagem, igual ao TextureLoader (a primeira linha da imagem é t = 0): não precisa inverter.
		uvs = new float[vertices * 2];
		for (int i = 0; i < vertices; i++) {
			uvs[i * 2] = arquivo.s()[tt[i]] / (float) arquivo.larguraSkin();
			uvs[i * 2 + 1] = arquivo.t()[tt[i]] / (float) arquivo.alturaSkin();
		}

		for (ArquivoMd2.Quadro q : arquivo.quadros()) {
			float[] convertidas = converter(q.posicoes(), ajuste);
			float[] normaisPosicao = calcularNormais(convertidas, tv, arquivo.numeroPosicoes());
			float[] p = new float[vertices * 3];
			float[] n = new float[vertices * 3];
			for (int i = 0; i < vertices; i++) {
				int v = tv[i];
				System.arraycopy(convertidas, v * 3, p, i * 3, 3);
				System.arraycopy(normaisPosicao, v * 3, n, i * 3, 3);
			}
			nomes.add(q.nome());
			posicoes.add(p);
			normais.add(n);
		}
		animacoes = Animacao.agrupar(nomes);
	}

	public int quadros() {
		return posicoes.size();
	}

	/** Procura uma animação pelo nome exato. */
	public Animacao animacao(String nome) {
		for (Animacao a : animacoes) {
			if (a.nome().equals(nome)) return a;
		}
		return null;
	}

	/** Todas as animações cujo nome começa com o prefixo (ex.: "pain" acha painaupp, painb, painc). */
	public List<Animacao> animacoesQueComecamCom(String prefixo) {
		List<Animacao> lista = new ArrayList<>();
		for (Animacao a : animacoes) {
			if (a.nome().startsWith(prefixo)) lista.add(a);
		}
		return lista;
	}

	/** (x, y, z) do Quake vira (x, z, -y), em metros, com os pés no chão. */
	static float[] converter(float[] quake, Ajuste ajuste) {
		float[] saida = new float[quake.length];
		for (int i = 0; i < quake.length; i += 3) {
			saida[i] = quake[i] * ajuste.escala();
			saida[i + 1] = quake[i + 2] * ajuste.escala() + ajuste.deslocamentoY();
			saida[i + 2] = -quake[i + 1] * ajuste.escala();
		}
		return saida;
	}

	/**
	 * Normal de cada posição: a soma das normais das faces que a usam, normalizada no fim.
	 * Sem normalizar cada face, as faces maiores pesam mais, o que dá normais melhores.
	 * O MD2 guarda os triângulos no sentido horário (vistos de fora), então o produto
	 * vetorial é feito na ordem (C - A) x (B - A) para a normal apontar para fora.
	 */
	static float[] calcularNormais(float[] p, int[] triangulos, int numeroPosicoes) {
		float[] soma = new float[numeroPosicoes * 3];
		for (int t = 0; t < triangulos.length; t += 3) {
			int a = triangulos[t] * 3, b = triangulos[t + 1] * 3, c = triangulos[t + 2] * 3;
			float ux = p[c] - p[a], uy = p[c + 1] - p[a + 1], uz = p[c + 2] - p[a + 2];
			float vx = p[b] - p[a], vy = p[b + 1] - p[a + 1], vz = p[b + 2] - p[a + 2];
			float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
			for (int k : new int[] { a, b, c }) {
				soma[k] += nx;
				soma[k + 1] += ny;
				soma[k + 2] += nz;
			}
		}
		for (int i = 0; i < soma.length; i += 3) {
			float t = (float) Math.sqrt(soma[i] * soma[i] + soma[i + 1] * soma[i + 1] + soma[i + 2] * soma[i + 2]);
			if (t > 1e-8f) {
				soma[i] /= t; soma[i + 1] /= t; soma[i + 2] /= t;
			} else {
				soma[i] = 0; soma[i + 1] = 1; soma[i + 2] = 0;
			}
		}
		return soma;
	}
}
