package render;

import static org.lwjgl.glfw.GLFW.*;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFWImage;

import util.Caminhos;
import util.TextureLoader;

/**
 * A identidade do jogo na janela: o ícone (interface/icone.png, ou um "U" pálido escorrendo
 * sangue, desenhado por código) e o cursor do mouse nas telas com cursor (uma seta cor de osso
 * com contorno escuro e a ponta vermelha). As imagens são feitas com Java2D e entregues ao GLFW
 * como RGBA, linha por linha, de cima para baixo.
 */
public final class Identidade {

	private Identidade() {}

	/** Aplica o ícone e o cursor na janela. Só na thread principal. */
	public static void aplicar(long janela) {
		// no Wayland, quem define o ícone é o sistema (o GLFW só avisaria um erro)
		if (glfwGetPlatform() != GLFW_PLATFORM_WAYLAND) {
			GLFWImage.Buffer icones = GLFWImage.malloc(2);
			BufferedImage grande = icone(64), pequeno = icone(32);
			icones.position(0).width(64).height(64).pixels(rgba(grande));
			icones.position(1).width(32).height(32).pixels(rgba(pequeno));
			icones.position(0);
			glfwSetWindowIcon(janela, icones); // o GLFW copia as imagens
			icones.free();
		}
		GLFWImage imagem = GLFWImage.malloc();
		BufferedImage seta = cursor();
		imagem.set(seta.getWidth(), seta.getHeight(), rgba(seta));
		long cursor = glfwCreateCursor(imagem, 2, 1); // o ponto ativo é a ponta da seta
		imagem.free();
		if (cursor != 0) {
			glfwSetCursor(janela, cursor); // vale enquanto o cursor estiver visível (menus, pausa, fim)
		}
	}

	/** O ícone, do arquivo ou desenhado: fundo escuro avermelhado e um "U" pálido escorrendo. */
	public static BufferedImage icone(int lado) {
		if (Caminhos.existe("interface/icone.png")) {
			BufferedImage arquivo = TextureLoader.loadImage(Caminhos.caminho("interface/icone.png"));
			if (arquivo != null) return redimensionar(arquivo, lado);
		}
		BufferedImage img = new BufferedImage(lado, lado, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		float s = lado / 64f;
		g.setPaint(new RadialGradientPaint(lado * 0.5f, lado * 0.4f, lado * 0.7f, new float[] { 0, 1 },
				new Color[] { new Color(70, 12, 12), new Color(8, 4, 4) }));
		g.fillRoundRect(0, 0, lado, lado, Math.round(14 * s), Math.round(14 * s));
		g.setFont(new Font(Font.SERIF, Font.BOLD, Math.round(52 * s)));
		int largura = g.getFontMetrics().stringWidth("U");
		g.setColor(new Color(225, 215, 195));
		g.drawString("U", (lado - largura) / 2f, lado * 0.78f);
		// três gotas escorrendo da base do U
		g.setColor(new Color(150, 10, 10));
		int[][] gotas = { { 22, 46, 10 }, { 33, 49, 14 }, { 42, 46, 7 } };
		for (int[] d : gotas) {
			g.fillRect(Math.round(d[0] * s), Math.round(d[1] * s), Math.max(1, Math.round(2.5f * s)), Math.round(d[2] * s));
			g.fillOval(Math.round((d[0] - 1) * s), Math.round((d[1] + d[2] - 1) * s), Math.max(2, Math.round(4.5f * s)),
					Math.max(2, Math.round(4.5f * s)));
		}
		g.dispose();
		return img;
	}

	/** A seta do cursor, 32 x 32: cor de osso, contorno quase preto, ponta vermelha. */
	public static BufferedImage cursor() {
		BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		Polygon seta = new Polygon(new int[] { 2, 2, 8, 12, 15, 11, 18 }, new int[] { 1, 22, 17, 26, 24, 16, 16 }, 7);
		g.setPaint(new GradientPaint(2, 1, new Color(160, 20, 15), 2, 9, new Color(220, 208, 185)));
		g.fillPolygon(seta);
		g.setColor(new Color(15, 8, 8));
		g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.drawPolygon(seta);
		g.dispose();
		return img;
	}

	private static BufferedImage redimensionar(BufferedImage origem, int lado) {
		BufferedImage img = new BufferedImage(lado, lado, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.drawImage(origem, 0, 0, lado, lado, null);
		g.dispose();
		return img;
	}

	/** Os pixels em RGBA, um byte por canal, da linha de cima para a de baixo (o que o GLFW espera). */
	static ByteBuffer rgba(BufferedImage img) {
		int w = img.getWidth(), h = img.getHeight();
		ByteBuffer b = BufferUtils.createByteBuffer(w * h * 4);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int argb = img.getRGB(x, y);
				b.put((byte) (argb >> 16)).put((byte) (argb >> 8)).put((byte) argb).put((byte) (argb >>> 24));
			}
		}
		return b.flip();
	}
}
