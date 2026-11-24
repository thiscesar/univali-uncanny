package mundo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lê arquivos .obj de texto, como os que o gerador de árvores escreve. Só dados: não usa OpenGL.
 *
 * O leitor de OBJ do projeto original não serve para eles: os vértices trazem cor
 * ("v x y z r g b", a sombra assada nos cantos), não há normais ("vn") e as faces são
 * "f a/a b/b c/c". Este leitor entende:
 *  - v x y z [r g b]   posição e, se houver, cor do vértice (sem cor, branco)
 *  - vt u v            coordenada de textura
 *  - f ...             faces com "a", "a/b", "a/b/c" ou "a//c"; índices negativos contam do fim;
 *                      faces com mais de 3 cantos viram um leque de triângulos
 *  - usemtl nome       o material das faces seguintes
 * O resto (o, g, s, mtllib, vn, comentários) é ignorado.
 *
 * As normais são calculadas: cada vértice recebe a média das normais das faces que o usam,
 * pesada pela área, e por isso a luz passa suave de uma face para a outra.
 *
 * Os vértices saem "desdobrados" (cada triângulo com os seus 3), agrupados por material,
 * prontos para um VBO e um glDrawArrays por material.
 */
public final class LeitorObj {

	/** Floats por vértice desdobrado: posição 3, cor 3, normal 3, uv 2. */
	public static final int FLOATS_VERTICE = 11;

	/** Um material: os vértices de "primeiro" até "primeiro + quantidade - 1". */
	public record Grupo(String material, int primeiro, int quantidade) {}

	/** O modelo lido. "altura" é o maior y, para saber quanto escalar. */
	public record ModeloObj(float[] vertices, List<Grupo> grupos, float altura) {
		public int quantidadeVertices() {
			return vertices.length / FLOATS_VERTICE;
		}
	}

	private LeitorObj() {}

	public static ModeloObj ler(String caminho) throws IOException {
		return ler(Files.readAllLines(Path.of(caminho), StandardCharsets.UTF_8));
	}

	public static ModeloObj ler(List<String> linhas) {
		List<float[]> posicoes = new ArrayList<>();
		List<float[]> cores = new ArrayList<>();
		List<float[]> uvs = new ArrayList<>();
		// para cada material, os triângulos na ordem: {posição, uv} de cada canto (uv -1 = sem)
		Map<String, List<int[]>> triangulos = new LinkedHashMap<>();
		String material = "padrao";

		for (String linha : linhas) {
			String[] p = linha.trim().split("\\s+");
			switch (p[0]) {
			case "v" -> {
				posicoes.add(new float[] { f(p[1]), f(p[2]), f(p[3]) });
				cores.add(p.length >= 7 ? new float[] { f(p[4]), f(p[5]), f(p[6]) } : new float[] { 1, 1, 1 });
			}
			case "vt" -> uvs.add(new float[] { f(p[1]), p.length > 2 ? f(p[2]) : 0 });
			case "usemtl" -> material = p.length > 1 ? p[1] : "padrao";
			case "f" -> {
				int cantos = p.length - 1;
				int[][] c = new int[cantos][];
				for (int i = 0; i < cantos; i++) {
					c[i] = canto(p[i + 1], posicoes.size(), uvs.size());
				}
				List<int[]> lista = triangulos.computeIfAbsent(material, k -> new ArrayList<>());
				for (int i = 1; i + 1 < cantos; i++) { // leque: (0, 1, 2), (0, 2, 3)...
					lista.add(new int[] { c[0][0], c[0][1], c[i][0], c[i][1], c[i + 1][0], c[i + 1][1] });
				}
			}
			default -> { }
			}
		}

		float[][] normais = normais(posicoes, triangulos);
		int total = 0;
		for (List<int[]> lista : triangulos.values()) {
			total += lista.size() * 3;
		}
		float[] v = new float[total * FLOATS_VERTICE];
		List<Grupo> grupos = new ArrayList<>();
		int k = 0, vertice = 0;
		float altura = 0;
		for (Map.Entry<String, List<int[]>> e : triangulos.entrySet()) {
			int primeiro = vertice;
			for (int[] t : e.getValue()) {
				for (int canto = 0; canto < 3; canto++) {
					int ip = t[canto * 2], iu = t[canto * 2 + 1];
					float[] pos = posicoes.get(ip), cor = cores.get(ip), n = normais[ip];
					float[] uv = iu >= 0 ? uvs.get(iu) : new float[] { 0, 0 };
					v[k++] = pos[0]; v[k++] = pos[1]; v[k++] = pos[2];
					v[k++] = cor[0]; v[k++] = cor[1]; v[k++] = cor[2];
					v[k++] = n[0]; v[k++] = n[1]; v[k++] = n[2];
					// no .obj, v = 0 é a base da imagem; no OpenGL do projeto, t = 0 é a primeira linha (o topo)
					v[k++] = uv[0]; v[k++] = 1 - uv[1];
					altura = Math.max(altura, pos[1]);
					vertice++;
				}
			}
			grupos.add(new Grupo(e.getKey(), primeiro, vertice - primeiro));
		}
		return new ModeloObj(v, grupos, altura);
	}

	/** "a", "a/b", "a/b/c" ou "a//c" -> {índice da posição, índice do uv ou -1}, contando de 0. */
	private static int[] canto(String texto, int quantidadePosicoes, int quantidadeUvs) {
		String[] partes = texto.split("/");
		int posicao = indice(partes[0], quantidadePosicoes);
		int uv = partes.length > 1 && !partes[1].isEmpty() ? indice(partes[1], quantidadeUvs) : -1;
		return new int[] { posicao, uv };
	}

	/** No .obj os índices começam em 1; negativos contam a partir do último lido (-1 = o último). */
	private static int indice(String texto, int quantidade) {
		int i = Integer.parseInt(texto);
		return i < 0 ? quantidade + i : i - 1;
	}

	/** Normal de cada posição: soma dos produtos vetoriais das faces que a usam, normalizada. */
	private static float[][] normais(List<float[]> posicoes, Map<String, List<int[]>> triangulos) {
		float[][] n = new float[posicoes.size()][3];
		for (List<int[]> lista : triangulos.values()) {
			for (int[] t : lista) {
				float[] a = posicoes.get(t[0]), b = posicoes.get(t[2]), c = posicoes.get(t[4]);
				float ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
				float vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
				// (B - A) x (C - A): o tamanho é o dobro da área, então faces grandes pesam mais
				float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
				for (int canto = 0; canto < 3; canto++) {
					float[] soma = n[t[canto * 2]];
					soma[0] += nx; soma[1] += ny; soma[2] += nz;
				}
			}
		}
		for (float[] v : n) {
			float tamanho = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
			if (tamanho < 1e-9f) {
				v[0] = 0; v[1] = 1; v[2] = 0; // vértice solto: aponta para cima
			} else {
				v[0] /= tamanho; v[1] /= tamanho; v[2] /= tamanho;
			}
		}
		return n;
	}

	private static float f(String s) {
		return Float.parseFloat(s);
	}
}
