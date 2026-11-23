package md2;

import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;

import java.nio.FloatBuffer;

import org.lwjgl.BufferUtils;

import Model.Model;

/**
 * Um MD2 na placa de vídeo, com todos os quadros de uma vez.
 *
 * Um VBO guarda TODOS os quadros, um depois do outro (posição e normal de cada vértice).
 * Outro guarda as coordenadas de textura, iguais para todos os quadros. Para desenhar a
 * mistura do quadro A com o quadro B, não se manda nada de novo: só se muda o ponto do
 * VBO para onde cada atributo aponta (o "offset" do glVertexAttribPointer):
 *   atributos 0 e 2 (posição e normal, padrão do projeto) apontam para o quadro A;
 *   atributos 4 e 5 apontam para o quadro B.
 * O vertex shader recebe os dois e mistura com mix().
 */
public class Md2Modelo extends Model {

	private static final int FLOATS_VERTICE = 6; // posição 3 + normal 3
	private static final int BYTES_VERTICE = FLOATS_VERTICE * 4;

	private final MalhaMd2 malha;
	private int vboQuadros, vboUv;

	public Md2Modelo(MalhaMd2 malha) {
		this.malha = malha;
	}

	public MalhaMd2 malha() {
		return malha;
	}

	@Override
	public void load() {
		FloatBuffer quadros = BufferUtils.createFloatBuffer(malha.quadros() * malha.vertices * FLOATS_VERTICE);
		for (int q = 0; q < malha.quadros(); q++) {
			float[] p = malha.posicoes.get(q), n = malha.normais.get(q);
			for (int v = 0; v < malha.vertices; v++) {
				quadros.put(p, v * 3, 3).put(n, v * 3, 3);
			}
		}
		quadros.flip();
		vboQuadros = glGenBuffers();
		glBindBuffer(GL_ARRAY_BUFFER, vboQuadros);
		glBufferData(GL_ARRAY_BUFFER, quadros, GL_STATIC_DRAW);

		FloatBuffer uv = BufferUtils.createFloatBuffer(malha.uvs.length);
		uv.put(malha.uvs).flip();
		vboUv = glGenBuffers();
		glBindBuffer(GL_ARRAY_BUFFER, vboUv);
		glBufferData(GL_ARRAY_BUFFER, uv, GL_STATIC_DRAW);
		glBindBuffer(GL_ARRAY_BUFFER, 0);
	}

	/** Desenha o primeiro quadro (o contrato do Model). */
	@Override
	public void draw() {
		desenhar(0, 0);
	}

	/** Desenha os atributos do quadro A e do quadro B; a mistura é o uniform "fator" do shader. */
	public void desenhar(int quadroA, int quadroB) {
		long bytesQuadro = (long) malha.vertices * BYTES_VERTICE;
		glBindBuffer(GL_ARRAY_BUFFER, vboQuadros);
		glEnableVertexAttribArray(0);
		glVertexAttribPointer(0, 3, GL_FLOAT, false, BYTES_VERTICE, quadroA * bytesQuadro);
		glEnableVertexAttribArray(2);
		glVertexAttribPointer(2, 3, GL_FLOAT, false, BYTES_VERTICE, quadroA * bytesQuadro + 12);
		glEnableVertexAttribArray(4);
		glVertexAttribPointer(4, 3, GL_FLOAT, false, BYTES_VERTICE, quadroB * bytesQuadro);
		glEnableVertexAttribArray(5);
		glVertexAttribPointer(5, 3, GL_FLOAT, false, BYTES_VERTICE, quadroB * bytesQuadro + 12);

		glBindBuffer(GL_ARRAY_BUFFER, vboUv);
		glEnableVertexAttribArray(3);
		glVertexAttribPointer(3, 2, GL_FLOAT, false, 0, 0);

		// o atributo 1 (cor) não vem de VBO: um valor constante para todos os vértices
		glDisableVertexAttribArray(1);
		glVertexAttrib3f(1, 1, 1, 1);

		glDrawArrays(GL_TRIANGLES, 0, malha.vertices);

		glDisableVertexAttribArray(4);
		glDisableVertexAttribArray(5);
		glBindBuffer(GL_ARRAY_BUFFER, 0);
	}

	public void liberar() {
		glDeleteBuffers(vboQuadros);
		glDeleteBuffers(vboUv);
	}
}
