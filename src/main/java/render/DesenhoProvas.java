package render;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.util.List;

import org.lwjgl.BufferUtils;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;

import dados.Recursos;
import entidades.Prova;
import shaders.ShaderObjeto;
import util.Caminhos;
import util.TextureLoader;

/**
 * Desenha as provas: cada uma é uma folha (um retângulo de 30 x 42 cm) com o nome escrito à
 * mão, girando no ar, com brilho próprio, e um halo somado à cena em volta dela, que aparece
 * um pouco além da neblina. A folha não tem normal: o shader de objetos usa a da face,
 * virada para a câmera, então ela é vista dos dois lados.
 */
public class DesenhoProvas {

	private static final float LARGURA = 0.30f, ALTURA = 0.42f;
	private static final float EMISSIVIDADE = 0.3f;
	private static final float TAMANHO_HALO = 1.3f;
	private static final float[] COR_HALO = { 0.26f, 0.25f, 0.19f };

	private final int vbo;
	private final PontosBrilhantes halos = new PontosBrilhantes(8);
	private final FloatBuffer buffer = BufferUtils.createFloatBuffer(16);

	/** Só na thread principal. */
	public DesenhoProvas() {
		// retângulo em pé no plano xy, centrado: posição 3, normal 3 (zero), uv 2 na convenção
		// do shader de objetos (guardamos (1 - t, u))
		float l = LARGURA / 2, a = ALTURA / 2;
		float[][] cantos = { { -l, -a, 0, 1 }, { l, -a, 1, 1 }, { l, a, 1, 0 }, { l, a, 1, 0 }, { -l, a, 0, 0 },
				{ -l, -a, 0, 1 } };
		FloatBuffer b = BufferUtils.createFloatBuffer(6 * 8);
		for (float[] c : cantos) {
			b.put(c[0]).put(c[1]).put(0).put(0).put(0).put(0).put(1 - c[3]).put(c[2]);
		}
		b.flip();
		vbo = glGenBuffers();
		glBindBuffer(GL_ARRAY_BUFFER, vbo);
		glBufferData(GL_ARRAY_BUFFER, b, GL_STATIC_DRAW);
		glBindBuffer(GL_ARRAY_BUFFER, 0);
	}

	public void desenhar(List<Prova> provas, FloatBuffer projecao, FloatBuffer visao, Ambiente ambiente, int alturaImagem) {
		ShaderObjeto shader = Recursos.shaderObjeto();
		shader.start();
		int p = shader.programID;
		glUniformMatrix4fv(glGetUniformLocation(p, "projection"), false, projecao);
		glUniformMatrix4fv(glGetUniformLocation(p, "view"), false, visao);
		glUniform1f(glGetUniformLocation(p, "emissividade"), EMISSIVIDADE);
		glUniform1i(glGetUniformLocation(p, "tex"), 0);
		glUniform1i(glGetUniformLocation(p, "usaNormalMap"), 0);
		ambiente.enviar(shader);
		glActiveTexture(GL_TEXTURE0);

		int bytes = 8 * 4;
		glBindBuffer(GL_ARRAY_BUFFER, vbo);
		glEnableVertexAttribArray(0);
		glVertexAttribPointer(0, 3, GL_FLOAT, false, bytes, 0);
		glDisableVertexAttribArray(1);
		glVertexAttrib3f(1, 1, 1, 1);
		glEnableVertexAttribArray(2);
		glVertexAttribPointer(2, 3, GL_FLOAT, false, bytes, 12);
		glEnableVertexAttribArray(3);
		glVertexAttribPointer(3, 2, GL_FLOAT, false, bytes, 24);

		halos.limpar();
		for (Prova prova : provas) {
			if (prova.coletada()) continue;
			Matrix4f m = new Matrix4f();
			m.translate(new Vector3f(prova.x, prova.y, prova.z));
			m.rotate(prova.giro(), new Vector3f(0, 1, 0));
			m.rotate(0.15f, new Vector3f(1, 0, 0)); // um pouco inclinada, como papel solto no ar
			buffer.clear();
			m.storeTranspose(buffer);
			buffer.flip();
			glUniformMatrix4fv(glGetUniformLocation(p, "model"), false, buffer);
			glBindTexture(GL_TEXTURE_2D, textura(prova.nome));
			glDrawArrays(GL_TRIANGLES, 0, 6);
			halos.adicionar(prova.x, prova.y, prova.z);
		}
		glBindBuffer(GL_ARRAY_BUFFER, 0);
		shader.stop();

		halos.desenhar(TAMANHO_HALO, COR_HALO, projecao, visao, ambiente, alturaImagem);
	}

	/** A textura da folha com o nome: texturas/prova.png por baixo, se existir; senão, papel pautado. */
	private static int textura(String nome) {
		return Recursos.texturaGerada("prova: " + nome, () -> folha(nome), false, false).id();
	}

	/** Uma folha de prova com o nome escrito à mão por cima. Não usa OpenGL. */
	public static BufferedImage folha(String nome) {
		int w = 150, h = 210;
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		BufferedImage base = Caminhos.existe("texturas/prova.png") ? TextureLoader.loadImage(Caminhos.caminho("texturas/prova.png"))
				: null;
		if (base != null) {
			g.drawImage(base, 0, 0, w, h, null);
		} else {
			g.setColor(new Color(226, 220, 200)); // papel envelhecido
			g.fillRect(0, 0, w, h);
			g.setColor(new Color(150, 170, 200));
			for (int y = 50; y < h - 10; y += 12) g.drawLine(8, y, w - 8, y); // pauta
			g.setColor(new Color(200, 90, 90));
			g.drawLine(24, 0, 24, h); // margem
			g.setColor(new Color(60, 55, 50));
			g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
			g.drawString("Computação Gráfica", 30, 18);
			g.drawString("Nome: ____________", 30, 32);
			// rabiscos de resposta
			g.setColor(new Color(40, 45, 90, 170));
			g.setStroke(new BasicStroke(1.2f));
			java.util.Random r = new java.util.Random(nome.hashCode());
			for (int y = 122; y < h - 14; y += 12) {
				int x = 30;
				while (x < w - 20) {
					int palavra = 6 + r.nextInt(18);
					g.drawLine(x, y - 2 + r.nextInt(3), x + palavra, y - 2 + r.nextInt(3));
					x += palavra + 5;
				}
			}
		}
		// o nome grande, "à mão": itálico, um pouco torto, com um círculo vermelho de correção
		g.rotate(-0.12, w / 2.0, 85);
		g.setColor(new Color(25, 30, 70));
		g.setFont(new Font(Font.SERIF, Font.BOLD | Font.ITALIC, 58));
		int largura = g.getFontMetrics().stringWidth(nome);
		g.drawString(nome, (w - largura) / 2, 105);
		g.setColor(new Color(180, 20, 20, 200));
		g.setStroke(new BasicStroke(3f));
		g.drawOval((w - largura) / 2 - 12, 52, largura + 24, 66);
		g.dispose();
		return img;
	}

	public void liberar() {
		glDeleteBuffers(vbo);
		halos.liberar();
	}
}
