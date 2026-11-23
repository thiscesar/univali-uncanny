package shaders;

/**
 * Shader usado nos projéteis (billboards).
 *
 * Esta classe e os arquivos shaders/vertexShader2.txt e shaders/fragmentShader2.txt
 * eram usados pelo Main3D, mas não estavam no repositório do professor.
 * Foram reconstruídos aqui com os mesmos atributos e uniforms que o código usa
 * (model, view, projection, tex, anim, liga_anim).
 */
public class StaticShader2 extends ShaderProgram {

	private static final String VERTEX_FILE = "./shaders/vertexShader2.txt";
	private static final String FRAGMENT_FILE = "./shaders/fragmentShader2.txt";

	public StaticShader2() {
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
