package shaders;

import static org.lwjgl.opengl.GL20.GL_FRAGMENT_SHADER;
import static org.lwjgl.opengl.GL20.GL_VERTEX_SHADER;

import render.PreprocessadorShader;

/**
 * Shader dos modelos MD2. Atributos 0 a 3 no padrão do projeto (quadro A e textura);
 * 4 e 5 são a posição e a normal do quadro B.
 */
public class ShaderMd2 extends ShaderProgram {

	public ShaderMd2() {
		super(PreprocessadorShader.preparar("shaders/md2Vertex.txt", GL_VERTEX_SHADER),
				PreprocessadorShader.preparar("shaders/md2Fragment.txt", GL_FRAGMENT_SHADER));
	}

	@Override
	protected void bindAttributes() {
		super.bindAttribute(0, "position");
		super.bindAttribute(1, "cor");
		super.bindAttribute(2, "normal");
		super.bindAttribute(3, "textuv");
		super.bindAttribute(4, "positionB");
		super.bindAttribute(5, "normalB");
	}
}
