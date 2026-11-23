package mundo;

import java.awt.image.BufferedImage;

import dados.Recursos;
import util.Caminhos;
import util.TextureLoader;

/**
 * As texturas do chão da floresta. Enquanto as texturas definitivas não existirem, cada
 * uma é substituída por uma das texturas antigas do terreno, tingida e escurecida por
 * código (a grama vira folhas secas, a areia vira terra...). Basta colocar o arquivo
 * definitivo na pasta texturas/ para ele ser usado no lugar.
 */
public final class TexturasFloresta {

	/** Nomes das texturas no shader, na mesma ordem de carregar(). */
	public static final String[] UNIFORMS = { "texChao", "texGrama", "texTerra", "texPedra", "texAgua" };

	private TexturasFloresta() {}

	/** Carrega (uma vez só) as cinco texturas e devolve os identificadores do OpenGL. */
	public static int[] carregar() {
		return new int[] {
				Recursos.texturaTerreno("texturas/chao_floresta.png",
						() -> tingir(base("texturas/grama.png"), 0.62f, 0.42f, 0.22f)), // folhas secas
				Recursos.texturaTerreno("texturas/grama_escura.png",
						() -> tingir(base("texturas/grama.png"), 0.42f, 0.55f, 0.38f)),
				Recursos.texturaTerreno("texturas/terra.png",
						() -> tingir(base("texturas/areia.png"), 0.50f, 0.38f, 0.28f)),
				Recursos.texturaTerreno("texturas/pedra.png",
						() -> tingir(base("texturas/pedra.png"), 0.70f, 0.70f, 0.72f)),
				Recursos.texturaTerreno("texturas/agua.png",
						() -> base("texturas/agua.png")),
		};
	}

	/** A imagem de um arquivo ou, se ele também faltar, um quadrado cinza. */
	static BufferedImage base(String relativo) {
		BufferedImage img = Caminhos.arquivo(relativo).exists() ? TextureLoader.loadImage(Caminhos.caminho(relativo)) : null;
		if (img == null) {
			img = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
			for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) img.setRGB(x, y, 0xFF808080);
		}
		return img;
	}

	/** Multiplica cada canal de cor pelo fator dado (0 a 1): tinge e escurece. */
	public static BufferedImage tingir(BufferedImage original, float r, float g, float b) {
		int w = original.getWidth(), h = original.getHeight();
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int c = original.getRGB(x, y);
				int nr = Math.round(((c >> 16) & 0xFF) * r);
				int ng = Math.round(((c >> 8) & 0xFF) * g);
				int nb = Math.round((c & 0xFF) * b);
				img.setRGB(x, y, 0xFF000000 | (nr << 16) | (ng << 8) | nb);
			}
		}
		return img;
	}
}
