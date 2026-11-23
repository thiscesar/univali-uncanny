package shaders;

import static org.lwjgl.opengl.GL20.GL_FRAGMENT_SHADER;
import static org.lwjgl.opengl.GL20.GL_VERTEX_SHADER;

import render.PreprocessadorShader;

/** Shader das árvores instanciadas: atributos 0, 2 e 3 do modelo e 4 e 5 de cada árvore. */
public class ShaderArvore extends ShaderProgram {

	public ShaderArvore() {
		super(PreprocessadorShader.preparar("shaders/arvoreVertex.txt", GL_VERTEX_SHADER),
				PreprocessadorShader.preparar("shaders/arvoreFragment.txt", GL_FRAGMENT_SHADER));
	}

	@Override
	protected void bindAttributes() {
		super.bindAttribute(0, "position");
		super.bindAttribute(2, "normal");
		super.bindAttribute(3, "textuv");
		super.bindAttribute(4, "instancia");
		super.bindAttribute(5, "extra");
	}
}
