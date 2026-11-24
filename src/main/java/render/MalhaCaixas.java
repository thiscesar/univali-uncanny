package render;

import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.BufferUtils;

/**
 * Uma malha feita de caixas (postes, tábuas, painéis), para o shader de objetos: posição,
 * normal e coordenada de textura. Cada caixa diz que pedaço da textura vai na face da frente
 * (+z), na de trás (-z) e nas outras quatro, sempre como {u0, t0, u1, t1} (t a partir do topo).
 *
 * A face de trás é mapeada para ser lida de trás (o lado esquerdo de quem olha é o +x), então
 * um texto na textura dela aparece do jeito certo, e não espelhado.
 */
public class MalhaCaixas {

	private static final int FLOATS = 8; // posição 3, normal 3, uv 2
	private final List<float[]> vertices = new ArrayList<>();
	private int vbo = 0, quantidade = 0;

	public MalhaCaixas caixa(float x0, float x1, float y0, float y1, float z0, float z1, float[] frente, float[] tras,
			float[] lados) {
		float[] a = { x0, y0, z1 }, b = { x1, y0, z1 }, c = { x1, y1, z1 }, d = { x0, y1, z1 }; // frente
		float[] e = { x0, y0, z0 }, f = { x1, y0, z0 }, g = { x1, y1, z0 }, h = { x0, y1, z0 }; // trás
		face(a, b, c, d, new float[] { 0, 0, 1 }, frente);
		face(f, e, h, g, new float[] { 0, 0, -1 }, tras);  // vista de trás, a esquerda é o +x
		face(e, a, d, h, new float[] { -1, 0, 0 }, lados);
		face(b, f, g, c, new float[] { 1, 0, 0 }, lados);
		face(d, c, g, h, new float[] { 0, 1, 0 }, lados);
		face(e, f, b, a, new float[] { 0, -1, 0 }, lados);
		return this;
	}

	/**
	 * Uma placa de uma face só, em pé no plano x = constante, virada para +x (ou -x) e lida certo
	 * por quem olha desse lado. Para decalques numa parede (com textura recortada): numa caixa, a
	 * face de trás apareceria espelhada pelos buracos da da frente.
	 */
	public MalhaCaixas placaEmX(float x, float y0, float y1, float z0, float z1, boolean paraMaisX, float[] uv) {
		if (paraMaisX) { // olhando de +x para -x, a esquerda é o +z
			face(new float[] { x, y0, z1 }, new float[] { x, y0, z0 }, new float[] { x, y1, z0 }, new float[] { x, y1, z1 },
					new float[] { 1, 0, 0 }, uv);
		} else {
			face(new float[] { x, y0, z0 }, new float[] { x, y0, z1 }, new float[] { x, y1, z1 }, new float[] { x, y1, z0 },
					new float[] { -1, 0, 0 }, uv);
		}
		return this;
	}

	/**
	 * Um quadrilátero qualquer (não precisa ser alinhado aos eixos): os cantos inferior esquerdo,
	 * inferior direito, superior direito e superior esquerdo, como vistos de fora. A normal sai do
	 * produto vetorial (para fora, nessa ordem). Para formas que não são caixas, como a cabeça.
	 */
	public MalhaCaixas quad(float[] ie, float[] id, float[] sd, float[] se, float[] uv) {
		float ax = id[0] - ie[0], ay = id[1] - ie[1], az = id[2] - ie[2];
		float bx = se[0] - ie[0], by = se[1] - ie[1], bz = se[2] - ie[2];
		float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
		float t = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		face(ie, id, sd, se, t > 1e-9f ? new float[] { nx / t, ny / t, nz / t } : new float[] { 0, 1, 0 }, uv);
		return this;
	}

	/** Quadrilátero com os cantos inferior esquerdo, inferior direito, superior direito, superior esquerdo. */
	private void face(float[] ie, float[] id, float[] sd, float[] se, float[] n, float[] uv) {
		float u0 = uv[0], t0 = uv[1], u1 = uv[2], t1 = uv[3];
		vertice(ie, n, u0, t1);
		vertice(id, n, u1, t1);
		vertice(sd, n, u1, t0);
		vertice(sd, n, u1, t0);
		vertice(se, n, u0, t0);
		vertice(ie, n, u0, t1);
	}

	/** O shader de objetos usa a convenção do ObjModel original (uv = (textuv.y, 1 - textuv.x)): guardamos (1 - t, u). */
	private void vertice(float[] p, float[] n, float u, float t) {
		vertices.add(new float[] { p[0], p[1], p[2], n[0], n[1], n[2], 1 - t, u });
	}

	/** Envia para a placa de vídeo. Só na thread principal. */
	public MalhaCaixas enviar() {
		FloatBuffer b = BufferUtils.createFloatBuffer(vertices.size() * FLOATS);
		for (float[] v : vertices) b.put(v);
		b.flip();
		vbo = glGenBuffers();
		glBindBuffer(GL_ARRAY_BUFFER, vbo);
		glBufferData(GL_ARRAY_BUFFER, b, GL_STATIC_DRAW);
		glBindBuffer(GL_ARRAY_BUFFER, 0);
		quantidade = vertices.size();
		vertices.clear();
		return this;
	}

	public void desenhar() {
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
		glDrawArrays(GL_TRIANGLES, 0, quantidade);
		glBindBuffer(GL_ARRAY_BUFFER, 0);
	}

	public void liberar() {
		if (vbo != 0) glDeleteBuffers(vbo);
		vbo = 0;
	}
}
