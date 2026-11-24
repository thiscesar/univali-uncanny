package fase;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.imageio.ImageIO;

import mundo.ConsultaAltura;
import mundo.Mundo;

/**
 * O mapa da fase visto de cima, para o relatório: o relevo (água em azul, terra do verde ao
 * marrom conforme a altura, com sombra pela inclinação), as trilhas (os caminhos do A*) e os
 * pontos do AG da fase: início (verde), provas (amarelo), caixas (vermelho), Univali (azul) e
 * placas (branco). Não usa OpenGL.
 */
public final class ImagemFase {

	private static final int PIXELS_POR_METRO = 3;

	private ImagemFase() {}

	public static BufferedImage desenhar(ConsultaAltura chao, GeradorFase.Fase fase) {
		int lado = Math.round(chao.tamanho() * PIXELS_POR_METRO);
		BufferedImage img = new BufferedImage(lado, lado, BufferedImage.TYPE_INT_RGB);
		for (int pz = 0; pz < lado; pz++) {
			for (int px = 0; px < lado; px++) {
				float x = px / (float) PIXELS_POR_METRO, z = pz / (float) PIXELS_POR_METRO;
				float h = chao.alturaEm(x, z);
				Color c;
				if (h < 0) {
					float f = Math.min(1, -h / 4);
					c = new Color(Math.round(40 - 25 * f), Math.round(90 - 50 * f), Math.round(150 - 60 * f));
				} else {
					float t = Math.min(1, h / 14);
					float sombra = 1 - 0.6f * chao.inclinacaoEm(x, z) * 3;
					c = new Color(clamp((60 + 90 * t) * sombra), clamp((95 + 30 * t) * sombra), clamp((50 + 30 * t) * sombra));
				}
				img.setRGB(px, pz, c.getRGB());
			}
		}
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setStroke(new BasicStroke(3));
		g.setColor(new Color(230, 190, 120));
		for (List<Ponto> t : fase.trilhas()) {
			for (int i = 1; i < t.size(); i++) {
				g.drawLine(px(t.get(i - 1).x()), px(t.get(i - 1).z()), px(t.get(i).x()), px(t.get(i).z()));
			}
		}
		Layout l = fase.layout();
		for (Placa p : fase.placas()) ponto(g, p.x(), p.z(), 4, Color.WHITE, null);
		for (Ponto c : l.caixas()) ponto(g, c.x(), c.z(), 6, new Color(220, 50, 40), null);
		for (int i = 0; i < l.provas().size(); i++) {
			ponto(g, l.provas().get(i).x(), l.provas().get(i).z(), 9, new Color(250, 220, 60), "M" + (i + 1));
		}
		ponto(g, l.univali().x(), l.univali().z(), 12, new Color(70, 140, 255), "Univali");
		ponto(g, l.inicio().x(), l.inicio().z(), 10, new Color(60, 230, 90), "início");
		g.setColor(Color.WHITE);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
		g.drawString(String.format("fitness da fase %.3f", fase.fitness()), 10, 22);
		g.dispose();
		return img;
	}

	public static void salvar(Mundo mundo, String arquivo) {
		try {
			ImageIO.write(desenhar(mundo.chao, mundo.fase), "png", new File(arquivo));
			System.out.println("[Fase] mapa salvo em " + new File(arquivo).getAbsolutePath());
		} catch (IOException e) {
			System.out.println("[Fase] não foi possível salvar " + arquivo + ": " + e.getMessage());
		}
	}

	private static void ponto(Graphics2D g, float x, float z, int raio, Color cor, String nome) {
		g.setColor(Color.BLACK);
		g.fillOval(px(x) - raio - 2, px(z) - raio - 2, 2 * raio + 4, 2 * raio + 4);
		g.setColor(cor);
		g.fillOval(px(x) - raio, px(z) - raio, 2 * raio, 2 * raio);
		if (nome != null) {
			g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
			g.setColor(Color.BLACK);
			g.drawString(nome, px(x) + raio + 3, px(z) + 6);
			g.setColor(cor);
			g.drawString(nome, px(x) + raio + 2, px(z) + 5);
		}
	}

	private static int px(float metros) {
		return Math.round(metros * PIXELS_POR_METRO);
	}

	private static int clamp(float v) {
		return Math.max(0, Math.min(255, Math.round(v)));
	}
}
