package render;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL20.*;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;

import dados.Recursos;
import shaders.ShaderObjeto;
import util.Caminhos;
import util.TextureLoader;

/**
 * A silhueta do susto falso: uma figura alta e magra demais, parada, com dois pontinhos claros no
 * lugar dos olhos. É uma placa fina (o desenho tem fundo transparente, que o shader descarta)
 * virada para o jogador, e os olhos são duas caixinhas que brilham um pouco na neblina.
 */
public class DesenhoSilhueta {

	public static final float LARGURA = 0.8f, ALTURA = 2.4f;

	private final MalhaCaixas corpo = new MalhaCaixas(), olhos = new MalhaCaixas();
	private final FloatBuffer buffer = BufferUtils.createFloatBuffer(16);

	public DesenhoSilhueta() {
		float[] tudo = { 0, 0, 1, 1 }, vazio = { 0, 0, 0.02f, 0.02f }; // o canto de cima à esquerda é transparente
		corpo.caixa(-LARGURA / 2, LARGURA / 2, 0, ALTURA, -0.01f, 0.01f, tudo, tudo, vazio).enviar();
		float y = ALTURA * 0.905f, lado = 0.05f; // grandes o bastante para virarem um pixel de longe
		float[] liso = { 0.4f, 0.4f, 0.6f, 0.6f };
		for (float x : new float[] { -0.05f, 0.05f }) {
			olhos.caixa(x - lado / 2, x + lado / 2, y - lado / 2, y + lado / 2, 0.012f, 0.02f, liso, liso, liso);
		}
		olhos.enviar();
	}

	/** Desenha a silhueta com os pés em (x, y, z), a frente virada pela guinada (0 = olhando para +z). */
	public void desenhar(float x, float y, float z, float guinada, FloatBuffer projecao, FloatBuffer visao,
			Ambiente ambiente) {
		ShaderObjeto shader = Recursos.shaderObjeto();
		shader.start();
		int p = shader.programID;
		glUniformMatrix4fv(glGetUniformLocation(p, "projection"), false, projecao);
		glUniformMatrix4fv(glGetUniformLocation(p, "view"), false, visao);
		glUniform1i(glGetUniformLocation(p, "tex"), 0);
		glUniform1i(glGetUniformLocation(p, "usaNormalMap"), 0);
		ambiente.enviar(shader);
		glActiveTexture(GL_TEXTURE0);

		Matrix4f m = new Matrix4f();
		m.translate(new Vector3f(x, y, z));
		m.rotate(guinada, new Vector3f(0, 1, 0));
		buffer.clear();
		m.storeTranspose(buffer);
		buffer.flip();
		glUniformMatrix4fv(glGetUniformLocation(p, "model"), false, buffer);

		glUniform1f(glGetUniformLocation(p, "emissividade"), 0f);
		glBindTexture(GL_TEXTURE_2D, Recursos.texturaGerada("silhueta", DesenhoSilhueta::figura, false, false).id());
		corpo.desenhar();
		glUniform1f(glGetUniformLocation(p, "emissividade"), 0.9f);
		glBindTexture(GL_TEXTURE_2D, Recursos.texturaGerada("silhueta: olhos", DesenhoSilhueta::olho, false, false).id());
		olhos.desenhar();
		shader.stop();
	}

	/** sprites/silhueta.png, ou a figura desenhada: cabeça pequena, pescoço comprido, braços até os joelhos. */
	public static BufferedImage figura() {
		if (Caminhos.existe("sprites/silhueta.png")) {
			BufferedImage img = TextureLoader.loadImage(Caminhos.caminho("sprites/silhueta.png"));
			if (img != null) return img;
		}
		BufferedImage img = new BufferedImage(64, 192, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new Color(30, 27, 27)); // quase preta: a borda do facho ainda a revela
		g.rotate(Math.toRadians(-6), 32, 20);                 // a cabeça meio torta
		g.fillOval(26, 8, 13, 19);                            // cabeça
		g.rotate(Math.toRadians(6), 32, 20);
		g.fillRect(30, 25, 5, 12);                            // pescoço comprido
		Path2D tronco = new Path2D.Float();
		tronco.moveTo(21, 38);
		tronco.lineTo(43, 38);
		tronco.lineTo(40, 100);
		tronco.lineTo(24, 100);
		tronco.closePath();
		g.fill(tronco);
		g.fillRoundRect(17, 37, 6, 86, 5, 5);                 // braços até os joelhos
		g.fillRoundRect(41, 37, 6, 84, 5, 5);
		g.fillRoundRect(25, 98, 6, 92, 4, 4);                 // pernas finas
		g.fillRoundRect(33, 98, 6, 92, 4, 4);
		g.dispose();
		return img;
	}

	private static BufferedImage olho() {
		BufferedImage img = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) img.setRGB(x, y, new Color(215, 210, 190).getRGB());
		return img;
	}

	public void liberar() {
		corpo.liberar();
		olhos.liberar();
	}
}
