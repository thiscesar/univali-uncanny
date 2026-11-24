package mundo;

import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glGetInteger;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.GL_CURRENT_PROGRAM;
import static org.lwjgl.opengl.GL20.glDisableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glGetUniformLocation;
import static org.lwjgl.opengl.GL20.glUniform1f;
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
 * As árvores como modelos 3D de verdade (tronco em tubo, copa em volume), feitos pelo gerador
 * de texturas PS1 e lidos de modelos/arvores/arvore_N.obj. Desenhadas por instancing, como as
 * árvores planas ({@link Arvores}), que continuam existindo para quando os modelos não estão
 * na pasta.
 *
 * Diferenças para as planas:
 *  - Só entram no desenho as árvores perto da câmera (até onde a neblina e o plano "longe"
 *    deixam ver) e que não estão atrás dela. A escolha é refeita a cada quadro, na CPU, e os
 *    dados dessas árvores vão para o VBO de instâncias (algumas centenas, é rápido).
 *  - Cada variação é um modelo diferente e cada modelo tem vários materiais (casca, pinho, copa,
 *    folhas), cada um com a sua textura: uma chamada glDrawArraysInstanced por variação e
 *    material, no máximo 6 por quadro. Para cada variação, o ponteiro dos atributos 4 e 5 começa
 *    no trecho do VBO de instâncias que é dela (as árvores ficam separadas por variação).
 *
 * Atributos: 0 posição, 1 cor (a sombra assada pelo gerador), 2 normal, 3 uv do modelo;
 * 4 e 5 os dados de cada árvore (posição, escala | giro, brilho, altura do modelo).
 */
public class Arvores3D extends Model {

	public static final String PASTA = "modelos/arvores/";
	/** Os materiais que os modelos usam; cada um tem modelos/arvores/material.png. */
	public static final String[] MATERIAIS = { "casca", "pinho", "copa", "folhas" };
	/** Só as folhas recortadas são planos finos, vistos dos dois lados. */
	private static final String MATERIAL_DUPLA_FACE = "folhas";

	/** Além disso a neblina já escondeu tudo (o plano "longe" é 60 m), com folga para a copa. */
	public static final float RAIO_DESENHO = 66f;
	/** Mais perto que isso a árvore é sempre desenhada, mesmo de lado ou atrás (a copa é larga e alta). */
	public static final float RAIO_SEMPRE = 16f;
	/** Quanto uma árvore pode estar atrás da câmera e ainda ser desenhada (metade da copa mais larga). */
	public static final float MARGEM_ATRAS = 5f;

	private static final int FLOATS_INSTANCIA = 8; // x, y, z, escala | giro, brilho, altura do modelo, livre

	private final List<DistribuidorArvores.Arvore> arvores;
	private final List<LeitorObj.ModeloObj> modelos;
	/** Primeiro vértice de cada modelo no VBO com os quatro. */
	private final int[] inicioModelo;

	private int vboModelo, vboInstancias;
	private final float[] instancias;
	private final FloatBuffer bufferInstancias;
	/** Por variação: onde começa no VBO de instâncias e quantas árvores foram escolhidas. */
	private final int[] inicioVariacao = new int[DistribuidorArvores.VARIACOES];
	private final int[] quantidadeVariacao = new int[DistribuidorArvores.VARIACOES];

	public Arvores3D(List<DistribuidorArvores.Arvore> arvores, List<LeitorObj.ModeloObj> modelos) {
		this.arvores = arvores;
		this.modelos = modelos;
		inicioModelo = new int[modelos.size()];
		instancias = new float[Math.max(1, arvores.size()) * FLOATS_INSTANCIA];
		bufferInstancias = BufferUtils.createFloatBuffer(instancias.length);
	}

	/** Os quatro modelos e as texturas dos materiais estão na pasta? Se não, o jogo usa as árvores planas. */
	public static boolean arquivosPresentes() {
		for (int i = 1; i <= DistribuidorArvores.VARIACOES; i++) {
			if (!Caminhos.arquivo(PASTA + "arvore_" + i + ".obj").exists()) return false;
		}
		for (String m : MATERIAIS) {
			if (!Caminhos.arquivo(PASTA + m + ".png").exists()) return false;
		}
		return true;
	}

	/** Lê os quatro modelos, na ordem das variações (pinheiro, abeto, seca, copa redonda). Só dados. */
	public static List<LeitorObj.ModeloObj> lerModelos() throws IOException {
		List<LeitorObj.ModeloObj> lista = new ArrayList<>();
		for (int i = 1; i <= DistribuidorArvores.VARIACOES; i++) {
			lista.add(LeitorObj.ler(Caminhos.caminho(PASTA + "arvore_" + i + ".obj")));
		}
		return lista;
	}

	public int quantidade() {
		return arvores.size();
	}

	/** Quantas árvores entraram no último desenho. */
	public int desenhadas() {
		int soma = 0;
		for (int q : quantidadeVariacao) soma += q;
		return soma;
	}

	@Override
	public void load() {
		int total = 0;
		for (int i = 0; i < modelos.size(); i++) {
			inicioModelo[i] = total;
			total += modelos.get(i).quantidadeVertices();
		}
		FloatBuffer dados = BufferUtils.createFloatBuffer(total * LeitorObj.FLOATS_VERTICE);
		for (LeitorObj.ModeloObj m : modelos) {
			dados.put(m.vertices());
		}
		dados.flip();
		vboModelo = glGenBuffers();
		glBindBuffer(GL_ARRAY_BUFFER, vboModelo);
		glBufferData(GL_ARRAY_BUFFER, dados, GL_STATIC_DRAW);

		// o VBO de instâncias é reescrito a cada quadro: GL_STREAM_DRAW avisa o driver disso
		vboInstancias = glGenBuffers();
		glBindBuffer(GL_ARRAY_BUFFER, vboInstancias);
		glBufferData(GL_ARRAY_BUFFER, (long) instancias.length * 4, GL_STREAM_DRAW);
		glBindBuffer(GL_ARRAY_BUFFER, 0);
	}

	/**
	 * A árvore entra no desenho? Perto demais, sempre; longe demais, nunca; no meio, só se não
	 * estiver atrás da câmera. (frenteX, frenteZ) é a direção da câmera no chão, unitária, ou
	 * (0, 0) quando ela olha reto para cima ou para baixo.
	 */
	static boolean visivel(float x, float z, float cameraX, float cameraZ, float frenteX, float frenteZ) {
		float dx = x - cameraX, dz = z - cameraZ;
		float distancia2 = dx * dx + dz * dz;
		if (distancia2 > RAIO_DESENHO * RAIO_DESENHO) return false;
		if (distancia2 < RAIO_SEMPRE * RAIO_SEMPRE) return true;
		return dx * frenteX + dz * frenteZ > -MARGEM_ATRAS; // projeção na direção do olhar
	}

	/**
	 * Escolhe as árvores do quadro e envia os dados delas, separadas por variação.
	 * (frenteX, frenteZ) é a direção do olhar, não precisa ser unitária.
	 */
	public void escolher(float cameraX, float cameraZ, float frenteX, float frenteZ) {
		float tamanho = (float) Math.sqrt(frenteX * frenteX + frenteZ * frenteZ);
		if (tamanho < 0.05f) {
			frenteX = 0; frenteZ = 0; // olhando reto para cima ou para baixo: só a distância conta
		} else {
			frenteX /= tamanho; frenteZ /= tamanho;
		}

		// 1ª passada: quantas de cada variação; 2ª: cada uma no seu trecho
		java.util.Arrays.fill(quantidadeVariacao, 0);
		for (DistribuidorArvores.Arvore a : arvores) {
			if (visivel(a.x(), a.z(), cameraX, cameraZ, frenteX, frenteZ)) quantidadeVariacao[a.textura()]++;
		}
		int inicio = 0;
		for (int v = 0; v < inicioVariacao.length; v++) {
			inicioVariacao[v] = inicio;
			inicio += quantidadeVariacao[v];
		}
		int[] proxima = inicioVariacao.clone();
		for (DistribuidorArvores.Arvore a : arvores) {
			if (!visivel(a.x(), a.z(), cameraX, cameraZ, frenteX, frenteZ)) continue;
			LeitorObj.ModeloObj modelo = modelos.get(a.textura());
			int k = proxima[a.textura()]++ * FLOATS_INSTANCIA;
			instancias[k] = a.x();
			instancias[k + 1] = a.y();
			instancias[k + 2] = a.z();
			instancias[k + 3] = a.altura() / modelo.altura(); // o modelo fica com a altura sorteada
			instancias[k + 4] = a.rotacao();
			instancias[k + 5] = a.brilho();
			instancias[k + 6] = modelo.altura();
			instancias[k + 7] = 0;
		}
		bufferInstancias.clear();
		bufferInstancias.put(instancias, 0, inicio * FLOATS_INSTANCIA);
		bufferInstancias.flip();
		glBindBuffer(GL_ARRAY_BUFFER, vboInstancias);
		glBufferSubData(GL_ARRAY_BUFFER, 0, bufferInstancias);
		glBindBuffer(GL_ARRAY_BUFFER, 0);
	}

	/** Desenha as árvores escolhidas. O shader das árvores 3D já deve estar ativo. */
	@Override
	public void draw() {
		int duplaFace = glGetUniformLocation(glGetInteger(GL_CURRENT_PROGRAM), "duplaFace");
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
		glVertexAttribDivisor(4, 1); // avança uma vez por árvore
		glVertexAttribDivisor(5, 1);
		for (int v = 0; v < modelos.size(); v++) {
			if (quantidadeVariacao[v] == 0) continue;
			long deslocamento = (long) inicioVariacao[v] * bytesInstancia; // o trecho desta variação
			glVertexAttribPointer(4, 4, GL_FLOAT, false, bytesInstancia, deslocamento);
			glVertexAttribPointer(5, 4, GL_FLOAT, false, bytesInstancia, deslocamento + 16);
			for (LeitorObj.Grupo g : modelos.get(v).grupos()) {
				glBindTexture(GL_TEXTURE_2D, Recursos.texturaArvore3D(g.material()));
				glUniform1f(duplaFace, g.material().equals(MATERIAL_DUPLA_FACE) ? 1 : 0);
				glDrawArraysInstanced(GL_TRIANGLES, inicioModelo[v] + g.primeiro(), g.quantidade(),
						quantidadeVariacao[v]);
			}
		}

		// devolve os atributos ao normal: outros modelos usam esses números de outro jeito
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
