package mundo;

import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.glDisableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL31.glDrawArraysInstanced;
import static org.lwjgl.opengl.GL33.glVertexAttribDivisor;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.BufferUtils;

import Model.Model;
import dados.Recursos;
import util.Caminhos;

/**
 * As pedras do cenário, modelos do gerador PS1 (modelos/pedras/pedra_N.obj, com musgo
 * pintado nos vértices de cima), desenhadas por instancing com o mesmo shader das árvores 3D,
 * só que sem o vento.
 *
 * Diferente das árvores, são poucas (umas 500) e de poucos triângulos (80 cada): o VBO de
 * instâncias é montado uma vez só, com as pedras separadas por modelo, e todas são desenhadas
 * todo quadro (a neblina e o plano "longe" cortam o que está longe).
 */
public class Pedras extends Model {

	public static final String PASTA = "modelos/pedras/";
	public static final int MODELOS = 4;
	private static final int FLOATS_INSTANCIA = 8; // x, y, z, escala | giro, brilho, altura do modelo, livre

	private final List<DistribuidorPedras.Pedra> pedras;
	private final List<LeitorObj.ModeloObj> modelos;
	private final int[] inicioModelo, inicioInstancias, quantidade;
	private int vboModelo, vboInstancias;

	public Pedras(List<DistribuidorPedras.Pedra> pedras, List<LeitorObj.ModeloObj> modelos) {
		this.pedras = pedras;
		this.modelos = modelos;
		inicioModelo = new int[modelos.size()];
		inicioInstancias = new int[modelos.size()];
		quantidade = new int[modelos.size()];
	}

	public static boolean arquivosPresentes() {
		for (int i = 1; i <= MODELOS; i++) {
			if (!Caminhos.existe(PASTA + "pedra_" + i + ".obj")) return false;
		}
		return Caminhos.existe(PASTA + "pedra.png");
	}

	public static List<LeitorObj.ModeloObj> lerModelos() throws IOException {
		List<LeitorObj.ModeloObj> lista = new ArrayList<>();
		for (int i = 1; i <= MODELOS; i++) {
			lista.add(LeitorObj.ler(Caminhos.caminho(PASTA + "pedra_" + i + ".obj")));
		}
		return lista;
	}

	/** A largura (o maior lado no chão) de cada modelo, em metros. */
	public static float[] larguras(List<LeitorObj.ModeloObj> modelos) {
		float[] l = new float[modelos.size()];
		for (int m = 0; m < modelos.size(); m++) {
			float[] v = modelos.get(m).vertices();
			float xMin = Float.MAX_VALUE, xMax = -Float.MAX_VALUE, zMin = Float.MAX_VALUE, zMax = -Float.MAX_VALUE;
			for (int i = 0; i < v.length; i += LeitorObj.FLOATS_VERTICE) {
				xMin = Math.min(xMin, v[i]);
				xMax = Math.max(xMax, v[i]);
				zMin = Math.min(zMin, v[i + 2]);
				zMax = Math.max(zMax, v[i + 2]);
			}
			l[m] = Math.max(xMax - xMin, zMax - zMin);
		}
		return l;
	}

	public int quantidade() {
		return pedras.size();
	}

	@Override
	public void load() {
		int total = 0;
		for (int i = 0; i < modelos.size(); i++) {
			inicioModelo[i] = total;
			total += modelos.get(i).quantidadeVertices();
		}
		FloatBuffer dados = BufferUtils.createFloatBuffer(Math.max(1, total) * LeitorObj.FLOATS_VERTICE);
		for (LeitorObj.ModeloObj m : modelos) dados.put(m.vertices());
		dados.flip();
		vboModelo = glGenBuffers();
		glBindBuffer(GL_ARRAY_BUFFER, vboModelo);
		glBufferData(GL_ARRAY_BUFFER, dados, GL_STATIC_DRAW);

		// as instâncias, separadas por modelo: cada modelo ocupa um trecho contínuo do VBO
		FloatBuffer inst = BufferUtils.createFloatBuffer(Math.max(1, pedras.size()) * FLOATS_INSTANCIA);
		int n = 0;
		for (int m = 0; m < modelos.size(); m++) {
			inicioInstancias[m] = n;
			for (DistribuidorPedras.Pedra p : pedras) {
				if (p.modelo() != m) continue;
				// brilho levemente diferente por pedra, tirado da posição (sem precisar de sorteio)
				float brilho = 0.85f + 0.3f * (float) Math.abs(Math.sin(p.x() * 12.9898 + p.z() * 78.233));
				inst.put(p.x()).put(p.y()).put(p.z()).put(p.escala());
				inst.put(p.giro()).put(brilho).put(modelos.get(m).altura()).put(0);
				n++;
			}
			quantidade[m] = n - inicioInstancias[m];
		}
		inst.flip();
		vboInstancias = glGenBuffers();
		glBindBuffer(GL_ARRAY_BUFFER, vboInstancias);
		glBufferData(GL_ARRAY_BUFFER, inst, GL_STATIC_DRAW);
		glBindBuffer(GL_ARRAY_BUFFER, 0);
	}

	/** Desenha todas as pedras. O shader das árvores 3D já deve estar ativo, com vento = 0. */
	@Override
	public void draw() {
		int bytesVertice = LeitorObj.FLOATS_VERTICE * 4;
		glBindBuffer(GL_ARRAY_BUFFER, vboModelo);
		glEnableVertexAttribArray(0);
		glVertexAttribPointer(0, 3, GL_FLOAT, false, bytesVertice, 0);
		glEnableVertexAttribArray(1);
		glVertexAttribPointer(1, 3, GL_FLOAT, false, bytesVertice, 12);
		glEnableVertexAttribArray(2);
		glVertexAttribPointer(2, 3, GL_FLOAT, false, bytesVertice, 24);
		glEnableVertexAttribArray(3);
		glVertexAttribPointer(3, 2, GL_FLOAT, false, bytesVertice, 36);

		int bytesInstancia = FLOATS_INSTANCIA * 4;
		glBindBuffer(GL_ARRAY_BUFFER, vboInstancias);
		glEnableVertexAttribArray(4);
		glEnableVertexAttribArray(5);
		glVertexAttribDivisor(4, 1);
		glVertexAttribDivisor(5, 1);
		for (int m = 0; m < modelos.size(); m++) {
			if (quantidade[m] == 0) continue;
			long deslocamento = (long) inicioInstancias[m] * bytesInstancia;
			glVertexAttribPointer(4, 4, GL_FLOAT, false, bytesInstancia, deslocamento);
			glVertexAttribPointer(5, 4, GL_FLOAT, false, bytesInstancia, deslocamento + 16);
			for (LeitorObj.Grupo g : modelos.get(m).grupos()) {
				glBindTexture(GL_TEXTURE_2D, Recursos.texturaModelo(PASTA + g.material() + ".png"));
				glDrawArraysInstanced(GL_TRIANGLES, inicioModelo[m] + g.primeiro(), g.quantidade(), quantidade[m]);
			}
		}
		glVertexAttribDivisor(4, 0);
		glVertexAttribDivisor(5, 0);
		glDisableVertexAttribArray(1);
		glDisableVertexAttribArray(4);
		glDisableVertexAttribArray(5);
		glBindBuffer(GL_ARRAY_BUFFER, 0);
	}

	public void liberar() {
		glDeleteBuffers(vboModelo);
		glDeleteBuffers(vboInstancias);
	}
}
