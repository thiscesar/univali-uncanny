package render;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL20.GL_POINT_SPRITE;
import static org.lwjgl.opengl.GL32.GL_PROGRAM_POINT_SIZE;

import java.nio.FloatBuffer;

import org.lwjgl.BufferUtils;

import dados.Recursos;
import shaders.ShaderBrilho;

/**
 * Pontos que brilham sozinhos, somados à cena (os olhos do Dragon Knight, o halo das provas):
 * cada ponto vira um círculo suave na tela (GL_POINTS com o shader "brilho"), do tamanho que
 * um objeto daquele diâmetro teria àquela distância, e atravessa a neblina melhor que o resto.
 */
public class PontosBrilhantes {

	private final int maximo;
	private final FloatBuffer pontos;
	private final int vbo;
	private int quantidade = 0;

	/** Só na thread principal. */
	public PontosBrilhantes(int maximo) {
		this.maximo = maximo;
		pontos = BufferUtils.createFloatBuffer(maximo * 3);
		vbo = glGenBuffers();
		glBindBuffer(GL_ARRAY_BUFFER, vbo);
		glBufferData(GL_ARRAY_BUFFER, (long) maximo * 3 * 4, GL_STREAM_DRAW);
		glBindBuffer(GL_ARRAY_BUFFER, 0);
	}

	/** Esvazia a lista do quadro. */
	public void limpar() {
		pontos.clear();
		quantidade = 0;
	}

	/** Um ponto, no mundo. */
	public void adicionar(float x, float y, float z) {
		if (quantidade == maximo) return;
		pontos.put(x).put(y).put(z);
		quantidade++;
	}

	public int quantidade() {
		return quantidade;
	}

	/** Desenha os pontos da lista com o diâmetro (metros) e a cor dados. */
	public void desenhar(float tamanho, float[] cor, FloatBuffer projecao, FloatBuffer visao, Ambiente ambiente,
			int alturaImagem) {
		if (quantidade == 0) return;
		pontos.flip();
		glBindBuffer(GL_ARRAY_BUFFER, vbo);
		glBufferSubData(GL_ARRAY_BUFFER, 0, pontos);
		pontos.limit(pontos.capacity()); // continua aceitando pontos depois do desenho
		pontos.position(quantidade * 3);

		ShaderBrilho shader = Recursos.shaderBrilho();
		shader.start();
		int p = shader.programID;
		glUniformMatrix4fv(glGetUniformLocation(p, "projection"), false, projecao);
		glUniformMatrix4fv(glGetUniformLocation(p, "view"), false, visao);
		glUniform1f(glGetUniformLocation(p, "tamanho"), tamanho);
		glUniform1f(glGetUniformLocation(p, "alturaTela"), alturaImagem);
		glUniform3f(glGetUniformLocation(p, "corBrilho"), cor[0], cor[1], cor[2]);
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
		glDrawArrays(GL_POINTS, 0, quantidade);

		glDisable(GL_POINT_SPRITE);
		glDisable(GL_PROGRAM_POINT_SIZE);
		glDepthMask(true);
		glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
		if (!tinhaBlend) glDisable(GL_BLEND);
		glBindBuffer(GL_ARRAY_BUFFER, 0);
		shader.stop();
	}

	public void liberar() {
		glDeleteBuffers(vbo);
	}
}
