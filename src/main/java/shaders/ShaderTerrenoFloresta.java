package shaders;

import static org.lwjgl.opengl.GL20.GL_FRAGMENT_SHADER;
import static org.lwjgl.opengl.GL20.GL_VERTEX_SHADER;

import render.PreprocessadorShader;

/**
 * Shader do terreno da floresta, em metros, com a luz e a neblina do comum.glsl.
 * Os arquivos passam pelo PreprocessadorShader (que resolve o #include) antes de chegar
 * ao construtor do ShaderProgram.
 */
public class ShaderTerrenoFloresta extends ShaderProgram {

	public ShaderTerrenoFloresta() {
		super(PreprocessadorShader.preparar("shaders/terrenoFlorestaVertex.txt", GL_VERTEX_SHADER),
				PreprocessadorShader.preparar("shaders/terrenoFlorestaFragment.txt", GL_FRAGMENT_SHADER));
	}

	@Override
	protected void bindAttributes() {
		super.bindAttribute(0, "position");
		super.bindAttribute(1, "cor");
		super.bindAttribute(2, "normal");
		super.bindAttribute(3, "textuv");
	}
}
