package obj;

import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL11.glDrawElements;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import org.lwjgl.BufferUtils;

/**
 * A malha do terreno do ObjHTGsrtm (do professor), montada de um jeito mais barato para a placa.
 * A classe original não muda: esta a estende e troca só o load() e o draw().
 *
 * 1. VÉRTICES ÚNICOS E ÍNDICES. O original repete cada ponto da grade em até 6 triângulos: são
 *    (lado-1)² x 6 vértices (540 mil no mapa de 301 x 301), e o vertex shader roda para cada um.
 *    Aqui cada ponto vira um vértice só (90.601), e os triângulos dizem quais pontos usam por uma
 *    lista de índices (glDrawElements). A placa guarda os vértices já transformados e reaproveita
 *    nos triângulos vizinhos. Os triângulos e os atributos de cada ponto (posição, altura real,
 *    normal, coordenada) são exatamente os mesmos do original: a imagem não muda.
 * 2. BLOCOS. Os índices ficam agrupados em blocos de BLOCO x BLOCO células (20 x 20 m). Antes de
 *    desenhar, cada bloco é testado: longe demais ou inteiro atrás de quem olha, ele é pulado.
 *    Na cena, "quem olha" é a câmera; no mapa de sombra, a lanterna, que alcança só 30 m.
 *
 * Os atributos seguem a convenção do projeto (0 posição, 1 cor, 2 normal, 3 textura).
 */
public class ObjHTGsrtmIndexado extends ObjHTGsrtm {

	/** Lado de um bloco, em células da grade. */
	public static final int BLOCO = 20;

	private final float passoXZ, metrosPorUnidade;
	private int vboPosicao, vboCor, vboNormal, vboTextura, vboIndices;
	private int blocosPorLado;
	/** Onde começa cada bloco na lista de índices (o último valor é o total). */
	private int[] inicioBloco;

	// de onde se olha, para pular os blocos que não aparecem (definirVista, a cada desenho)
	private float olhoX, olhoZ, frenteX, frenteZ, alcance = Float.MAX_VALUE;
	private int blocosDesenhados;

	public ObjHTGsrtmIndexado(int[][] alturas, float passoXZ, float metrosPorUnidade) {
		super(alturas, passoXZ, metrosPorUnidade);
		this.passoXZ = passoXZ;
		this.metrosPorUnidade = metrosPorUnidade;
	}

	/**
	 * De onde se olha: a posição (x, z), a direção no chão (não precisa ser unitária; quase zero =
	 * olhando reto para cima ou para baixo, e aí só a distância conta) e até onde se enxerga.
	 */
	public void definirVista(float x, float z, float dirX, float dirZ, float alcance) {
		float t = (float) Math.sqrt(dirX * dirX + dirZ * dirZ);
		olhoX = x;
		olhoZ = z;
		frenteX = t > 0.2f ? dirX / t : 0;
		frenteZ = t > 0.2f ? dirZ / t : 0;
		this.alcance = alcance;
	}

	/** Quantos blocos entraram no último desenho (para conferência). */
	public int blocosDesenhados() {
		return blocosDesenhados;
	}

	/** A mesma altura da malha original: abaixo da água, plana no nível 0. */
	private float alturaVisual(int i, int j) {
		i = Math.max(0, Math.min(wh - 1, i));
		j = Math.max(0, Math.min(wh - 1, j));
		return Math.max(data[j][i], 0) * metrosPorUnidade;
	}

	@Override
	public void load() {
		int pontos = wh * wh;
		FloatBuffer posicao = BufferUtils.createFloatBuffer(pontos * 3);
		FloatBuffer cor = BufferUtils.createFloatBuffer(pontos * 3);
		FloatBuffer normal = BufferUtils.createFloatBuffer(pontos * 3);
		FloatBuffer textura = BufferUtils.createFloatBuffer(pontos * 2);
		for (int j = 0; j < wh; j++) {
			for (int i = 0; i < wh; i++) {
				posicao.put(i * passoXZ).put(alturaVisual(i, j)).put(j * passoXZ);
				cor.put(data[j][i] * metrosPorUnidade / 1000f).put(0).put(0); // a altura real, como no original
				// a normal suave, pelas diferenças centrais entre os vizinhos (igual ao normalEm do original)
				float nx = alturaVisual(i - 1, j) - alturaVisual(i + 1, j);
				float ny = 2 * passoXZ;
				float nz = alturaVisual(i, j - 1) - alturaVisual(i, j + 1);
				float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
				normal.put(nx / len).put(ny / len).put(nz / len);
				textura.put(i).put(j);
			}
		}
		Indices ind = indices(wh, BLOCO);
		blocosPorLado = ind.blocosPorLado();
		inicioBloco = ind.inicioBloco();
		IntBuffer indices = BufferUtils.createIntBuffer(ind.indices().length).put(ind.indices());

		posicao.flip();
		cor.flip();
		normal.flip();
		textura.flip();
		indices.flip();
		vboPosicao = enviar(posicao);
		vboCor = enviar(cor);
		vboNormal = enviar(normal);
		vboTextura = enviar(textura);
		vboIndices = glGenBuffers();
		glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, vboIndices);
		glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW);
		glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
	}

	/** Os índices dos triângulos, bloco por bloco, e onde começa cada bloco. */
	public record Indices(int[] indices, int[] inicioBloco, int blocosPorLado) {}

	/**
	 * Monta os índices de uma grade de lado x lado pontos, em blocos de bloco x bloco células. Cada
	 * célula dá os mesmos dois triângulos do original, na mesma ordem dos cantos: (i,j) (i+1,j)
	 * (i+1,j+1) e (i+1,j+1) (i,j+1) (i,j). Só dados, sem OpenGL (testado).
	 */
	public static Indices indices(int lado, int bloco) {
		int celulas = lado - 1;
		int porLado = (celulas + bloco - 1) / bloco;
		int[] lista = new int[celulas * celulas * 6];
		int[] inicio = new int[porLado * porLado + 1];
		int k = 0;
		for (int bj = 0; bj < porLado; bj++) {
			for (int bi = 0; bi < porLado; bi++) {
				inicio[bj * porLado + bi] = k;
				for (int j = bj * bloco; j < Math.min(celulas, (bj + 1) * bloco); j++) {
					for (int i = bi * bloco; i < Math.min(celulas, (bi + 1) * bloco); i++) {
						int a = j * lado + i, b = a + 1, c = a + lado + 1, d = a + lado;
						lista[k++] = a;
						lista[k++] = b;
						lista[k++] = c;
						lista[k++] = c;
						lista[k++] = d;
						lista[k++] = a;
					}
				}
			}
		}
		inicio[porLado * porLado] = k;
		return new Indices(lista, inicio, porLado);
	}

	/**
	 * O bloco com centro em (cx, cz) e "raio" (meia diagonal) aparece para quem está em (olhoX,
	 * olhoZ) olhando na direção (frenteX, frenteZ), até "alcance"? Fica de fora se estiver longe
	 * demais ou inteiro atrás. É uma conta folgada: na dúvida, o bloco é desenhado. (Testado.)
	 */
	public static boolean blocoVisivel(float cx, float cz, float raio, float olhoX, float olhoZ, float frenteX,
			float frenteZ, float alcance) {
		float dx = cx - olhoX, dz = cz - olhoZ;
		if (Math.sqrt(dx * dx + dz * dz) - raio > alcance) return false; // longe demais
		return dx * frenteX + dz * frenteZ >= -raio;                   // atrás, com folga do tamanho do bloco
	}

	private static int enviar(FloatBuffer dados) {
		int vbo = glGenBuffers();
		glBindBuffer(GL_ARRAY_BUFFER, vbo);
		glBufferData(GL_ARRAY_BUFFER, dados, GL_STATIC_DRAW);
		glBindBuffer(GL_ARRAY_BUFFER, 0);
		return vbo;
	}

	private static void ligar(int vbo, int atributo, int tamanho) {
		glBindBuffer(GL_ARRAY_BUFFER, vbo);
		glEnableVertexAttribArray(atributo);
		glVertexAttribPointer(atributo, tamanho, GL_FLOAT, false, 0, 0);
	}

	@Override
	public void draw() {
		ligar(vboPosicao, 0, 3);
		ligar(vboCor, 1, 3);
		ligar(vboNormal, 2, 3);
		ligar(vboTextura, 3, 2);
		glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, vboIndices);
		float meio = BLOCO * passoXZ / 2, raioBloco = meio * (float) Math.sqrt(2);
		blocosDesenhados = 0;
		for (int b = 0; b < blocosPorLado * blocosPorLado; b++) {
			int inicio = inicioBloco[b], fim = inicioBloco[b + 1];
			float cx = (b % blocosPorLado) * BLOCO * passoXZ + meio, cz = (b / blocosPorLado) * BLOCO * passoXZ + meio;
			if (fim == inicio || !blocoVisivel(cx, cz, raioBloco, olhoX, olhoZ, frenteX, frenteZ, alcance)) continue;
			glDrawElements(GL_TRIANGLES, fim - inicio, GL_UNSIGNED_INT, (long) inicio * 4); // deslocamento em bytes
			blocosDesenhados++;
		}
		glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
		glBindBuffer(GL_ARRAY_BUFFER, 0);
	}

	@Override
	public void liberar() {
		glDeleteBuffers(vboPosicao);
		glDeleteBuffers(vboCor);
		glDeleteBuffers(vboNormal);
		glDeleteBuffers(vboTextura);
		glDeleteBuffers(vboIndices);
	}
}
