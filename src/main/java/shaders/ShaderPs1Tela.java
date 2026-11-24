package shaders;

import util.Caminhos;

/** Shader que leva a cena à tela: amplia a imagem do modo PS1 e aplica vinheta, granulação e aberração. */
public class ShaderPs1Tela extends ShaderProgram {

	public ShaderPs1Tela() {
		super(Caminhos.caminho("shaders/ps1TelaVertex.txt"), Caminhos.caminho("shaders/ps1TelaFragment.txt"));
	}

	@Override
	protected void bindAttributes() {
		super.bindAttribute(0, "position");
	}
}
