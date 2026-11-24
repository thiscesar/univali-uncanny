package shaders;

import static org.lwjgl.opengl.GL20.GL_FRAGMENT_SHADER;
import static org.lwjgl.opengl.GL20.GL_VERTEX_SHADER;

import render.PreprocessadorShader;

/** Shader do feixe de luz das provas: posição (0) e coordenada dentro do feixe (3). */
public class ShaderFeixe extends ShaderProgram {

	public ShaderFeixe() {
		super(PreprocessadorShader.preparar("shaders/feixeVertex.txt", GL_VERTEX_SHADER),
				PreprocessadorShader.preparar("shaders/feixeFragment.txt", GL_FRAGMENT_SHADER));
	}

	@Override
	protected void bindAttributes() {
		super.bindAttribute(0, "position");
		super.bindAttribute(3, "textuv");
	}
}
