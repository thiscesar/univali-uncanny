package shaders;

import static org.lwjgl.opengl.GL20.GL_FRAGMENT_SHADER;
import static org.lwjgl.opengl.GL20.GL_VERTEX_SHADER;

import render.PreprocessadorShader;

/** Shader dos pontos que brilham sozinhos (olhos do monstro): só a posição, no atributo 0. */
public class ShaderBrilho extends ShaderProgram {

	public ShaderBrilho() {
		super(PreprocessadorShader.preparar("shaders/brilhoVertex.txt", GL_VERTEX_SHADER),
				PreprocessadorShader.preparar("shaders/brilhoFragment.txt", GL_FRAGMENT_SHADER));
	}

	@Override
	protected void bindAttributes() {
		super.bindAttribute(0, "position");
	}
}
