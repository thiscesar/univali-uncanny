package shaders;

import static org.lwjgl.opengl.GL20.GL_FRAGMENT_SHADER;
import static org.lwjgl.opengl.GL20.GL_VERTEX_SHADER;

import render.PreprocessadorShader;

/**
 * Shader dos objetos do jogo (sucessor do StaticShader no jogo): mesmos atributos 0 a 3,
 * com a luz e a neblina do comum.glsl.
 */
public class ShaderObjeto extends ShaderProgram {

	public ShaderObjeto() {
		super(PreprocessadorShader.preparar("shaders/objetoVertex.txt", GL_VERTEX_SHADER),
				PreprocessadorShader.preparar("shaders/objetoFragment.txt", GL_FRAGMENT_SHADER));
	}

	@Override
	protected void bindAttributes() {
		super.bindAttribute(0, "position");
		super.bindAttribute(1, "cor");
		super.bindAttribute(2, "normal");
		super.bindAttribute(3, "textuv");
	}
}
