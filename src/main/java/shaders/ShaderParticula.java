package shaders;

import static org.lwjgl.opengl.GL20.GL_FRAGMENT_SHADER;
import static org.lwjgl.opengl.GL20.GL_VERTEX_SHADER;

import render.PreprocessadorShader;

/** Shader das partículas: posição (0), cor com transparência (1) e tamanho (4). */
public class ShaderParticula extends ShaderProgram {

	public ShaderParticula() {
		super(PreprocessadorShader.preparar("shaders/particulaVertex.txt", GL_VERTEX_SHADER),
				PreprocessadorShader.preparar("shaders/particulaFragment.txt", GL_FRAGMENT_SHADER));
	}

	@Override
	protected void bindAttributes() {
		super.bindAttribute(0, "position");
		super.bindAttribute(1, "cor");
		super.bindAttribute(4, "tamanho");
	}
}
