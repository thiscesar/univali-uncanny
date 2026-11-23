package util;

import static org.lwjgl.opengl.GL11.*;

import java.awt.image.BufferedImage;

import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL30;

/**
 * Carrega uma textura para ser repetida sobre o terreno.
 *
 * Diferenças para o TextureLoader original:
 *  - GL_REPEAT: a textura se repete em vez de esticar;
 *  - mipmaps: versões menores da textura para quando ela está longe,
 *    o que elimina o "chiado" (moiré);
 *  - filtro anisotrópico: mantém a textura nítida quando vista de lado (chão ao longe).
 */
public final class TexturaTerreno {
	private TexturaTerreno() {}

	public static int carregar(String arquivo) {
		BufferedImage imagem = TextureLoader.loadImage(arquivo);
		if (imagem == null) {
			throw new RuntimeException("Textura não encontrada: " + arquivo);
		}
		int id = TextureLoader.loadTexture(imagem); // envia os pixels e deixa a textura ativa

		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
		GL30.glGenerateMipmap(GL_TEXTURE_2D);

		if (GL.getCapabilities().GL_EXT_texture_filter_anisotropic) {
			float maximo = glGetFloat(EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT);
			glTexParameterf(GL_TEXTURE_2D, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, Math.min(8f, maximo));
		}
		return id;
	}
}
