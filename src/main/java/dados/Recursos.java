package dados;

import static org.lwjgl.opengl.GL11.glDeleteTextures;

import java.awt.Font;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import render.Fonte;
import render.Overlay2D;
import render.TextoTextura;
import shaders.ShaderProgram;
import shaders.StaticShaderTerreno;
import util.Caminhos;
import util.TexturaTerreno;
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

	/** Shader do terreno, criado na primeira vez que alguém pede. */
	private static StaticShaderTerreno shaderTerreno;

	/** Desenho 2D (menus, HUD) e o cache de textos, criados na primeira vez. */
	private static Overlay2D overlay;
	private static TextoTextura textos;

	/** Fontes da interface (não usam OpenGL, mas ficam aqui por serem compartilhadas). */
	private static Fonte fonteTitulo;
	private static Fonte fonteTexto;

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

	/**
	 * Carrega (uma vez só) uma textura para ser repetida sobre uma superfície grande,
	 * com mipmaps e filtro anisotrópico, como as do terreno.
	 */
	public static int texturaRepetida(String relativo) {
		String chave = "repetida:" + relativo; // a mesma imagem pode existir nas duas versões
		Integer id = texturas.get(chave);
		if (id == null) {
			id = TexturaTerreno.carregar(Caminhos.caminho(relativo));
			texturas.put(chave, id);
		}
		return id;
	}

	/** O shader do terreno, compartilhado por quem desenhar o terreno. */
	public static StaticShaderTerreno shaderTerreno() {
		if (shaderTerreno == null) {
			shaderTerreno = registrar(new StaticShaderTerreno());
		}
		return shaderTerreno;
	}

	/** O desenhista 2D compartilhado por todas as telas. */
	public static Overlay2D overlay() {
		if (overlay == null) {
			overlay = new Overlay2D();
		}
		return overlay;
	}

	/** O cache de textos transformados em textura. */
	public static TextoTextura textos() {
		if (textos == null) {
			textos = new TextoTextura();
		}
		return textos;
	}

	/** Fonte do título e das telas finais (fontes/titulo.ttf; sem o arquivo, serifada do sistema). */
	public static Fonte fonteTitulo() {
		if (fonteTitulo == null) {
			fonteTitulo = Fonte.carregar("fontes/titulo.ttf", Font.SERIF, Font.BOLD);
		}
		return fonteTitulo;
	}

	/** Fonte do HUD e dos botões (fontes/texto.ttf; sem o arquivo, sem serifa do sistema). */
	public static Fonte fonteTexto() {
		if (fonteTexto == null) {
			fonteTexto = Fonte.carregar("fontes/texto.ttf", Font.SANS_SERIF, Font.PLAIN);
		}
		return fonteTexto;
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
		shaderTerreno = null;
		if (overlay != null) {
			overlay.liberar();
			overlay = null;
		}
		if (textos != null) {
			textos.liberar();
			textos = null;
		}
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
