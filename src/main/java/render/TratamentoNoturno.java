package render;

import java.awt.image.BufferedImage;

/**
 * Transforma uma foto de dia numa cena noturna, pixel a pixel (processamento de imagem
 * simples, feito uma vez ao carregar):
 *
 *  1. O céu (pixels onde o azul domina, na parte de cima da foto) vira um degradê quase preto.
 *  2. O resto vira tons de cinza frios e escuros, com uma curva que afunda as sombras.
 *  3. Pontos muito claros e sem cor (a placa branca da torre, por exemplo) escurecem menos,
 *     como se estivessem iluminados: o olho vai direto para eles.
 *
 * Não usa OpenGL, então dá para testar e para salvar a imagem e conferir.
 */
public final class TratamentoNoturno {

	private TratamentoNoturno() {}

	public static BufferedImage aplicar(BufferedImage foto) {
		int w = foto.getWidth();
		int h = foto.getHeight();
		BufferedImage noite = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

		for (int y = 0; y < h; y++) {
			float altura = (float) y / h; // 0 no topo, 1 embaixo
			for (int x = 0; x < w; x++) {
				int rgb = foto.getRGB(x, y);
				float r = ((rgb >> 16) & 0xFF) / 255f;
				float g = ((rgb >> 8) & 0xFF) / 255f;
				float b = (rgb & 0xFF) / 255f;

				float luminancia = 0.299f * r + 0.587f * g + 0.114f * b;
				float saturacao = Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));

				// 1. quanto o pixel é "céu": azul dominante, e só na metade de cima
				float azul = b - Math.max(r, g);
				float ceu = suave(0.06f, 0.20f, azul) * (1 - suave(0.55f, 0.80f, altura));

				// 2. cena escura e fria
				float v = (float) Math.pow(luminancia, 1.35) * 0.36f;
				// 3. brilhos claros e neutros resistem ao escuro
				float brilho = suave(0.72f, 0.93f, luminancia) * (1 - suave(0.08f, 0.22f, saturacao));
				v += brilho * 0.38f;
				float nr = v * 0.80f, ng = v * 0.88f, nb = v;

				// céu noturno: quase preto no alto, um azul muito escuro perto do horizonte
				float cr = 0.008f + 0.020f * altura;
				float cg = 0.010f + 0.026f * altura;
				float cb = 0.020f + 0.050f * altura;

				int fr = paraByte(misturar(nr, cr, ceu));
				int fg = paraByte(misturar(ng, cg, ceu));
				int fb = paraByte(misturar(nb, cb, ceu));
				noite.setRGB(x, y, 0xFF000000 | (fr << 16) | (fg << 8) | fb);
			}
		}
		return noite;
	}

	/** smoothstep: 0 antes de inicio, 1 depois de fim, curva suave no meio. */
	static float suave(float inicio, float fim, float x) {
		float t = Math.max(0, Math.min(1, (x - inicio) / (fim - inicio)));
		return t * t * (3 - 2 * t);
	}

	private static float misturar(float a, float b, float t) {
		return a + (b - a) * t;
	}

	private static int paraByte(float v) {
		return Math.max(0, Math.min(255, Math.round(v * 255)));
	}
}
