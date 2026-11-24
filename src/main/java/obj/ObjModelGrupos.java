package obj;

import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL20.glDisableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttrib3f;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;

import java.util.List;

import render.ModeloArma;

/**
 * O ObjModel do projeto original desenhando cada grupo ("g" do arquivo) separadamente.
 *
 * O ObjModel já lê os grupos e guarda, em cada GrupoFaces, a primeira e a última face; mas o
 * draw() dele desenha o modelo inteiro. Como o load() põe no VBO os vértices das faces na
 * ordem do arquivo (3 por triângulo, 6 por quadrilátero), o grupo ocupa um trecho contínuo do
 * VBO: basta contar quantos vértices vêm antes dele e quantos ele tem, e chamar glDrawArrays
 * só nesse trecho.
 *
 * Fica no pacote obj porque os VBOs do ObjModel são visíveis só dentro do pacote.
 * O ObjModel não foi alterado.
 */
public class ObjModelGrupos extends ObjModel implements ModeloArma {

	/** Lê o arquivo e envia para a placa de vídeo. Só na thread principal. */
	public ObjModelGrupos(String arquivo) {
		loadObj(arquivo);
		load();
	}

	/** {primeiro vértice, quantidade} do grupo no VBO; null se o grupo não existe. */
	public int[] faixa(String nome) {
		GrupoFaces grupo = gname.get(nome);
		return grupo == null ? null : faixa(f, grupo.finicial, grupo.ffinal);
	}

	/** Vértices das faces [0, inicial) e [inicial, final]: 3 por triângulo, 6 por quadrilátero. */
	static int[] faixa(List<Face3D> faces, int inicial, int finalInclusivo) {
		int antes = 0, dentro = 0;
		for (int i = 0; i <= finalInclusivo && i < faces.size(); i++) {
			int n = faces.get(i).nvertices == 4 ? 6 : faces.get(i).nvertices == 3 ? 3 : 0;
			if (i < inicial) antes += n; else dentro += n;
		}
		return new int[] { antes, dentro };
	}

	@Override
	public boolean temGrupo(String nome) {
		return gname.containsKey(nome);
	}

	@Override
	public void desenharGrupo(String nome) {
		int[] faixa = faixa(nome);
		if (faixa == null || faixa[1] == 0) return;
		// os mesmos atributos do draw() original: 2 normal, 3 textura, 0 posição
		glBindBuffer(GL_ARRAY_BUFFER, vbo_normal_handle);
		glEnableVertexAttribArray(2);
		glVertexAttribPointer(2, vertex_size, GL_FLOAT, false, 0, 0);
		glBindBuffer(GL_ARRAY_BUFFER, vbo_textcood_handle);
		glEnableVertexAttribArray(3);
		glVertexAttribPointer(3, texture_size, GL_FLOAT, false, 0, 0);
		glBindBuffer(GL_ARRAY_BUFFER, vbo_vertex_handle);
		glEnableVertexAttribArray(0);
		glVertexAttribPointer(0, vertex_size, GL_FLOAT, false, 0, 0);
		glDisableVertexAttribArray(1);
		glVertexAttrib3f(1, 1, 1, 1);
		glDrawArrays(GL_TRIANGLES, faixa[0], faixa[1]);
		glBindBuffer(GL_ARRAY_BUFFER, 0);
	}

	@Override
	public void liberar() {
		glDeleteBuffers(vbo_vertex_handle);
		glDeleteBuffers(vbo_normal_handle);
		glDeleteBuffers(vbo_textcood_handle);
	}
}
