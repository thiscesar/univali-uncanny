package shaders;

import util.Caminhos;

/** Shader que amplia a cena pequena do modo PS1 e pode reduzir as cores com pontilhado. */
public class ShaderPs1Tela extends ShaderProgram {

	public ShaderPs1Tela() {
		super(Caminhos.caminho("shaders/ps1TelaVertex.txt"), Caminhos.caminho("shaders/ps1TelaFragment.txt"));
	}

	@Override
	protected void bindAttributes() {
		super.bindAttribute(0, "position");
	}
}
