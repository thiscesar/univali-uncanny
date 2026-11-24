package md2;

import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.GL_TEXTURE1;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL20.*;

import java.util.List;

import dados.Recursos;
import render.GeradorNormalMap;
import shaders.ShaderMd2;
import util.Caminhos;
import util.TextureLoader;

/**
 * Um personagem MD2 completo, como os do Quake 2: o corpo (tris.md2) e, se houver, a arma
 * (weapon.md2), que tem os mesmos quadros e acompanha o corpo. Cada peça tem sua skin e
 * seu normal map (o arquivo normal.png ao lado da skin ou, se não existir, um gerado a
 * partir da própria skin).
 *
 * Arquivos esperados na pasta: tris.md2, skin.png e, opcionais, weapon.md2, weapon.png,
 * normal.png e weapon_normal.png. Os caminhos de skin gravados dentro do MD2 são ignorados.
 */
public class PersonagemMd2 {

	/** Quanto o relevo do normal map gerado aparece. */
	public static final float INTENSIDADE_NORMAL = 2.5f;

	/** O que vem do disco, sem OpenGL (pode ser lido na thread de carregamento). */
	public record Dados(String pasta, MalhaMd2 corpo, MalhaMd2 arma) {}

	private final Dados dados;
	private final Md2Modelo corpo;
	private final Md2Modelo arma;
	private final int skinCorpo, normalCorpo, skinArma, normalArma;

	/** Lê os MD2 da pasta, deixando o corpo com "altura" metros. Devolve null se não houver tris.md2. */
	public static Dados ler(String pasta, float altura) {
		if (!Caminhos.arquivo(pasta + "/tris.md2").exists()) {
			return null;
		}
		ArquivoMd2 arquivoCorpo = LeitorMd2.ler(Caminhos.arquivo(pasta + "/tris.md2"));
		MalhaMd2.Ajuste ajuste = MalhaMd2.ajustePara(arquivoCorpo, altura);
		MalhaMd2 corpo = new MalhaMd2(arquivoCorpo, ajuste);
		MalhaMd2 arma = null;
		if (Caminhos.existe(pasta + "/weapon.md2")) {
			// a arma usa o ajuste do corpo: assim as duas peças continuam encaixadas
			arma = new MalhaMd2(LeitorMd2.ler(Caminhos.arquivo(pasta + "/weapon.md2")), ajuste);
		}
		System.out.println("[MD2] " + pasta + ": " + corpo.vertices / 3 + " triângulos, " + corpo.quadros()
				+ " quadros, animações: " + corpo.animacoes);
		return new Dados(pasta, corpo, arma);
	}

	/** Envia os modelos e as texturas para a placa de vídeo. Só na thread principal. */
	public PersonagemMd2(Dados dados) {
		this.dados = dados;
		corpo = new Md2Modelo(dados.corpo());
		corpo.load();
		skinCorpo = Recursos.textura(dados.pasta() + "/skin.png");
		normalCorpo = normalMap(dados.pasta() + "/normal.png", dados.pasta() + "/skin.png");
		if (dados.arma() != null) {
			arma = new Md2Modelo(dados.arma());
			arma.load();
			skinArma = Recursos.textura(dados.pasta() + "/weapon.png");
			normalArma = normalMap(dados.pasta() + "/weapon_normal.png", dados.pasta() + "/weapon.png");
		} else {
			arma = null;
			skinArma = normalArma = 0;
		}
	}

	/** O normal map do arquivo, se existir; senão, um gerado da skin (filtro de Sobel). */
	private static int normalMap(String arquivo, String skin) {
		if (Caminhos.existe(arquivo)) {
			return Recursos.textura(arquivo);
		}
		return Recursos.texturaGerada("normal gerado de " + skin, () -> {
			var imagem = Caminhos.existe(skin) ? TextureLoader.loadImage(Caminhos.caminho(skin)) : null;
			return imagem == null ? new java.awt.image.BufferedImage(4, 4, java.awt.image.BufferedImage.TYPE_INT_ARGB)
					: GeradorNormalMap.aPartirDaCor(imagem, INTENSIDADE_NORMAL);
		}, false, false).id();
	}

	public List<Animacao> animacoes() {
		return dados.corpo().animacoes;
	}

	/** A malha do corpo (posições de todos os quadros), para achar pontos nela e medir o tamanho. */
	public MalhaMd2 malhaCorpo() {
		return dados.corpo();
	}

	public boolean temArma() {
		return arma != null;
	}

	/**
	 * Desenha a mistura dos quadros A e B. O shader já precisa estar ativo, com as matrizes,
	 * o Ambiente, a emissividade e o dano definidos.
	 */
	public void desenhar(ShaderMd2 shader, int quadroA, int quadroB, float fator, boolean comArma,
			boolean comNormalMap) {
		int p = shader.programID;
		glUniform1f(glGetUniformLocation(p, "fator"), fator);
		glUniform1i(glGetUniformLocation(p, "skin"), 0);
		glUniform1i(glGetUniformLocation(p, "texNormal"), 1);
		glUniform1i(glGetUniformLocation(p, "usaNormalMap"), comNormalMap ? 1 : 0);

		usarTexturas(skinCorpo, normalCorpo);
		corpo.desenhar(quadroA, quadroB);
		if (comArma && arma != null) {
			usarTexturas(skinArma, normalArma);
			arma.desenhar(quadroA, quadroB);
		}
		glActiveTexture(GL_TEXTURE0);
	}

	private static void usarTexturas(int skin, int normal) {
		glActiveTexture(GL_TEXTURE0);
		glBindTexture(GL_TEXTURE_2D, skin);
		glActiveTexture(GL_TEXTURE1);
		glBindTexture(GL_TEXTURE_2D, normal);
	}

	public void liberar() {
		corpo.liberar();
		if (arma != null) arma.liberar();
	}
}
