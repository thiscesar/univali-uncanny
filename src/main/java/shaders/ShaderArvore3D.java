package shaders;

import static org.lwjgl.opengl.GL20.GL_FRAGMENT_SHADER;
import static org.lwjgl.opengl.GL20.GL_VERTEX_SHADER;

import render.PreprocessadorShader;

/** Shader das árvores 3D instanciadas: atributos 0 a 3 do modelo e 4 e 5 de cada árvore. */
public class ShaderArvore3D extends ShaderProgram {

	public ShaderArvore3D() {
		super(PreprocessadorShader.preparar("shaders/arvore3dVertex.txt", GL_VERTEX_SHADER),
				PreprocessadorShader.preparar("shaders/arvore3dFragment.txt", GL_FRAGMENT_SHADER));
	}

	@Override
	protected void bindAttributes() {
		super.bindAttribute(0, "position");
		super.bindAttribute(1, "cor");
		super.bindAttribute(2, "normal");
		super.bindAttribute(3, "textuv");
		super.bindAttribute(4, "instancia");
		super.bindAttribute(5, "extra");
	}
}
