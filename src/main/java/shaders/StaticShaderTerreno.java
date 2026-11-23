package shaders;

/** Shader exclusivo do terreno: mistura as texturas por altura e inclinação. */
public class StaticShaderTerreno extends ShaderProgram {

	private static final String VERTEX_FILE = "./shaders/terrenoVertex.txt";
	private static final String FRAGMENT_FILE = "./shaders/terrenoFragment.txt";

	public StaticShaderTerreno() {
		super(VERTEX_FILE, FRAGMENT_FILE);
	}

	@Override
	protected void bindAttributes() {
		super.bindAttribute(0, "position");
		super.bindAttribute(1, "cor");
		super.bindAttribute(2, "normal");
		super.bindAttribute(3, "textuv");
	}
}
