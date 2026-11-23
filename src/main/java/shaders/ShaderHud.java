package shaders;

import util.Caminhos;

/** Shader da interface 2D (menus, HUD, textos). Usado pelo render.Overlay2D. */
public class ShaderHud extends ShaderProgram {

	public ShaderHud() {
		super(Caminhos.caminho("shaders/hudVertex.txt"), Caminhos.caminho("shaders/hudFragment.txt"));
	}

	@Override
	protected void bindAttributes() {
		super.bindAttribute(0, "position");
		super.bindAttribute(3, "textuv");
	}
}
