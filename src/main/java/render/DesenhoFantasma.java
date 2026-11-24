package render;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL20.*;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.util.Random;

import org.lwjgl.BufferUtils;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;

import dados.Recursos;
import shaders.ShaderObjeto;
import util.Caminhos;
import util.TextureLoader;

/**
 * O fantasma: uma figura pálida e translúcida, de olhos e boca vazios, que termina em fiapos em
 * vez de pés. É uma placa fina sempre virada para a câmera, desenhada com mistura (glBlend) e sem
 * escrever na profundidade (o que está atrás continua aparecendo através dele). Brilha um pouco
 * sozinho (emissividade), então se vê no escuro e também atravessa a neblina.
 */
public class DesenhoFantasma {

	public static final float LARGURA = 0.9f, ALTURA = 2.1f;
	/** Opacidade máxima (com alfa 1, ainda dá para ver através dele). */
	private static final float OPACIDADE = 0.55f;

	private final MalhaCaixas placa = new MalhaCaixas();
	private final FloatBuffer buffer = BufferUtils.createFloatBuffer(16);

	public DesenhoFantasma() {
		float[] tudo = { 0, 0, 1, 1 }, vazio = { 0, 0, 0.02f, 0.02f }; // o canto de cima à esquerda é transparente
		placa.caixa(-LARGURA / 2, LARGURA / 2, 0, ALTURA, -0.01f, 0.01f, tudo, tudo, vazio).enviar();
	}

	/**
	 * Desenha com a base em (x, y, z), flutuando (sobe e desce devagar), virado para a câmera.
	 * alfa: de 0 (invisível) a 1 (aparecendo por inteiro). Chamar depois das coisas opacas.
	 */
	public void desenhar(float x, float y, float z, float alfa, float tempo, float cameraX, float cameraZ,
			FloatBuffer projecao, FloatBuffer visao, Ambiente ambiente) {
		if (alfa <= 0.01f) return;
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
		m.translate(new Vector3f(x, y + 0.25f + 0.12f * (float) Math.sin(tempo * 1.7f), z));
		m.rotate((float) Math.atan2(cameraX - x, cameraZ - z), new Vector3f(0, 1, 0)); // a frente (+z) para a câmera
		buffer.clear();
		m.storeTranspose(buffer);
		buffer.flip();
		glUniformMatrix4fv(glGetUniformLocation(p, "model"), false, buffer);
		glUniform1f(glGetUniformLocation(p, "emissividade"), 0.75f * alfa);
		glUniform1f(glGetUniformLocation(p, "transparencia"), 1 - OPACIDADE * alfa);
		glBindTexture(GL_TEXTURE_2D, Recursos.texturaGerada("fantasma", DesenhoFantasma::figura, false, false).id());

		boolean misturava = glIsEnabled(GL_BLEND);
		glEnable(GL_BLEND);
		glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
		glDepthMask(false); // translúcido: não esconde o que vier depois
		placa.desenhar();
		glDepthMask(true);
		if (!misturava) glDisable(GL_BLEND);
		glUniform1f(glGetUniformLocation(p, "transparencia"), 0); // o shader é compartilhado: volta a opaco
		shader.stop();
	}

	/** sprites/fantasma.png, ou a figura desenhada: cabelo comprido, rosto vazio, corpo que vira fiapos. */
	public static BufferedImage figura() {
		if (Caminhos.existe("sprites/fantasma.png")) {
			BufferedImage img = TextureLoader.loadImage(Caminhos.caminho("sprites/fantasma.png"));
			if (img != null) return img;
		}
		int w = 64, h = 150;
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		Color palido = new Color(205, 222, 235), sombra = new Color(150, 170, 190);
		// o corpo: um vestido longo que se abre para baixo
		Path2D corpo = new Path2D.Float();
		corpo.moveTo(24, 30);
		corpo.lineTo(40, 30);
		corpo.curveTo(46, 60, 52, 100, 58, 140);
		corpo.lineTo(6, 140);
		corpo.curveTo(12, 100, 18, 60, 24, 30);
		g.setPaint(new GradientPaint(0, 30, palido, 0, 140, sombra));
		g.fill(corpo);
		g.fillRoundRect(13, 34, 7, 52, 6, 6);  // braços caídos
		g.fillRoundRect(44, 34, 7, 52, 6, 6);
		g.setColor(palido);
		g.fillOval(22, 6, 20, 26);             // cabeça
		g.setColor(new Color(170, 185, 200));
		g.fillRect(20, 8, 4, 40);              // cabelo comprido dos lados
		g.fillRect(40, 8, 4, 40);
		g.setColor(new Color(18, 20, 28));     // olhos e boca vazios
		g.fillOval(26, 15, 5, 6);
		g.fillOval(33, 15, 5, 6);
		g.fillOval(29, 24, 6, 5);
		g.dispose();
		// de baixo: em vez de pés, fiapos (pixels somem cada vez mais para baixo, como no PS1)
		Random r = new Random(5);
		for (int y = 100; y < h; y++) {
			float some = (y - 100) / 50f;
			for (int x = 0; x < w; x++) {
				if ((img.getRGB(x, y) >>> 24) > 0 && r.nextFloat() < some * 1.1f) img.setRGB(x, y, 0);
			}
		}
		return img;
	}

	public void liberar() {
		placa.liberar();
	}
}
