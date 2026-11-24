package render;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL20.GL_POINT_SPRITE;
import static org.lwjgl.opengl.GL32.GL_PROGRAM_POINT_SIZE;

import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.util.List;

import org.lwjgl.BufferUtils;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;

import dados.Recursos;
import entidades.DragonKnight;
import md2.Animador;
import md2.MalhaMd2;
import md2.PersonagemMd2;
import md2.PontoMd2;
import shaders.ShaderBrilho;
import shaders.ShaderMd2;
import shaders.ShaderObjeto;

/**
 * Desenha o Dragon Knight: o MD2 animado na posição e na guinada da lógica, com um brilho
 * próprio fraco e o tom vermelho do dano, e os olhos brilhando para ele ser achado no escuro
 * mesmo com a lanterna desligada.
 *
 * Os olhos: o olho amarelo do dragão está pintado na skin, no pixel (54, 122) da skin.png de
 * 256 x 256. O PontoMd2 acha os triângulos do modelo que cobrem esse pixel (um de cada lado da
 * cabeça) e, a cada quadro, a posição deles na animação. Ali se desenha um ponto que brilha
 * (GL_POINTS com o shader "brilho"), somado à cena.
 *
 * Sem o MD2, desenha o substituto: um retângulo vermelho em pé, sempre de frente para a
 * câmera (billboard), com dois olhos no alto.
 */
public class DesenhoMonstro {

	/** Onde o olho está na skin, de 0 a 1 (o v conta do topo, como no MD2). */
	private static final float OLHO_U = 54.5f / 256f, OLHO_V = 122f / 256f;
	/** Brilho próprio do corpo: pouco, só para a silhueta não sumir no preto. */
	private static final float EMISSIVIDADE = 0.08f;
	private static final float TAMANHO_OLHO = 0.16f; // metros
	private static final float[] COR_OLHO = { 1.6f, 0.75f, 0.15f };
	/** O substituto: largura e altura do retângulo, em metros. */
	private static final float LARGURA_SUBSTITUTO = 1.4f, ALTURA_SUBSTITUTO = 2.2f;

	private final PersonagemMd2 personagem;
	private final List<PontoMd2> olhos;
	private final FloatBuffer bufferMatriz = BufferUtils.createFloatBuffer(16);
	private final FloatBuffer bufferOlhos = BufferUtils.createFloatBuffer(4 * 3);
	private int vboOlhos, vboSubstituto;
	private int quantidadeOlhos;

	/** O personagem já carregado, ou null para usar o substituto. Só na thread principal. */
	public DesenhoMonstro(PersonagemMd2 personagem) {
		this.personagem = personagem;
		olhos = personagem != null ? PontoMd2.naSkin(personagem.malhaCorpo(), OLHO_U, OLHO_V) : List.of();

		vboOlhos = glGenBuffers();
		glBindBuffer(GL_ARRAY_BUFFER, vboOlhos);
		glBufferData(GL_ARRAY_BUFFER, 4 * 3 * 4, GL_STREAM_DRAW);

		if (personagem == null) {
			// retângulo em pé no plano xy, com a base no chão: posição 3, cor 3, normal 3 (zero), uv 2
			float l = LARGURA_SUBSTITUTO / 2, a = ALTURA_SUBSTITUTO;
			float[][] cantos = { { -l, 0 }, { l, 0 }, { l, a }, { l, a }, { -l, a }, { -l, 0 } };
			FloatBuffer b = BufferUtils.createFloatBuffer(6 * 11);
			for (float[] c : cantos) {
				b.put(c[0]).put(c[1]).put(0).put(1).put(1).put(1).put(0).put(0).put(0).put(0).put(0);
			}
			b.flip();
			vboSubstituto = glGenBuffers();
			glBindBuffer(GL_ARRAY_BUFFER, vboSubstituto);
			glBufferData(GL_ARRAY_BUFFER, b, GL_STATIC_DRAW);
		}
		glBindBuffer(GL_ARRAY_BUFFER, 0);
	}

	/**
	 * Desenha o monstro (se estiver visível). As matrizes vêm prontas (storeTranspose);
	 * cameraX/Z servem para o substituto virar para a câmera; alturaImagem é a altura, em pixels,
	 * da imagem onde a cena está sendo desenhada (a janela ou a imagem pequena do modo PS1).
	 */
	public void desenhar(DragonKnight m, FloatBuffer projecao, FloatBuffer visao, Ambiente ambiente,
			float cameraX, float cameraZ, int alturaImagem) {
		if (!m.visivel()) {
			return;
		}
		quantidadeOlhos = 0;
		if (personagem != null) {
			desenharMd2(m, projecao, visao, ambiente);
		} else {
			desenharSubstituto(m, projecao, visao, ambiente, cameraX, cameraZ);
		}
		if (m.estado() != DragonKnight.Estado.MORRENDO) { // morto, os olhos apagam
			desenharOlhos(projecao, visao, ambiente, alturaImagem);
		}
	}

	private void desenharMd2(DragonKnight m, FloatBuffer projecao, FloatBuffer visao, Ambiente ambiente) {
		Animador a = m.animador();
		ShaderMd2 shader = Recursos.shaderMd2();
		shader.start();
		int p = shader.programID;
		glUniformMatrix4fv(glGetUniformLocation(p, "projection"), false, projecao);
		glUniformMatrix4fv(glGetUniformLocation(p, "view"), false, visao);
		glUniformMatrix4fv(glGetUniformLocation(p, "model"), false, guardar(modelo(m.x, m.y, m.z, m.guinada)));
		glUniform1f(glGetUniformLocation(p, "emissividade"), EMISSIVIDADE);
		glUniform1f(glGetUniformLocation(p, "dano"), m.marcaDano());
		ambiente.enviar(shader);
		personagem.desenhar(shader, a.quadroA(), a.quadroB(), a.fator(), true, true);
		shader.stop();

		// os olhos acompanham a animação: a mesma mistura de quadros que o vertex shader faz
		MalhaMd2 malha = personagem.malhaCorpo();
		float c = (float) Math.cos(m.guinada), s = (float) Math.sin(m.guinada);
		bufferOlhos.clear();
		for (PontoMd2 olho : olhos) {
			if (quantidadeOlhos == 4) break;
			float[] l = olho.posicao(malha, a.quadroA(), a.quadroB(), a.fator());
			float[] n = olho.normal(malha, a.quadroA(), a.quadroB(), a.fator());
			for (int k = 0; k < 3; k++) {
				l[k] += n[k] * 0.03f; // um pouco para fora da pele, para não ficar escondido nela
			}
			// o mesmo giro do modelo (rotação em y), depois a posição
			bufferOlhos.put(m.x + c * l[0] + s * l[2]).put(m.y + l[1]).put(m.z - s * l[0] + c * l[2]);
			quantidadeOlhos++;
		}
		bufferOlhos.flip();
	}

	private void desenharSubstituto(DragonKnight m, FloatBuffer projecao, FloatBuffer visao, Ambiente ambiente,
			float cameraX, float cameraZ) {
		// o retângulo tem a frente em +z; girar por atan2(dx, dz) leva +z para a câmera
		float virar = (float) Math.atan2(cameraX - m.x, cameraZ - m.z);
		ShaderObjeto shader = Recursos.shaderObjeto();
		shader.start();
		int p = shader.programID;
		glUniformMatrix4fv(glGetUniformLocation(p, "projection"), false, projecao);
		glUniformMatrix4fv(glGetUniformLocation(p, "view"), false, visao);
		glUniformMatrix4fv(glGetUniformLocation(p, "model"), false, guardar(modelo(m.x, m.y, m.z, virar)));
		glUniform1f(glGetUniformLocation(p, "emissividade"), 0.35f + m.marcaDano());
		glUniform1i(glGetUniformLocation(p, "tex"), 0);
		ambiente.enviar(shader);
		glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
		glBindTexture(GL_TEXTURE_2D, Recursos.texturaGerada("monstro substituto", () -> {
			BufferedImage img = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
			for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) img.setRGB(x, y, 0xFF8A0E0A);
			return img;
		}, false, true).id());

		int bytes = 11 * 4;
		glBindBuffer(GL_ARRAY_BUFFER, vboSubstituto);
		glEnableVertexAttribArray(0);
		glVertexAttribPointer(0, 3, GL_FLOAT, false, bytes, 0);
		glDisableVertexAttribArray(1);
		glVertexAttrib3f(1, 1, 1, 1);
		glEnableVertexAttribArray(2);
		glVertexAttribPointer(2, 3, GL_FLOAT, false, bytes, 24);
		glEnableVertexAttribArray(3);
		glVertexAttribPointer(3, 2, GL_FLOAT, false, bytes, 36);
		glDrawArrays(GL_TRIANGLES, 0, 6);
		glBindBuffer(GL_ARRAY_BUFFER, 0);
		shader.stop();

		// dois olhos no alto do retângulo, um pouco à frente dele
		float c = (float) Math.cos(virar), s = (float) Math.sin(virar);
		bufferOlhos.clear();
		for (float lado : new float[] { -0.22f, 0.22f }) {
			float lx = lado, lz = 0.05f;
			bufferOlhos.put(m.x + c * lx + s * lz).put(m.y + ALTURA_SUBSTITUTO * 0.85f).put(m.z - s * lx + c * lz);
		}
		bufferOlhos.flip();
		quantidadeOlhos = 2;
	}

	private void desenharOlhos(FloatBuffer projecao, FloatBuffer visao, Ambiente ambiente, int alturaImagem) {
		if (quantidadeOlhos == 0) return;
		glBindBuffer(GL_ARRAY_BUFFER, vboOlhos);
		glBufferSubData(GL_ARRAY_BUFFER, 0, bufferOlhos);

		ShaderBrilho shader = Recursos.shaderBrilho();
		shader.start();
		int p = shader.programID;
		glUniformMatrix4fv(glGetUniformLocation(p, "projection"), false, projecao);
		glUniformMatrix4fv(glGetUniformLocation(p, "view"), false, visao);
		glUniform1f(glGetUniformLocation(p, "tamanho"), TAMANHO_OLHO);
		glUniform1f(glGetUniformLocation(p, "alturaTela"), alturaImagem);
		glUniform3f(glGetUniformLocation(p, "corBrilho"), COR_OLHO[0], COR_OLHO[1], COR_OLHO[2]);
		ambiente.enviar(shader);

		// somado à cena (GL_ONE, GL_ONE), sem gravar profundidade: troncos na frente ainda escondem
		boolean tinhaBlend = glIsEnabled(GL_BLEND);
		glEnable(GL_BLEND);
		glBlendFunc(GL_ONE, GL_ONE);
		glDepthMask(false);
		glEnable(GL_PROGRAM_POINT_SIZE); // o tamanho vem do gl_PointSize do shader
		// o projeto cria o contexto OpenGL no perfil de compatibilidade (o padrão do GLFW): nele,
		// o gl_PointCoord (a posição dentro do ponto, usada para fazer o círculo) só existe com isto
		glEnable(GL_POINT_SPRITE);

		glEnableVertexAttribArray(0);
		glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);
		glDrawArrays(GL_POINTS, 0, quantidadeOlhos);

		glDisable(GL_POINT_SPRITE);
		glDisable(GL_PROGRAM_POINT_SIZE);
		glDepthMask(true);
		glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
		if (!tinhaBlend) glDisable(GL_BLEND);
		glBindBuffer(GL_ARRAY_BUFFER, 0);
		shader.stop();
	}

	/**
	 * Um olho em relação aos pés do monstro, no espaço do modelo (sem o giro), no quadro de agora.
	 * Usado para pôr a cabeça bem na frente da câmera no susto.
	 */
	public float[] olhoLocal(DragonKnight m) {
		if (personagem == null || olhos.isEmpty()) {
			return new float[] { 0, ALTURA_SUBSTITUTO * 0.85f, 0 };
		}
		Animador a = m.animador();
		return olhos.get(0).posicao(personagem.malhaCorpo(), a.quadroA(), a.quadroB(), a.fator());
	}

	/** A matriz model: primeiro gira em volta de y, depois leva até a posição. */
	private static Matrix4f modelo(float x, float y, float z, float guinada) {
		Matrix4f m = new Matrix4f();
		m.translate(new Vector3f(x, y, z));
		m.rotate(guinada, new Vector3f(0, 1, 0));
		return m;
	}

	private FloatBuffer guardar(Matrix4f m) {
		bufferMatriz.clear();
		m.storeTranspose(bufferMatriz);
		bufferMatriz.flip();
		return bufferMatriz;
	}

	public void liberar() {
		glDeleteBuffers(vboOlhos);
		if (vboSubstituto != 0) glDeleteBuffers(vboSubstituto);
	}
}
