package render;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL14.GL_DEPTH_COMPONENT24;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

import java.nio.FloatBuffer;

import org.lwjgl.BufferUtils;

import dados.Recursos;
import shaders.ShaderPs1Tela;

/**
 * O "modo PS1" da tela: a cena 3D é desenhada numa imagem pequena (240 linhas, como no
 * PlayStation) dentro de um framebuffer, e depois ampliada para a janela sem suavização
 * (shader ps1Tela). A redução de cores com pontilhado existe, mas fica desligada: no monitor
 * moderno, numa cena quase toda escura, ela aparece como uma grade por cima do jogo.
 *
 * Uso: comecar() antes de desenhar a cena e terminar() depois. O HUD vem depois, na
 * resolução da janela, para continuar legível.
 *
 * O mesmo caminho serve de pós-processamento: com o modo PS1 desligado, a imagem tem o tamanho
 * da janela. Nos dois casos, o shader pode escurecer os cantos (vinheta), pôr granulação de
 * filme e, com o monstro por perto, separar as cores e ondular a imagem (os campos abaixo).
 */
public class FiltroPs1 {

	/** Altura mínima da imagem pequena (o PlayStation desenhava 240 linhas). */
	public static final int ALTURA_ALVO = 240;
	/**
	 * Níveis de cor por canal. 32 seria o console (5 bits por canal, com pontilhado), mas na
	 * TV de tubo o pontilhado se borrava e no monitor vira uma grade. 0 desliga a redução.
	 */
	public static final float NIVEIS_COR = 0;

	/** Efeitos da passagem para a tela, de 0 a 1 (0 = desligado). */
	public float vinheta = 0, granulacao = 0, aberracao = 0;
	/** Segundos, para a granulação e a ondulação mudarem com o tempo. */
	public float tempo = 0;

	private int fbo, cor, profundidade;
	private int largura, altura;
	private int vbo;

	/**
	 * Tamanho da imagem pequena para uma janela: divide a janela pela maior escala inteira que
	 * ainda deixa pelo menos 240 linhas (cada pixel vira um bloco do mesmo tamanho).
	 */
	public static int[] tamanhoBaixo(int larguraTela, int alturaTela) {
		int escala = Math.max(1, alturaTela / ALTURA_ALVO);
		return new int[] { Math.max(1, larguraTela / escala), Math.max(1, alturaTela / escala) };
	}

	public int largura() {
		return largura;
	}

	public int altura() {
		return altura;
	}

	/** Passa a desenhar na imagem pequena. larguraTela e alturaTela: o tamanho real da janela. */
	public void comecar(int larguraTela, int alturaTela) {
		comecar(larguraTela, alturaTela, true);
	}

	/** Passa a desenhar na imagem da cena: pequena (modo PS1) ou do tamanho da janela. */
	public void comecar(int larguraTela, int alturaTela, boolean baixaResolucao) {
		int[] t = baixaResolucao ? tamanhoBaixo(larguraTela, alturaTela) : new int[] { larguraTela, alturaTela };
		if (fbo == 0 || t[0] != largura || t[1] != altura) {
			criar(t[0], t[1]); // primeira vez, ou a janela mudou de tamanho
		}
		glBindFramebuffer(GL_FRAMEBUFFER, fbo);
		glViewport(0, 0, largura, altura);
	}

	/** Volta para a janela e desenha a imagem pequena ampliada, sem suavização. */
	public void terminar(int larguraTela, int alturaTela) {
		glBindFramebuffer(GL_FRAMEBUFFER, 0);
		glViewport(0, 0, larguraTela, alturaTela);
		boolean profundidadeLigada = glIsEnabled(GL_DEPTH_TEST);
		glDisable(GL_DEPTH_TEST);

		ShaderPs1Tela shader = Recursos.shaderPs1Tela();
		shader.start();
		int p = shader.programID;
		glUniform1i(glGetUniformLocation(p, "cena"), 0);
		glUniform2f(glGetUniformLocation(p, "resolucao"), largura, altura);
		glUniform1f(glGetUniformLocation(p, "niveis"), NIVEIS_COR);
		glUniform1f(glGetUniformLocation(p, "tempo"), tempo);
		glUniform1f(glGetUniformLocation(p, "vinheta"), vinheta);
		glUniform1f(glGetUniformLocation(p, "granulacao"), granulacao);
		glUniform1f(glGetUniformLocation(p, "aberracao"), aberracao);
		glActiveTexture(GL_TEXTURE0);
		glBindTexture(GL_TEXTURE_2D, cor);

		glBindBuffer(GL_ARRAY_BUFFER, vbo);
		glEnableVertexAttribArray(0);
		glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);
		glDrawArrays(GL_TRIANGLES, 0, 6);
		glDisableVertexAttribArray(0);
		glBindBuffer(GL_ARRAY_BUFFER, 0);
		shader.stop();

		if (profundidadeLigada) {
			glEnable(GL_DEPTH_TEST);
		}
	}

	/** Framebuffer com uma textura de cor (sem suavização) e um buffer de profundidade. */
	private void criar(int w, int h) {
		liberar();
		largura = w;
		altura = h;

		cor = glGenTextures();
		glBindTexture(GL_TEXTURE_2D, cor);
		glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0L);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

		profundidade = glGenRenderbuffers();
		glBindRenderbuffer(GL_RENDERBUFFER, profundidade);
		glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, w, h);

		fbo = glGenFramebuffers();
		glBindFramebuffer(GL_FRAMEBUFFER, fbo);
		glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, cor, 0);
		glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, profundidade);
		if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
			throw new IllegalStateException("framebuffer do modo PS1 incompleto");
		}
		glBindFramebuffer(GL_FRAMEBUFFER, 0);

		// dois triângulos cobrindo a tela inteira
		FloatBuffer cantos = BufferUtils.createFloatBuffer(18);
		cantos.put(new float[] { -1, -1, 0, 1, -1, 0, 1, 1, 0, 1, 1, 0, -1, 1, 0, -1, -1, 0 }).flip();
		vbo = glGenBuffers();
		glBindBuffer(GL_ARRAY_BUFFER, vbo);
		glBufferData(GL_ARRAY_BUFFER, cantos, GL_STATIC_DRAW);
		glBindBuffer(GL_ARRAY_BUFFER, 0);
	}

	public void liberar() {
		if (fbo != 0) {
			glDeleteFramebuffers(fbo);
			glDeleteRenderbuffers(profundidade);
			glDeleteTextures(cor);
			glDeleteBuffers(vbo);
			fbo = 0;
		}
	}
}
