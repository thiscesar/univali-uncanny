package render;

import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;

import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.lwjgl.BufferUtils;

/**
 * Uma escopeta de bomba (pump) montada por código, em estilo PS1: caixas e cilindros de 8
 * lados, com uma textura pequena de madeira e metal. É o substituto de modelos/escopeta/
 * escopeta.obj: o jogo funciona igual com ela, e o OBJ, quando existir, entra no lugar.
 *
 * Já vem na orientação da câmera: x para a direita, y para cima, cano para -z, em metros,
 * com a origem no meio da caixa da culatra. Dois grupos: "corpo" e "telha" (a bomba de
 * madeira que anda no engatilhar).
 */
public class MalhaEscopeta implements ModeloArma {

	/** Onde fica a lente da lanterna (montada ao lado do cano) e a boca do cano, no modelo. */
	public static final float[] LENTE_LANTERNA = { 0.032f, -0.004f, -0.63f };
	public static final float[] BOCA_DO_CANO = { 0f, 0.012f, -0.69f };

	/** Pedaços da textura: madeira à esquerda, metal à direita (u de 0 a 1, t a partir do topo). */
	private static final float[] MADEIRA = { 0.0f, 0.5f }, METAL = { 0.5f, 1.0f };
	private static final int FLOATS = 8; // posição 3, normal 3, uv 2

	private final Map<String, int[]> grupos = new LinkedHashMap<>();
	private final List<float[]> vertices = new ArrayList<>();
	private int vbo;

	/** Monta a malha e envia para a placa de vídeo. Só na thread principal. */
	public MalhaEscopeta() {
		montar();
		FloatBuffer b = BufferUtils.createFloatBuffer(vertices.size() * FLOATS);
		for (float[] v : vertices) b.put(v);
		b.flip();
		vbo = glGenBuffers();
		glBindBuffer(GL_ARRAY_BUFFER, vbo);
		glBufferData(GL_ARRAY_BUFFER, b, GL_STATIC_DRAW);
		glBindBuffer(GL_ARRAY_BUFFER, 0);
	}

	/** As peças da arma. Medidas em metros: ~1,1 m de ponta a ponta. */
	private void montar() {
		int inicio = vertices.size();
		caixa(-0.024f, 0.024f, -0.035f, 0.03f, -0.06f, 0.12f, METAL);                    // culatra
		cilindro(0f, 0.012f, 0.011f, -0.69f, -0.06f, METAL);                            // cano
		cilindro(0f, -0.016f, 0.010f, -0.58f, -0.06f, METAL);                           // tubo do carregador
		cilindro(0f, 0.012f, 0.014f, -0.69f, -0.66f, METAL);                            // anel na boca do cano
		caixa(-0.006f, 0.006f, -0.062f, -0.035f, 0.03f, 0.09f, METAL);                  // guarda-mato
		cilindro(LENTE_LANTERNA[0], LENTE_LANTERNA[1], 0.016f, LENTE_LANTERNA[2], -0.47f, METAL); // lanterna
		caixa(0.012f, 0.026f, -0.012f, 0.004f, -0.52f, -0.49f, METAL);                  // presilha da lanterna
		// coronha: mais alta e mais baixa atrás (o "cotovelo" para o ombro)
		caixaAfunilada(new float[] { -0.022f, 0.022f, -0.04f, 0.025f }, 0.12f,
				new float[] { -0.025f, 0.025f, -0.115f, 0.02f }, 0.46f, MADEIRA);
		grupos.put("corpo", new int[] { inicio, vertices.size() - inicio });

		inicio = vertices.size();
		cilindro(0f, -0.016f, 0.021f, -0.41f, -0.20f, MADEIRA);                         // telha (bomba)
		grupos.put("telha", new int[] { inicio, vertices.size() - inicio });
	}

	// ---------- peças ----------

	private void caixa(float x0, float x1, float y0, float y1, float z0, float z1, float[] material) {
		caixaAfunilada(new float[] { x0, x1, y0, y1 }, z0, new float[] { x0, x1, y0, y1 }, z1, material);
	}

	/**
	 * Uma caixa cujas faces da frente (z0) e de trás (z1) podem ter tamanhos diferentes:
	 * cada face é {xMin, xMax, yMin, yMax}. Seis faces, cada uma com a sua normal.
	 */
	private void caixaAfunilada(float[] frente, float z0, float[] tras, float z1, float[] material) {
		float[][] c = new float[8][];
		for (int i = 0; i < 8; i++) {
			float[] f = (i & 4) == 0 ? frente : tras;
			c[i] = new float[] { (i & 1) == 0 ? f[0] : f[1], (i & 2) == 0 ? f[2] : f[3], (i & 4) == 0 ? z0 : z1 };
		}
		// cantos: bit 1 = x, bit 2 = y, bit 4 = z (0 = frente, ou seja, mais para -z)
		quad(c[0], c[2], c[3], c[1], material); // frente (-z)
		quad(c[5], c[7], c[6], c[4], material); // trás (+z)
		quad(c[4], c[6], c[2], c[0], material); // esquerda (-x)
		quad(c[1], c[3], c[7], c[5], material); // direita (+x)
		quad(c[2], c[6], c[7], c[3], material); // cima (+y)
		quad(c[4], c[0], c[1], c[5], material); // baixo (-y)
	}

	/** Cilindro de 8 lados ao longo de z (de z0 a z1), centrado em (cx, cy), com as tampas. */
	private void cilindro(float cx, float cy, float raio, float z0, float z1, float[] material) {
		int lados = 8;
		for (int i = 0; i < lados; i++) {
			double a0 = 2 * Math.PI * i / lados, a1 = 2 * Math.PI * (i + 1) / lados;
			float[] p0 = { cx + raio * (float) Math.cos(a0), cy + raio * (float) Math.sin(a0) };
			float[] p1 = { cx + raio * (float) Math.cos(a1), cy + raio * (float) Math.sin(a1) };
			quad(new float[] { p0[0], p0[1], z0 }, new float[] { p1[0], p1[1], z0 },
					new float[] { p1[0], p1[1], z1 }, new float[] { p0[0], p0[1], z1 }, material);
			triangulo(new float[] { cx, cy, z0 }, new float[] { p1[0], p1[1], z0 }, new float[] { p0[0], p0[1], z0 },
					material); // tampa da frente (normal para -z)
			triangulo(new float[] { cx, cy, z1 }, new float[] { p0[0], p0[1], z1 }, new float[] { p1[0], p1[1], z1 },
					material); // tampa de trás (normal para +z)
		}
	}

	/** Quadrilátero a-b-c-d (anti-horário visto de fora: (b - a) x (c - a) aponta para fora), com a textura do material. */
	private void quad(float[] a, float[] b, float[] c, float[] d, float[] material) {
		float[] n = normal(a, b, c);
		vertice(a, n, material, 0, 1);
		vertice(b, n, material, 0, 0);
		vertice(c, n, material, 1, 0);
		vertice(c, n, material, 1, 0);
		vertice(d, n, material, 1, 1);
		vertice(a, n, material, 0, 1);
	}

	private void triangulo(float[] a, float[] b, float[] c, float[] material) {
		float[] n = normal(a, b, c);
		vertice(a, n, material, 0.5f, 0.5f);
		vertice(b, n, material, 0, 0);
		vertice(c, n, material, 1, 0);
	}

	private static float[] normal(float[] a, float[] b, float[] c) {
		float ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
		float vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
		float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
		float t = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		return t < 1e-9f ? new float[] { 0, 1, 0 } : new float[] { nx / t, ny / t, nz / t };
	}

	/**
	 * Um vértice. (s, t) vão de 0 a 1 dentro do pedaço do material. O shader de objetos usa a
	 * convenção do ObjModel original (uv = (textuv.y, 1 - textuv.x)), então guardamos (1 - t, u).
	 */
	private void vertice(float[] p, float[] n, float[] material, float s, float t) {
		float u = material[0] + (material[1] - material[0]) * s;
		vertices.add(new float[] { p[0], p[1], p[2], n[0], n[1], n[2], 1 - t, u });
	}

	// ---------- desenho ----------

	@Override
	public boolean temGrupo(String nome) {
		return grupos.containsKey(nome);
	}

	@Override
	public void desenharGrupo(String nome) {
		int[] g = grupos.get(nome);
		if (g == null) return;
		int bytes = FLOATS * 4;
		glBindBuffer(GL_ARRAY_BUFFER, vbo);
		glEnableVertexAttribArray(0);
		glVertexAttribPointer(0, 3, GL_FLOAT, false, bytes, 0);
		glDisableVertexAttribArray(1);
		glVertexAttrib3f(1, 1, 1, 1);
		glEnableVertexAttribArray(2);
		glVertexAttribPointer(2, 3, GL_FLOAT, false, bytes, 12);
		glEnableVertexAttribArray(3);
		glVertexAttribPointer(3, 2, GL_FLOAT, false, bytes, 24);
		glDrawArrays(GL_TRIANGLES, g[0], g[1]);
		glBindBuffer(GL_ARRAY_BUFFER, 0);
	}

	@Override
	public void liberar() {
		glDeleteBuffers(vbo);
	}

	// ---------- textura ----------

	/**
	 * A textura da escopeta, 128 x 64: madeira (veios escuros ao longo) à esquerda e metal
	 * oxidado (riscos finos e manchas) à direita. Poucas cores, como as do gerador PS1.
	 */
	public static BufferedImage textura() {
		int w = 128, h = 64;
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Random r = new Random(5);
		float[] fase = new float[h];
		for (int y = 0; y < h; y++) fase[y] = r.nextFloat() * 6.28f;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int cor;
				if (x < w / 2) {
					// madeira: veios = ondas ao longo de x que variam devagar em y
					double veio = Math.sin(y * 0.9 + Math.sin(x * 0.12 + fase[y / 8]) * 2.2);
					double v = 0.55 + 0.25 * veio + (r.nextDouble() - 0.5) * 0.15;
					cor = rgb(92 * v + 20, 52 * v + 12, 26 * v + 6);
				} else {
					double v = 0.5 + (r.nextDouble() - 0.5) * 0.18;
					if (r.nextDouble() < 0.02) v += 0.35;                       // risco claro
					if (Math.sin(x * 0.31 + y * 0.17) > 0.93) v -= 0.18;        // mancha escura
					cor = rgb(60 * v + 18, 62 * v + 18, 68 * v + 20);
				}
				img.setRGB(x, y, cor);
			}
		}
		return img;
	}

	private static int rgb(double r, double g, double b) {
		int ri = (int) Math.max(0, Math.min(255, r)), gi = (int) Math.max(0, Math.min(255, g)),
				bi = (int) Math.max(0, Math.min(255, b));
		return 0xFF000000 | (ri << 16) | (gi << 8) | bi;
	}
}
