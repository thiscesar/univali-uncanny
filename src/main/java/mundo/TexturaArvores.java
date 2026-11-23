package mundo;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.Random;

import util.Caminhos;
import util.TextureLoader;

/**
 * A textura das árvores: uma imagem com as 4 variações lado a lado (um "atlas"). O shader
 * escolhe o pedaço pela variação de cada árvore. Cada pedaço vem de texturas/arvore_N.png
 * se o arquivo existir; senão, é uma silhueta desenhada por código (pinheiro, abeto, árvore
 * seca e copa redonda). Não usa OpenGL.
 */
public final class TexturaArvores {

	public static final int LARGURA_PEDACO = 256;
	public static final int ALTURA_PEDACO = 512;

	private TexturaArvores() {}

	public static BufferedImage atlas() {
		int n = DistribuidorArvores.VARIACOES;
		BufferedImage atlas = new BufferedImage(LARGURA_PEDACO * n, ALTURA_PEDACO, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = atlas.createGraphics();
		for (int i = 0; i < n; i++) {
			String arquivo = "texturas/arvore_" + (i + 1) + ".png";
			BufferedImage pedaco = Caminhos.arquivo(arquivo).exists()
					? TextureLoader.loadImage(Caminhos.caminho(arquivo)) : null;
			if (pedaco == null) {
				pedaco = silhueta(i);
			}
			g.setComposite(java.awt.AlphaComposite.Src);
			g.drawImage(pedaco, i * LARGURA_PEDACO, 0, LARGURA_PEDACO, ALTURA_PEDACO, null);
		}
		g.dispose();
		return atlas;
	}

	/** Silhueta substituta da variação "tipo", desenhada com formas simples e bordas irregulares. */
	public static BufferedImage silhueta(int tipo) {
		int w = LARGURA_PEDACO, h = ALTURA_PEDACO;
		// cores de dia: quem escurece a cena é a iluminação (lua fraca e lanterna), não a textura
		Color folhas = switch (tipo) {
			case 0 -> new Color(58, 92, 56);
			case 1 -> new Color(44, 82, 70);
			case 2 -> new Color(112, 98, 84);
			default -> new Color(74, 98, 52);
		};
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		// fundo transparente com a cor das folhas: a borda filtrada não fica escura
		g.setComposite(java.awt.AlphaComposite.Src);
		g.setColor(new Color(folhas.getRed(), folhas.getGreen(), folhas.getBlue(), 0));
		g.fillRect(0, 0, w, h);
		g.setComposite(java.awt.AlphaComposite.SrcOver);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		Random r = new Random(1000 + tipo);
		Color tronco = new Color(92, 68, 48);

		switch (tipo) {
			case 0 -> { // pinheiro: camadas largas em triângulo
				g.setColor(tronco);
				g.fillRect(w / 2 - 9, h - 140, 18, 140);
				camadas(g, r, folhas, 7, h - 60, 24, 230, 40);
			}
			case 1 -> { // abeto: estreito e alto, muitas camadas
				g.setColor(tronco);
				g.fillRect(w / 2 - 7, h - 110, 14, 110);
				camadas(g, r, folhas, 11, h - 40, 10, 170, 22);
			}
			case 2 -> { // árvore seca: só tronco e galhos
				g.setColor(folhas);
				g.setStroke(new BasicStroke(20, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g.drawLine(w / 2, h, w / 2 + 6, 40);
				for (int i = 0; i < 9; i++) {
					int y = 90 + i * 40 + r.nextInt(20);
					int lado = i % 2 == 0 ? 1 : -1;
					int comprimento = 40 + r.nextInt(70);
					g.setStroke(new BasicStroke(9 - i * 0.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
					int px = w / 2 + lado * comprimento, py = y - 30 - r.nextInt(40);
					g.drawLine(w / 2, y, px, py);
					g.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
					g.drawLine(px, py, px + lado * (15 + r.nextInt(20)), py - 20 - r.nextInt(25));
				}
			}
			default -> { // copa redonda: várias elipses sobrepostas
				g.setColor(tronco);
				g.fillRect(w / 2 - 10, h - 230, 20, 230);
				for (int i = 0; i < 26; i++) {
					double ang = r.nextDouble() * 2 * Math.PI, dist = r.nextDouble() * 70;
					int cx = w / 2 + (int) (Math.cos(ang) * dist), cy = 170 + (int) (Math.sin(ang) * dist * 1.3);
					int raio = 30 + r.nextInt(30);
					g.setColor(variar(folhas, r));
					g.fill(new Ellipse2D.Float(cx - raio, cy - raio, raio * 2, raio * 2));
				}
			}
		}
		g.dispose();
		return img;
	}

	/** Empilha camadas de galhos em forma de triângulo, de baixo (larga) para cima (estreita). */
	private static void camadas(Graphics2D g, Random r, Color cor, int quantidade, int base, int topo,
			int larguraBase, int larguraTopo) {
		int w = LARGURA_PEDACO;
		for (int i = 0; i < quantidade; i++) {
			float t = (float) i / (quantidade - 1);
			int yBase = Math.round(base + (topo + 60 - base) * t);
			int largura = Math.round(larguraBase + (larguraTopo - larguraBase) * t);
			int altura = 110 - Math.round(40 * t);
			Polygon p = new Polygon();
			p.addPoint(w / 2, yBase - altura);
			// borda de baixo em zigue-zague: pontas de galhos
			int dentes = 9;
			for (int d = 0; d <= dentes; d++) {
				float u = (float) d / dentes;
				int x = Math.round(w / 2f + (u - 0.5f) * largura);
				int y = yBase + (d % 2 == 0 ? r.nextInt(10) : -12 - r.nextInt(12));
				p.addPoint(x, y);
			}
			g.setColor(variar(cor, r));
			g.fillPolygon(p);
		}
	}

	private static Color variar(Color c, Random r) {
		float f = 0.85f + r.nextFloat() * 0.35f;
		return new Color(Math.min(255, Math.round(c.getRed() * f)), Math.min(255, Math.round(c.getGreen() * f)),
				Math.min(255, Math.round(c.getBlue() * f)));
	}
}
