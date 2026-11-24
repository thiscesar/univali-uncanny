package render;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Sangue escorrendo das letras: acha, na imagem do texto, as colunas onde a letra termina
 * (a borda de baixo de cada traço) e sorteia em algumas delas um escorrido, com largura,
 * comprimento e atraso próprios. O desenho é só um retângulo que cresce para baixo, com uma
 * gota um pouco mais larga na ponta. Não usa OpenGL (dá para testar).
 */
public final class Escorridos {

	/** Um escorrido, em pixels da imagem do texto: começa em (x, y) e desce até "comprimento". */
	public record Escorrido(float x, float y, float largura, float comprimento, float atraso, float duracao) {

		/** Quanto já desceu no instante t (segundos desde o começo): rápido no início, devagar no fim. */
		public float descido(float t) {
			float p = Math.max(0, Math.min(1, (t - atraso) / duracao));
			return comprimento * (1 - (1 - p) * (1 - p) * (1 - p));
		}
	}

	private Escorridos() {}

	/**
	 * Sorteia até "quantidade" escorridos na imagem do texto (pixels com alfa acima da metade
	 * são letra). Dois escorridos nunca ficam a menos de 2 larguras um do outro.
	 */
	public static List<Escorrido> gerar(BufferedImage texto, int quantidade, Random random) {
		int w = texto.getWidth(), h = texto.getHeight();
		// para cada coluna, a última linha com letra, se a de baixo for vazia (o fim de um traço)
		List<int[]> bordas = new ArrayList<>();
		for (int x = 0; x < w; x++) {
			for (int y = h - 2; y >= 0; y--) {
				if (letra(texto, x, y) && !letra(texto, x, y + 1)) {
					bordas.add(new int[] { x, y });
					break; // só a borda mais baixa da coluna
				}
			}
		}
		List<Escorrido> lista = new ArrayList<>();
		if (bordas.isEmpty()) return lista;

		float base = h / 30f; // largura típica, proporcional ao tamanho do texto
		for (int tentativa = 0; tentativa < quantidade * 20 && lista.size() < quantidade; tentativa++) {
			int[] b = bordas.get(random.nextInt(bordas.size()));
			float largura = base * (0.6f + random.nextFloat() * 0.9f);
			boolean longe = true;
			for (Escorrido e : lista) {
				if (Math.abs(e.x() - b[0]) < 2 * Math.max(e.largura(), largura)) {
					longe = false;
					break;
				}
			}
			if (!longe) continue;
			float comprimento = h * (0.12f + random.nextFloat() * random.nextFloat() * 0.65f); // muitos curtos, poucos longos
			float atraso = random.nextFloat() * 1.2f;
			float duracao = 1.5f + random.nextFloat() * 3.5f;
			// começa um pouco para dentro da letra, para não ficar uma emenda visível
			lista.add(new Escorrido(b[0] - largura / 2, b[1] - largura, largura, comprimento + largura, atraso, duracao));
		}
		return lista;
	}

	private static boolean letra(BufferedImage img, int x, int y) {
		return (img.getRGB(x, y) >>> 24) > 128;
	}
}
