package dados;

import static org.lwjgl.opengl.GL11.*;

import java.awt.Font;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import mundo.Arvores3D;
import mundo.TexturaArvores;
import render.Fonte;
import render.MipmapsCobertura;
import render.Overlay2D;
import render.TextoTextura;
import shaders.ShaderProgram;
import shaders.ShaderArvore;
import shaders.ShaderArvore3D;
import shaders.ShaderBrilho;
import shaders.ShaderParticula;
import shaders.ShaderMd2;
import shaders.ShaderPs1Tela;
import shaders.ShaderObjeto;
import shaders.ShaderTerrenoFloresta;
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

	/** Uma textura e o tamanho da imagem que a originou, em pixels. */
	public record Textura(int id, int largura, int altura) {}

	/** Texturas geradas por código (ou tratadas), pela chave escolhida por quem pediu. */
	private static final Map<String, Textura> geradas = new HashMap<>();

	/** Texturas já carregadas, pelo caminho relativo: pedir a mesma duas vezes não recarrega. */
	private static final Map<String, Integer> texturas = new HashMap<>();

	/** Shaders criados pelo jogo, para serem apagados no fim. */
	private static final List<ShaderProgram> shaders = new ArrayList<>();

	/** Shader do terreno, criado na primeira vez que alguém pede. */
	private static StaticShaderTerreno shaderTerreno;
	private static ShaderTerrenoFloresta shaderFloresta;
	private static ShaderObjeto shaderObjeto;
	private static ShaderArvore shaderArvore;
	private static ShaderArvore3D shaderArvore3D;
	private static ShaderBrilho shaderBrilho;
	private static ShaderParticula shaderParticula;
	private static ShaderMd2 shaderMd2;
	private static ShaderPs1Tela shaderPs1Tela;

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

	/**
	 * Uma textura criada a partir de uma imagem gerada por código (ruído, degradê, foto tratada...).
	 * O gerador só roda na primeira vez; depois a mesma textura é devolvida pela chave.
	 *
	 * @param repetir true para a textura se repetir (GL_REPEAT), como um ladrilho
	 * @param nitida  true para não suavizar os pixels ao ampliar (GL_NEAREST), bom para granulação
	 */
	public static Textura texturaGerada(String chave, Supplier<BufferedImage> gerador, boolean repetir, boolean nitida) {
		Textura t = geradas.get(chave);
		if (t == null) {
			BufferedImage img = gerador.get();
			int id = TextureLoader.loadTexture(img); // deixa a textura ativa para os ajustes abaixo
			if (repetir) {
				glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
				glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
			}
			if (nitida) {
				glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
				glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
			}
			t = new Textura(id, img.getWidth(), img.getHeight());
			geradas.put(chave, t);
		}
		return t;
	}

	/**
	 * Textura de terreno (repetida, com mipmaps): o arquivo, se existir; senão, a imagem
	 * substituta gerada por código. Carregada uma vez só.
	 */
	public static int texturaTerreno(String relativo, Supplier<BufferedImage> substituta) {
		String chave = "terreno:" + relativo;
		Integer id = texturas.get(chave);
		if (id == null) {
			id = Caminhos.arquivo(relativo).exists()
					? TexturaTerreno.carregar(Caminhos.caminho(relativo))
					: TexturaTerreno.criar(substituta.get());
			texturas.put(chave, id);
		}
		return id;
	}

	/** O shader do terreno da floresta. */
	public static ShaderTerrenoFloresta shaderFloresta() {
		if (shaderFloresta == null) {
			shaderFloresta = registrar(new ShaderTerrenoFloresta());
		}
		return shaderFloresta;
	}

	/** O shader que amplia a cena do modo PS1. */
	public static ShaderPs1Tela shaderPs1Tela() {
		if (shaderPs1Tela == null) {
			shaderPs1Tela = registrar(new ShaderPs1Tela());
		}
		return shaderPs1Tela;
	}

	/**
	 * Troca o filtro das texturas da cena 3D (terreno, árvores, skins). No modo PS1, sem
	 * suavização: cada pixel da textura aparece como um bloco nítido, como no console.
	 * As texturas da interface (menu, textos) não mudam.
	 */
	public static void aplicarModoPs1(boolean ps1) {
		for (Map.Entry<String, Integer> e : texturas.entrySet()) {
			String chave = e.getKey();
			boolean comMipmaps = chave.startsWith("terreno:") || chave.startsWith("repetida:") || chave.startsWith("arvores");
			glBindTexture(GL_TEXTURE_2D, e.getValue());
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, ps1 ? GL_NEAREST : GL_LINEAR);
			int minificacao = comMipmaps ? (ps1 ? GL_NEAREST_MIPMAP_NEAREST : GL_LINEAR_MIPMAP_LINEAR)
					: (ps1 ? GL_NEAREST : GL_LINEAR);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, minificacao);
			if (comMipmaps && org.lwjgl.opengl.GL.getCapabilities().GL_EXT_texture_filter_anisotropic) {
				float maximo = glGetFloat(org.lwjgl.opengl.EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT);
				glTexParameterf(GL_TEXTURE_2D, org.lwjgl.opengl.EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT,
						ps1 ? 1f : Math.min(8f, maximo));
			}
		}
		glBindTexture(GL_TEXTURE_2D, 0);
	}

	/** O shader dos modelos MD2 animados. */
	public static ShaderMd2 shaderMd2() {
		if (shaderMd2 == null) {
			shaderMd2 = registrar(new ShaderMd2());
		}
		return shaderMd2;
	}

	/** O shader das árvores. */
	public static ShaderArvore shaderArvore() {
		if (shaderArvore == null) {
			shaderArvore = registrar(new ShaderArvore());
		}
		return shaderArvore;
	}

	/** O shader dos pontos que brilham (olhos do monstro). */
	public static ShaderBrilho shaderBrilho() {
		if (shaderBrilho == null) {
			shaderBrilho = registrar(new ShaderBrilho());
		}
		return shaderBrilho;
	}

	/** O shader das partículas (faíscas, poeira, fumaça, sangue). */
	public static ShaderParticula shaderParticula() {
		if (shaderParticula == null) {
			shaderParticula = registrar(new ShaderParticula());
		}
		return shaderParticula;
	}

	/** O shader das árvores 3D. */
	public static ShaderArvore3D shaderArvore3D() {
		if (shaderArvore3D == null) {
			shaderArvore3D = registrar(new ShaderArvore3D());
		}
		return shaderArvore3D;
	}

	/**
	 * A textura de um material das árvores 3D (modelos/arvores/material.png), repetida ao longo
	 * dos troncos e com mipmaps que preservam o recorte das folhas. Sem o arquivo, o xadrez.
	 */
	public static int texturaArvore3D(String material) {
		String chave = "arvores3d:" + material;
		Integer id = texturas.get(chave);
		if (id == null) {
			String relativo = Arvores3D.PASTA + material + ".png";
			BufferedImage imagem = Caminhos.arquivo(relativo).exists()
					? TextureLoader.loadImage(Caminhos.caminho(relativo)) : null;
			id = MipmapsCobertura.enviar(imagem != null ? imagem : xadrez());
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
			texturas.put(chave, id);
		}
		return id;
	}

	/** A textura das árvores (as variações lado a lado), com mipmaps que preservam o recorte. */
	public static int texturaArvores() {
		Integer id = texturas.get("arvores");
		if (id == null) {
			id = MipmapsCobertura.enviar(TexturaArvores.atlas());
			texturas.put("arvores", id);
		}
		return id;
	}

	/** O shader dos objetos do jogo. */
	public static ShaderObjeto shaderObjeto() {
		if (shaderObjeto == null) {
			shaderObjeto = registrar(new ShaderObjeto());
		}
		return shaderObjeto;
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
		for (Textura t : geradas.values()) {
			glDeleteTextures(t.id());
		}
		geradas.clear();
		for (ShaderProgram s : shaders) {
			s.cleanUp();
		}
		shaders.clear();
		shaderTerreno = null;
		shaderFloresta = null;
		shaderObjeto = null;
		shaderArvore = null;
		shaderArvore3D = null;
		shaderBrilho = null;
		shaderParticula = null;
		shaderMd2 = null;
		shaderPs1Tela = null;
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
