package render;

import static org.lwjgl.opengl.GL11.glDeleteTextures;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

import util.TextureLoader;

/**
 * Transforma textos em texturas.
 *
 * O OpenGL não sabe desenhar letras. Então o texto é desenhado pelo Java2D (Graphics2D)
 * numa imagem com fundo transparente, e a imagem vira textura pelo TextureLoader do
 * projeto original. Como gerar uma textura é caro, cada texto fica guardado num cache:
 * pedir o mesmo texto, fonte, tamanho e cor de novo devolve a mesma textura.
 *
 * O cache tem limite. Quando passa dele, a textura usada há mais tempo é apagada
 * (importante para textos que mudam sempre, como o cronômetro).
 */
public class TextoTextura {

	/** Uma textura de texto e o seu tamanho em pixels (para alinhar). */
	public record Imagem(int textura, int largura, int altura) {}

	private record Chave(String texto, String fonte, int tamanho, int rgb, boolean contorno, boolean sombra) {}

	public static final int LIMITE_CACHE = 200;

	private static final Color COR_CONTORNO = Color.BLACK;
	private static final Color COR_SOMBRA = new Color(0, 0, 0, 170);

	/** LinkedHashMap em "ordem de acesso": o primeiro da lista é o usado há mais tempo. */
	private final Map<Chave, Imagem> cache = new LinkedHashMap<>(64, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Chave, Imagem> maisAntiga) {
			if (size() > LIMITE_CACHE) {
				glDeleteTextures(maisAntiga.getValue().textura());
				return true;
			}
			return false;
		}
	};

	/**
	 * A textura do texto, criada na primeira vez. A cor é gravada na textura sem
	 * transparência; a transparência (fade) é aplicada na hora de desenhar.
	 * Devolve null para texto vazio.
	 */
	public Imagem obter(String texto, Fonte fonte, int tamanho, Color cor, boolean contorno, boolean sombra) {
		if (texto == null || texto.isEmpty()) {
			return null;
		}
		Color opaca = new Color(cor.getRed(), cor.getGreen(), cor.getBlue());
		Chave chave = new Chave(texto, fonte.nome(), tamanho, opaca.getRGB(), contorno, sombra);
		Imagem imagem = cache.get(chave);
		if (imagem == null) {
			BufferedImage img = gerarImagem(texto, fonte.emTamanho(tamanho), opaca, contorno, sombra);
			imagem = new Imagem(TextureLoader.loadTexture(img), img.getWidth(), img.getHeight());
			cache.put(chave, imagem);
		}
		return imagem;
	}

	public int quantidadeNoCache() {
		return cache.size();
	}

	/** Apaga todas as texturas de texto. Chamado ao fechar o jogo. */
	public void liberar() {
		for (Imagem i : cache.values()) {
			glDeleteTextures(i.textura());
		}
		cache.clear();
	}

	/**
	 * Desenha o texto numa imagem ARGB com fundo transparente. Não usa OpenGL (dá para testar).
	 *
	 * Detalhe importante: o fundo transparente é pintado com a cor do texto (ou do contorno)
	 * e alfa zero, em vez de preto transparente. Assim, quando a placa de vídeo mistura um
	 * pixel da borda da letra com o fundo, não aparece uma franja escura em volta.
	 */
	public static BufferedImage gerarImagem(String texto, Font fonte, Color cor, boolean contorno, boolean sombra) {
		FontRenderContext frc = new FontRenderContext(null, true, true);
		TextLayout layout = new TextLayout(texto, fonte, frc);

		float espessura = contorno ? Math.max(1f, fonte.getSize2D() / 14f) : 0f;
		int deslocamentoSombra = sombra ? Math.max(1, Math.round(fonte.getSize2D() / 16f)) : 0;
		int margem = (int) Math.ceil(espessura) + 2;

		int largura = (int) Math.ceil(layout.getAdvance()) + 2 * margem + deslocamentoSombra;
		int altura = (int) Math.ceil(layout.getAscent() + layout.getDescent()) + 2 * margem + deslocamentoSombra;

		BufferedImage img = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();

		Color fundo = contorno ? COR_CONTORNO : cor;
		g.setComposite(AlphaComposite.Src);
		g.setColor(new Color(fundo.getRed(), fundo.getGreen(), fundo.getBlue(), 0));
		g.fillRect(0, 0, largura, altura);
		g.setComposite(AlphaComposite.SrcOver);

		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

		// contorno das letras, posicionado na linha de base
		Shape forma = layout.getOutline(AffineTransform.getTranslateInstance(margem, margem + layout.getAscent()));
		BasicStroke traco = new BasicStroke(2 * espessura, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

		if (sombra) {
			Shape formaSombra = AffineTransform.getTranslateInstance(deslocamentoSombra, deslocamentoSombra)
					.createTransformedShape(forma);
			g.setColor(COR_SOMBRA);
			g.fill(formaSombra);
			if (contorno) {
				g.setStroke(traco);
				g.draw(formaSombra);
			}
		}
		if (contorno) {
			g.setColor(COR_CONTORNO);
			g.setStroke(traco);
			g.draw(forma);
		}
		g.setColor(cor);
		g.fill(forma);
		g.dispose();
		return img;
	}
}
