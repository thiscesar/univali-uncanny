package render;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL12.GL_TEXTURE_MAX_LEVEL;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.BufferUtils;

/**
 * Mipmaps que preservam a cobertura do alfa, para texturas com recorte (folhas, copas).
 *
 * O problema: mipmaps são versões menores da textura, usadas de longe. Cada pixel delas é
 * a média de 4 pixels do nível anterior. Numa copa de árvore com buracos, a média do alfa
 * cai, e com o teste "alfa < 0,5 descarta" a copa vai sumindo ao longe ("derrete").
 *
 * A solução (de Ignacio Castaño): em cada nível, multiplicar o alfa por um fator escolhido
 * para que a fração de pixels acima de 0,5 seja a mesma do nível original. O fator é
 * achado por busca binária. As cores são médias ponderadas pelo alfa, para os pixels
 * transparentes (que podem ser pretos) não escurecerem a borda das folhas.
 */
public final class MipmapsCobertura {

	/** Um nível do mipmap: tamanho e pixels ARGB. */
	public record Nivel(int largura, int altura, int[] argb) {}

	private static final float LIMIAR = 0.5f;

	private MipmapsCobertura() {}

	/** Gera todos os níveis, do original até 1 x 1. Não usa OpenGL. */
	public static List<Nivel> gerar(BufferedImage imagem) {
		int w = imagem.getWidth(), h = imagem.getHeight();
		int[] original = imagem.getRGB(0, 0, w, h, null, 0, w);
		float coberturaAlvo = cobertura(original, 1);

		List<Nivel> niveis = new ArrayList<>();
		niveis.add(new Nivel(w, h, original));
		int[] base = original; // a redução parte sempre do nível sem o ajuste de alfa
		while (w > 1 || h > 1) {
			int nw = Math.max(1, w / 2), nh = Math.max(1, h / 2);
			base = reduzir(base, w, h, nw, nh);
			w = nw;
			h = nh;
			niveis.add(new Nivel(w, h, ajustarAlfa(base, coberturaAlvo)));
		}
		return niveis;
	}

	/** Fração dos pixels que passam no teste de alfa, com o alfa multiplicado por "escala". */
	static float cobertura(int[] argb, float escala) {
		int passa = 0;
		for (int c : argb) {
			if (Math.min(1f, ((c >>> 24) / 255f) * escala) >= LIMIAR) passa++;
		}
		return (float) passa / argb.length;
	}

	/**
	 * Busca binária do fator de alfa que devolve a cobertura original. Como a cobertura anda
	 * em degraus (texturas pequenas têm poucos valores de alfa), no fim fica o lado do degrau
	 * mais perto do alvo.
	 */
	private static int[] ajustarAlfa(int[] argb, float alvo) {
		float baixo = 0, alto = 8;
		for (int i = 0; i < 16; i++) {
			float meio = (baixo + alto) / 2;
			if (cobertura(argb, meio) < alvo) baixo = meio; else alto = meio;
		}
		float escala = Math.abs(cobertura(argb, alto) - alvo) <= Math.abs(cobertura(argb, baixo) - alvo) ? alto : baixo;
		int[] saida = new int[argb.length];
		for (int i = 0; i < argb.length; i++) {
			int a = Math.min(255, Math.round((argb[i] >>> 24) * escala));
			saida[i] = (a << 24) | (argb[i] & 0xFFFFFF);
		}
		return saida;
	}

	/** Cada pixel novo é a média de um bloco 2 x 2 (cores ponderadas pelo alfa). */
	static int[] reduzir(int[] argb, int w, int h, int nw, int nh) {
		int[] saida = new int[nw * nh];
		for (int y = 0; y < nh; y++) {
			for (int x = 0; x < nw; x++) {
				float a = 0, r = 0, g = 0, b = 0, rs = 0, gs = 0, bs = 0;
				int n = 0;
				for (int dy = 0; dy < 2; dy++) {
					for (int dx = 0; dx < 2; dx++) {
						int sx = Math.min(w - 1, x * 2 + dx), sy = Math.min(h - 1, y * 2 + dy);
						int c = argb[sy * w + sx];
						float pa = (c >>> 24) / 255f;
						float pr = (c >> 16) & 0xFF, pg = (c >> 8) & 0xFF, pb = c & 0xFF;
						a += pa;
						r += pr * pa; g += pg * pa; b += pb * pa;
						rs += pr; gs += pg; bs += pb;
						n++;
					}
				}
				int cr, cg, cb;
				if (a > 0) {
					cr = Math.round(r / a); cg = Math.round(g / a); cb = Math.round(b / a);
				} else { // tudo transparente: média simples
					cr = Math.round(rs / n); cg = Math.round(gs / n); cb = Math.round(bs / n);
				}
				int ca = Math.round(a / n * 255);
				saida[y * nw + x] = (ca << 24) | (cr << 16) | (cg << 8) | cb;
			}
		}
		return saida;
	}

	/** Envia a imagem com todos os níveis para a placa de vídeo. Só na thread principal. */
	public static int enviar(BufferedImage imagem) {
		List<Nivel> niveis = gerar(imagem);
		int id = glGenTextures();
		glBindTexture(GL_TEXTURE_2D, id);
		for (int i = 0; i < niveis.size(); i++) {
			Nivel n = niveis.get(i);
			ByteBuffer buffer = BufferUtils.createByteBuffer(n.largura() * n.altura() * 4);
			for (int c : n.argb()) {
				buffer.put((byte) ((c >> 16) & 0xFF)).put((byte) ((c >> 8) & 0xFF)).put((byte) (c & 0xFF))
						.put((byte) ((c >>> 24) & 0xFF));
			}
			buffer.flip();
			glTexImage2D(GL_TEXTURE_2D, i, GL_RGBA8, n.largura(), n.altura(), 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
		}
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, niveis.size() - 1);
		return id;
	}
}
