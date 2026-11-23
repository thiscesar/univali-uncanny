package render;

import java.awt.image.BufferedImage;

/**
 * Gera um normal map aproximado a partir de uma textura de cor, quando o modelo não traz
 * um normal map próprio.
 *
 * A ideia: tratar o brilho de cada pixel como altura (claro = alto, escuro = fundo, o que
 * funciona razoavelmente para escamas, rebites e riscos) e medir a inclinação com o filtro
 * de Sobel, que olha os 8 vizinhos de cada pixel. A normal é (-inclinação em x,
 * -inclinação em y, 1), normalizada e guardada como cor: x, y, z de -1..1 viram r, g, b
 * de 0..255. Uma superfície lisa fica (128, 128, 255), o azul típico dos normal maps.
 *
 * Não usa OpenGL (dá para testar).
 */
public final class GeradorNormalMap {

	private GeradorNormalMap() {}

	/** @param intensidade quanto o relevo aparece (1 a 4 é um bom começo) */
	public static BufferedImage aPartirDaCor(BufferedImage cor, float intensidade) {
		int w = cor.getWidth(), h = cor.getHeight();
		float[] altura = new float[w * h];
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int c = cor.getRGB(x, y);
				altura[y * w + x] = (0.299f * ((c >> 16) & 0xFF) + 0.587f * ((c >> 8) & 0xFF) + 0.114f * (c & 0xFF)) / 255f;
			}
		}
		BufferedImage normal = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				// Sobel: diferença ponderada entre os vizinhos de um lado e do outro
				float gx = (a(altura, w, h, x + 1, y - 1) + 2 * a(altura, w, h, x + 1, y) + a(altura, w, h, x + 1, y + 1))
						- (a(altura, w, h, x - 1, y - 1) + 2 * a(altura, w, h, x - 1, y) + a(altura, w, h, x - 1, y + 1));
				float gy = (a(altura, w, h, x - 1, y + 1) + 2 * a(altura, w, h, x, y + 1) + a(altura, w, h, x + 1, y + 1))
						- (a(altura, w, h, x - 1, y - 1) + 2 * a(altura, w, h, x, y - 1) + a(altura, w, h, x + 1, y - 1));
				float nx = -gx * intensidade, ny = -gy * intensidade, nz = 1;
				float t = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
				int r = Math.round((nx / t * 0.5f + 0.5f) * 255);
				int g = Math.round((ny / t * 0.5f + 0.5f) * 255);
				int b = Math.round((nz / t * 0.5f + 0.5f) * 255);
				normal.setRGB(x, y, 0xFF000000 | (r << 16) | (g << 8) | b);
			}
		}
		return normal;
	}

	/** Altura no pixel, repetindo a imagem nas bordas (as texturas costumam se repetir). */
	private static float a(float[] altura, int w, int h, int x, int y) {
		return altura[Math.floorMod(y, h) * w + Math.floorMod(x, w)];
	}
}
