package render;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL20.*;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.Random;

import org.lwjgl.BufferUtils;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;

import dados.Recursos;
import fase.Placa;
import shaders.ShaderObjeto;
import util.Caminhos;
import util.TextureLoader;

/**
 * As placas da floresta, montadas com caixas (MalhaCaixas) e texturas geradas por código:
 *  - do evento: dois postes e um painel escuro com o logo do Computer on the Beach
 *    (texturas/logo_cotb.png), que brilha de leve, como tinta refletiva;
 *  - de trilha: um poste de madeira velha com uma tábua, uma seta pintada e o nome da prova.
 *    A seta aponta para o +x do modelo; o sorteio já girou a placa para a seta apontar para a prova.
 */
public class DesenhoPlacas {

	public static final String LOGO = "texturas/logo_cotb.png";

	// pedaços das texturas {u0, t0, u1, t1}: em cima, as faces pintadas; embaixo, madeira lisa
	private static final float[] TRILHA_FRENTE = { 0, 0, 0.5f, 0.5f }, TRILHA_TRAS = { 0.5f, 0, 1, 0.5f },
			TRILHA_MADEIRA = { 0, 0.5f, 1, 1 };
	private static final float[] EVENTO_FRENTE = { 0, 0, 1, 0.75f }, EVENTO_MADEIRA = { 0, 0.75f, 1, 1 };

	private final MalhaCaixas trilha, evento;
	private final FloatBuffer buffer = BufferUtils.createFloatBuffer(16);

	/** Só na thread principal. */
	public DesenhoPlacas() {
		trilha = new MalhaCaixas()
				.caixa(-0.05f, 0.05f, 0, 1.55f, -0.05f, 0.05f, TRILHA_MADEIRA, TRILHA_MADEIRA, TRILHA_MADEIRA) // poste
				.caixa(-0.12f, 0.80f, 1.12f, 1.42f, 0.05f, 0.09f, TRILHA_FRENTE, TRILHA_TRAS, TRILHA_MADEIRA)   // tábua
				.enviar();
		evento = new MalhaCaixas()
				.caixa(-1.16f, -1.04f, 0, 2.3f, -0.06f, 0.06f, EVENTO_MADEIRA, EVENTO_MADEIRA, EVENTO_MADEIRA)
				.caixa(1.04f, 1.16f, 0, 2.3f, -0.06f, 0.06f, EVENTO_MADEIRA, EVENTO_MADEIRA, EVENTO_MADEIRA)
				.caixa(-1.35f, 1.35f, 1.2f, 2.2f, 0.06f, 0.12f, EVENTO_FRENTE, EVENTO_MADEIRA, EVENTO_MADEIRA)
				.enviar();
	}

	public void desenhar(List<Placa> placas, mundo.ConsultaAltura chao, FloatBuffer projecao,
			FloatBuffer visao, Ambiente ambiente) {
		ShaderObjeto shader = Recursos.shaderObjeto();
		shader.start();
		int p = shader.programID;
		glUniformMatrix4fv(glGetUniformLocation(p, "projection"), false, projecao);
		glUniformMatrix4fv(glGetUniformLocation(p, "view"), false, visao);
		glUniform1i(glGetUniformLocation(p, "tex"), 0);
		glUniform1i(glGetUniformLocation(p, "usaNormalMap"), 0);
		ambiente.enviar(shader);
		glActiveTexture(GL_TEXTURE0);
		for (Placa placa : placas) {
			Matrix4f m = new Matrix4f();
			m.translate(new Vector3f(placa.x(), chao.alturaEm(placa.x(), placa.z()) - 0.1f, placa.z()));
			m.rotate(placa.giro(), new Vector3f(0, 1, 0));
			buffer.clear();
			m.storeTranspose(buffer);
			buffer.flip();
			glUniformMatrix4fv(glGetUniformLocation(p, "model"), false, buffer);
			if (placa.evento()) {
				glUniform1f(glGetUniformLocation(p, "emissividade"), 0.18f);
				glBindTexture(GL_TEXTURE_2D, Recursos.texturaGerada("placa do evento", DesenhoPlacas::texturaEvento, false, false).id());
				evento.desenhar();
			} else {
				glUniform1f(glGetUniformLocation(p, "emissividade"), 0.04f);
				glBindTexture(GL_TEXTURE_2D,
						Recursos.texturaGerada("placa de trilha " + placa.texto(), () -> texturaTrilha(placa.texto()), false, false).id());
				trilha.desenhar();
			}
		}
		shader.stop();
	}

	// ---------- texturas (sem OpenGL) ----------

	/** Madeira velha: tábuas com veios escuros e manchas. */
	private static void madeira(Graphics2D g, int x, int y, int w, int h, long semente) {
		Random r = new Random(semente);
		g.setColor(new Color(74, 56, 40));
		g.fillRect(x, y, w, h);
		for (int i = 0; i < w * h / 6; i++) {
			int px = x + r.nextInt(w), py = y + r.nextInt(h);
			int v = 40 + r.nextInt(50);
			g.setColor(new Color(v + 20, v, v / 2 + 10));
			g.fillRect(px, py, 1 + r.nextInt(6), 1); // veios: traços na horizontal
		}
		g.setColor(new Color(30, 22, 16, 140));
		for (int i = 0; i < 3; i++) g.drawLine(x, y + r.nextInt(h), x + w, y + r.nextInt(h)); // frestas
	}

	/**
	 * A tábua de trilha, 256 x 128: em cima, a frente ("M1" e a seta para a direita) e o verso
	 * (a seta para a esquerda, porque de trás o lado da prova fica à esquerda); embaixo, madeira lisa.
	 */
	public static BufferedImage texturaTrilha(String nome) {
		BufferedImage img = new BufferedImage(256, 128, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		madeira(g, 0, 0, 256, 128, nome.hashCode());
		for (int lado = 0; lado < 2; lado++) {
			boolean direita = lado == 0;
			int x0 = lado * 128;
			Color tinta = new Color(214, 206, 186, 225);
			g.setColor(tinta);
			g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
			int ax = direita ? x0 + 70 : x0 + 8; // a seta do lado da prova
			g.drawString(nome, direita ? x0 + 10 : x0 + 62, 44);
			Polygon seta = new Polygon();
			int[][] pontos = { { 0, 24 }, { 34, 24 }, { 34, 12 }, { 52, 32 }, { 34, 52 }, { 34, 40 }, { 0, 40 } };
			for (int[] p : pontos) {
				seta.addPoint(ax + (direita ? p[0] : 52 - p[0]), p[1]);
			}
			g.fill(seta);
			// tinta descascada: falhas aleatórias por cima
			Random r = new Random(nome.hashCode() + lado);
			g.setColor(new Color(74, 56, 40));
			for (int i = 0; i < 40; i++) g.fillRect(x0 + r.nextInt(128), r.nextInt(64), 1 + r.nextInt(3), 1 + r.nextInt(2));
		}
		g.dispose();
		return img;
	}

	/**
	 * O painel do evento, 512 x 256: em cima (3/4), fundo azul-escuro com borda e o logo do
	 * Computer on the Beach centralizado; embaixo, madeira para os postes e o verso.
	 */
	public static BufferedImage texturaEvento() {
		BufferedImage img = new BufferedImage(512, 256, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setColor(new Color(16, 36, 58));
		g.fillRect(0, 0, 512, 192);
		g.setColor(new Color(214, 238, 205));
		g.setStroke(new BasicStroke(4));
		g.drawRect(6, 6, 500, 180);
		BufferedImage logo = Caminhos.existe(LOGO) ? TextureLoader.loadImage(Caminhos.caminho(LOGO)) : null;
		if (logo != null) {
			float escala = Math.min(470f / logo.getWidth(), 160f / logo.getHeight());
			int w = Math.round(logo.getWidth() * escala), h = Math.round(logo.getHeight() * escala);
			g.drawImage(logo, (512 - w) / 2, (192 - h) / 2, w, h, null);
		} else {
			g.setFont(new Font(Font.SANS_SERIF, Font.BOLD | Font.ITALIC, 44));
			g.drawString("COMPUTER ON THE BEACH", 18, 110);
		}
		madeira(g, 0, 192, 512, 64, 7);
		g.dispose();
		return img;
	}

	public void liberar() {
		trilha.liberar();
		evento.liberar();
	}
}
