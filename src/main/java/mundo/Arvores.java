package mundo;

import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.glDisableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL31.glDrawArraysInstanced;
import static org.lwjgl.opengl.GL33.glVertexAttribDivisor;

import java.nio.FloatBuffer;
import java.util.List;

import org.lwjgl.BufferUtils;

import Model.Model;

/**
 * Todas as árvores da floresta, desenhadas numa única chamada (instancing).
 *
 * Cada árvore são dois planos cruzados em X, com a textura recortada pelo alfa. Em vez de
 * mandar milhares de árvores uma a uma, a placa recebe:
 *  - um VBO com o modelo de UMA árvore (os dois planos, 12 vértices, de altura 1);
 *  - um VBO com os dados de cada árvore (posição, altura, giro, largura, brilho, textura).
 * O glVertexAttribDivisor(atributo, 1) diz que esses dados avançam uma vez por árvore,
 * e não por vértice. O glDrawArraysInstanced desenha os 12 vértices N vezes.
 *
 * Atributos 0, 2 e 3 seguem o padrão do projeto (posição, normal, textura);
 * os dados de cada árvore usam os atributos 4 e 5.
 */
public class Arvores extends Model {

	private static final int FLOATS_VERTICE = 8;   // posição 3, normal 3, uv 2
	private static final int FLOATS_INSTANCIA = 8; // x, y, z, altura | rotação, largura, brilho, textura
	private static final int VERTICES = 12;

	private final List<DistribuidorArvores.Arvore> arvores;
	private int vboModelo, vboInstancias;

	public Arvores(List<DistribuidorArvores.Arvore> arvores) {
		this.arvores = arvores;
	}

	public int quantidade() {
		return arvores.size();
	}

	@Override
	public void load() {
		FloatBuffer modelo = BufferUtils.createFloatBuffer(VERTICES * FLOATS_VERTICE);
		// plano 1, no eixo X (normal para +z); plano 2, no eixo Z (normal para +x)
		plano(modelo, true);
		plano(modelo, false);
		modelo.flip();
		vboModelo = glGenBuffers();
		glBindBuffer(GL_ARRAY_BUFFER, vboModelo);
		glBufferData(GL_ARRAY_BUFFER, modelo, GL_STATIC_DRAW);

		FloatBuffer dados = BufferUtils.createFloatBuffer(Math.max(1, arvores.size()) * FLOATS_INSTANCIA);
		for (DistribuidorArvores.Arvore a : arvores) {
			dados.put(a.x()).put(a.y()).put(a.z()).put(a.altura());
			dados.put(a.rotacao()).put(a.largura()).put(a.brilho()).put(a.textura());
		}
		dados.flip();
		vboInstancias = glGenBuffers();
		glBindBuffer(GL_ARRAY_BUFFER, vboInstancias);
		glBufferData(GL_ARRAY_BUFFER, dados, GL_STATIC_DRAW);
		glBindBuffer(GL_ARRAY_BUFFER, 0);
	}

	/** Dois triângulos de um plano de largura 1 e altura 1, com a base no chão. */
	private static void plano(FloatBuffer b, boolean eixoX) {
		float[][] cantos = { { -0.5f, 0 }, { 0.5f, 0 }, { 0.5f, 1 }, { 0.5f, 1 }, { -0.5f, 1 }, { -0.5f, 0 } };
		for (float[] c : cantos) {
			float lado = c[0], altura = c[1];
			if (eixoX) {
				b.put(lado).put(altura).put(0).put(0).put(0).put(1);
			} else {
				b.put(0).put(altura).put(lado).put(1).put(0).put(0);
			}
			// u de 0 a 1 na largura; v = 1 embaixo, porque a primeira linha da imagem é o topo
			b.put(lado + 0.5f).put(1 - altura);
		}
	}

	@Override
	public void draw() {
		int bytesVertice = FLOATS_VERTICE * 4;
		glBindBuffer(GL_ARRAY_BUFFER, vboModelo);
		glEnableVertexAttribArray(0);
		glVertexAttribPointer(0, 3, GL_FLOAT, false, bytesVertice, 0);
		glEnableVertexAttribArray(2);
		glVertexAttribPointer(2, 3, GL_FLOAT, false, bytesVertice, 12);
		glEnableVertexAttribArray(3);
		glVertexAttribPointer(3, 2, GL_FLOAT, false, bytesVertice, 24);

		int bytesInstancia = FLOATS_INSTANCIA * 4;
		glBindBuffer(GL_ARRAY_BUFFER, vboInstancias);
		glEnableVertexAttribArray(4);
		glVertexAttribPointer(4, 4, GL_FLOAT, false, bytesInstancia, 0);
		glVertexAttribDivisor(4, 1); // avança uma vez por árvore
		glEnableVertexAttribArray(5);
		glVertexAttribPointer(5, 4, GL_FLOAT, false, bytesInstancia, 16);
		glVertexAttribDivisor(5, 1);

		glDrawArraysInstanced(GL_TRIANGLES, 0, VERTICES, arvores.size());

		// devolve os atributos 4 e 5 ao normal: outros modelos usam esses números de outro jeito
		glVertexAttribDivisor(4, 0);
		glVertexAttribDivisor(5, 0);
		glDisableVertexAttribArray(4);
		glDisableVertexAttribArray(5);
		glBindBuffer(GL_ARRAY_BUFFER, 0);
	}

	public void liberar() {
		glDeleteBuffers(vboModelo);
		glDeleteBuffers(vboInstancias);
	}
}
