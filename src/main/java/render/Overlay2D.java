package render;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;

import java.awt.Color;
import java.nio.FloatBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.util.vector.Matrix4f;

import shaders.ShaderHud;

/**
 * Desenha retângulos e imagens em coordenadas de tela, em pixels, com a origem no
 * canto superior esquerdo (as mesmas coordenadas do mouse).
 *
 * Uso, sempre depois da cena 3D:
 *   overlay.iniciar(largura, altura);
 *   overlay.retangulo(...); overlay.imagem(...); Texto.desenhar(overlay, ...);
 *   overlay.finalizar();
 *
 * Todo desenho usa o mesmo VBO: um quadrado de 0 a 1 que o vertex shader estica até
 * o retângulo pedido. O iniciar() desliga o teste de profundidade e liga a transparência;
 * o finalizar() devolve o OpenGL como estava, para não estragar o próximo quadro 3D.
 */
public class Overlay2D {

	private static final int FLOATS_POR_VERTICE = 5; // x, y, z, u, v
	private static final int BYTES_POR_VERTICE = FLOATS_POR_VERTICE * 4;

	private final ShaderHud shader;
	private final int vbo;

	private final int locProjecao, locRetangulo, locRecorte, locAngulo, locCor, locUsaTextura, locTex;
	private final FloatBuffer bufferMatriz = BufferUtils.createFloatBuffer(16);
	private final float[] rgba = new float[4];

	// estado do OpenGL guardado no iniciar() e devolvido no finalizar()
	private boolean tinhaProfundidade, tinhaTransparencia, tinhaCulling;
	private int blendOrigem, blendDestino;

	public Overlay2D() {
		shader = new ShaderHud();
		int p = shader.programID;
		locProjecao = glGetUniformLocation(p, "projecao");
		locRetangulo = glGetUniformLocation(p, "retangulo");
		locRecorte = glGetUniformLocation(p, "recorte");
		locAngulo = glGetUniformLocation(p, "angulo");
		locCor = glGetUniformLocation(p, "cor");
		locUsaTextura = glGetUniformLocation(p, "usaTextura");
		locTex = glGetUniformLocation(p, "tex");

		// quadrado unitário em dois triângulos; a coordenada de textura é igual à posição
		float[][] cantos = { { 0, 0 }, { 1, 0 }, { 1, 1 }, { 1, 1 }, { 0, 1 }, { 0, 0 } };
		FloatBuffer dados = BufferUtils.createFloatBuffer(6 * FLOATS_POR_VERTICE);
		for (float[] c : cantos) {
			dados.put(c[0]).put(c[1]).put(0f); // posição
			dados.put(c[0]).put(c[1]);         // uv
		}
		dados.flip();
		vbo = glGenBuffers();
		glBindBuffer(GL_ARRAY_BUFFER, vbo);
		glBufferData(GL_ARRAY_BUFFER, dados, GL_STATIC_DRAW);
		glBindBuffer(GL_ARRAY_BUFFER, 0);
	}

	/** Prepara o OpenGL para desenhar em 2D numa área de largura x altura pixels. */
	public void iniciar(int largura, int altura) {
		tinhaProfundidade = glIsEnabled(GL_DEPTH_TEST);
		tinhaTransparencia = glIsEnabled(GL_BLEND);
		tinhaCulling = glIsEnabled(GL_CULL_FACE);
		blendOrigem = glGetInteger(GL_BLEND_SRC);
		blendDestino = glGetInteger(GL_BLEND_DST);

		glDisable(GL_DEPTH_TEST);
		glDisable(GL_CULL_FACE);
		glEnable(GL_BLEND);
		glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

		shader.start();
		Matrix4f orto = ortografica(largura, altura);
		bufferMatriz.clear();
		orto.storeTranspose(bufferMatriz);
		bufferMatriz.flip();
		glUniformMatrix4fv(locProjecao, false, bufferMatriz);
		glUniform1i(locTex, 0);
		glActiveTexture(GL_TEXTURE0);

		glBindBuffer(GL_ARRAY_BUFFER, vbo);
		glEnableVertexAttribArray(0);
		glVertexAttribPointer(0, 3, GL_FLOAT, false, BYTES_POR_VERTICE, 0);
		glEnableVertexAttribArray(3);
		glVertexAttribPointer(3, 2, GL_FLOAT, false, BYTES_POR_VERTICE, 3 * 4);
	}

	/** Retângulo de cor sólida (a transparência vem do alfa da cor). */
	public void retangulo(float x, float y, float largura, float altura, Color cor) {
		glUniform1i(locUsaTextura, 0);
		desenharQuad(x, y, largura, altura, 0, 0, 1, 1, cor, 0);
	}

	/**
	 * Retângulo de cor sólida girado em volta do próprio centro (cx, cy).
	 * Ângulo em radianos; positivo gira no sentido horário (o y da tela cresce para baixo).
	 */
	public void retanguloGirado(float cx, float cy, float largura, float altura, float angulo, Color cor) {
		glUniform1i(locUsaTextura, 0);
		desenharQuad(cx - largura / 2, cy - altura / 2, largura, altura, 0, 0, 1, 1, cor, angulo);
	}

	/** Uma textura inteira, multiplicada pela cor (Color.WHITE = como está na imagem). */
	public void imagem(int textura, float x, float y, float largura, float altura, Color cor) {
		imagemRecorte(textura, x, y, largura, altura, 0, 0, 1, 1, cor);
	}

	/**
	 * Só uma parte da textura (por exemplo, um quadro de uma spritesheet).
	 * u0, v0 é o canto superior esquerdo do recorte e u1, v1 o inferior direito, de 0 a 1.
	 */
	public void imagemRecorte(int textura, float x, float y, float largura, float altura,
			float u0, float v0, float u1, float v1, Color cor) {
		glBindTexture(GL_TEXTURE_2D, textura);
		glUniform1i(locUsaTextura, 1);
		desenharQuad(x, y, largura, altura, u0, v0, u1, v1, cor, 0);
	}

	/** Devolve o OpenGL ao estado em que estava antes do iniciar(). */
	public void finalizar() {
		glDisableVertexAttribArray(0);
		glDisableVertexAttribArray(3);
		glBindBuffer(GL_ARRAY_BUFFER, 0);
		shader.stop();

		glBlendFunc(blendOrigem, blendDestino);
		ligar(GL_BLEND, tinhaTransparencia);
		ligar(GL_DEPTH_TEST, tinhaProfundidade);
		ligar(GL_CULL_FACE, tinhaCulling);
	}

	/** Apaga o VBO e o shader. Chamado ao fechar o jogo. */
	public void liberar() {
		glDeleteBuffers(vbo);
		shader.cleanUp();
	}

	private void desenharQuad(float x, float y, float largura, float altura,
			float u0, float v0, float u1, float v1, Color cor, float angulo) {
		cor.getRGBComponents(rgba);
		glUniform4f(locCor, rgba[0], rgba[1], rgba[2], rgba[3]);
		glUniform4f(locRetangulo, x, y, largura, altura);
		glUniform4f(locRecorte, u0, v0, u1, v1);
		glUniform1f(locAngulo, angulo);
		glDrawArrays(GL_TRIANGLES, 0, 6);
	}

	/**
	 * Projeção ortográfica que leva x de 0..largura para -1..1 e y de 0..altura para 1..-1
	 * (o y da tela cresce para baixo; o do OpenGL, para cima).
	 * Nos campos da lwjgl_util, m30 e m31 são a translação (coluna 3).
	 */
	static Matrix4f ortografica(int largura, int altura) {
		Matrix4f m = new Matrix4f(); // começa como identidade
		m.m00 = 2f / largura;
		m.m11 = -2f / altura;
		m.m30 = -1f;
		m.m31 = 1f;
		return m;
	}

	private static void ligar(int capacidade, boolean ligado) {
		if (ligado) {
			glEnable(capacidade);
		} else {
			glDisable(capacidade);
		}
	}
}
