package dados;

import static org.lwjgl.opengl.GL11.glDeleteTextures;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import shaders.ShaderProgram;
import util.Caminhos;
import util.TextureLoader;

/**
 * Recursos carregados uma única vez e compartilhados pelo jogo inteiro: texturas comuns,
 * shaders e, mais tarde, fontes.
 *
 * A classe Constantes, do projeto original, continua existindo para os recursos antigos.
 * Os recursos novos ficam aqui, porque esta classe também sabe liberá-los ao fechar o jogo.
 *
 * Tudo aqui usa OpenGL, então só pode ser chamado na thread principal.
 */
public final class Recursos {

	/** Texturas já carregadas, pelo caminho relativo: pedir a mesma duas vezes não recarrega. */
	private static final Map<String, Integer> texturas = new HashMap<>();

	/** Shaders criados pelo jogo, para serem apagados no fim. */
	private static final List<ShaderProgram> shaders = new ArrayList<>();

	private Recursos() {}

	/**
	 * Carrega uma textura (uma vez só) a partir de um caminho relativo, por exemplo
	 * "texturas/prova.png". Se o arquivo não existir, devolve uma textura xadrez
	 * magenta e preta, que chama atenção na tela sem travar o jogo.
	 */
	public static int textura(String relativo) {
		Integer id = texturas.get(relativo);
		if (id != null) {
			return id;
		}
		BufferedImage imagem = null;
		if (Caminhos.arquivo(relativo).exists()) {
			imagem = TextureLoader.loadImage(Caminhos.caminho(relativo));
		}
		if (imagem == null) {
			imagem = xadrez();
		}
		id = TextureLoader.loadTexture(imagem);
		texturas.put(relativo, id);
		return id;
	}

	/** Guarda um shader para ser apagado no liberar(). Devolve o próprio shader, por praticidade. */
	public static <T extends ShaderProgram> T registrar(T shader) {
		shaders.add(shader);
		return shader;
	}

	/** Apaga da placa de vídeo tudo o que foi carregado por aqui. Chamado ao fechar o jogo. */
	public static void liberar() {
		for (int id : texturas.values()) {
			glDeleteTextures(id);
		}
		texturas.clear();
		for (ShaderProgram s : shaders) {
			s.cleanUp();
		}
		shaders.clear();
	}

	/** Textura substituta: xadrez magenta e preto de 64 x 64 pixels. */
	private static BufferedImage xadrez() {
		BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 64; y++) {
			for (int x = 0; x < 64; x++) {
				boolean claro = ((x / 8) + (y / 8)) % 2 == 0;
				img.setRGB(x, y, claro ? 0xFFFF00FF : 0xFF000000);
			}
		}
		return img;
	}
}
