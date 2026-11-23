package shaders;

import util.Caminhos;

/**
 * Shader do terreno da floresta, em metros. Usa o mesmo vertex shader do terreno da ilha
 * (terrenoVertex.txt) e um fragment shader próprio com os materiais da floresta.
 */
public class ShaderTerrenoFloresta extends ShaderProgram {

	public ShaderTerrenoFloresta() {
		super(Caminhos.caminho("shaders/terrenoVertex.txt"), Caminhos.caminho("shaders/terrenoFlorestaFragment.txt"));
	}

	@Override
	protected void bindAttributes() {
		super.bindAttribute(0, "position");
		super.bindAttribute(1, "cor");
		super.bindAttribute(2, "normal");
		super.bindAttribute(3, "textuv");
	}
}
