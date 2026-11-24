package render;

import static org.lwjgl.opengl.GL20.*;

import shaders.ShaderProgram;

/**
 * Os valores de luz e neblina da cena, enviados como uniforms a todo shader que usa o
 * comum.glsl (terreno, objetos, e depois árvores e monstro). Como todos recebem os mesmos
 * valores, tudo na cena tem exatamente a mesma neblina e a mesma iluminação.
 *
 * Posições e direções de luz estão em espaço da câmera (ver comum.glsl).
 */
public class Ambiente {

	/** Céu e neblina da mesma cor: o que some na neblina se funde com o fundo. */
	public final float[] corCeu = { 0.02f, 0.025f, 0.03f };
	/** Com 0,05, a neblina cobre ~86% a 40 m e praticamente tudo a 45 m (o far é de 60 m). */
	public float densidadeNeblina = 0.05f;

	/** Luz da lua: azulada e bem fraca, só para se ver o vulto do relevo próximo. */
	public final float[] corAmbiente = { 0.35f, 0.45f, 0.75f };
	public float intensidadeAmbiente = 0.18f;

	public final float[] corLanterna = { 1.0f, 0.92f, 0.78f };
	public float intensidadeLanterna = 0;       // 0 = desligada
	public final float[] posLanterna = new float[3];
	public final float[] dirLanterna = { 0, 0, -1 };
	public float anguloInterno = 12;            // graus: luz cheia
	public float anguloExterno = 24;            // graus: borda do cone
	public float alcanceLanterna = 30;          // metros

	public float intensidadeClarao = 0;
	public final float[] posClarao = new float[3];
	public float alcanceClarao = 12;

	public float intensidadePreenchimento = 0;

	/** Modo PS1 (vértices tremendo e textura sem correção de perspectiva): 1 ligado, 0 desligado. */
	public float modoPs1 = 0;
	/** Resolução em que a cena é desenhada no modo PS1 (a grade em que os vértices caem). */
	public final float[] resolucaoPs1 = { 320, 240 };

	/** Envia todos os valores ao shader (que precisa estar ativo, com start()). */
	public void enviar(ShaderProgram shader) {
		int p = shader.programID;
		glUniform3f(glGetUniformLocation(p, "corNeblina"), corCeu[0], corCeu[1], corCeu[2]);
		glUniform1f(glGetUniformLocation(p, "densidadeNeblina"), densidadeNeblina);
		glUniform3f(glGetUniformLocation(p, "luzAmbiente"), corAmbiente[0] * intensidadeAmbiente,
				corAmbiente[1] * intensidadeAmbiente, corAmbiente[2] * intensidadeAmbiente);

		glUniform1f(glGetUniformLocation(p, "intensidadeLanterna"), intensidadeLanterna);
		glUniform3f(glGetUniformLocation(p, "corLanterna"), corLanterna[0], corLanterna[1], corLanterna[2]);
		glUniform3f(glGetUniformLocation(p, "posLanterna"), posLanterna[0], posLanterna[1], posLanterna[2]);
		glUniform3f(glGetUniformLocation(p, "dirLanterna"), dirLanterna[0], dirLanterna[1], dirLanterna[2]);
		glUniform1f(glGetUniformLocation(p, "cosInternoLanterna"), (float) Math.cos(Math.toRadians(anguloInterno)));
		glUniform1f(glGetUniformLocation(p, "cosExternoLanterna"), (float) Math.cos(Math.toRadians(anguloExterno)));
		glUniform1f(glGetUniformLocation(p, "alcanceLanterna"), alcanceLanterna);

		glUniform1f(glGetUniformLocation(p, "intensidadeClarao"), intensidadeClarao);
		glUniform3f(glGetUniformLocation(p, "posClarao"), posClarao[0], posClarao[1], posClarao[2]);
		glUniform1f(glGetUniformLocation(p, "alcanceClarao"), alcanceClarao);

		glUniform1f(glGetUniformLocation(p, "intensidadePreenchimento"), intensidadePreenchimento);

		glUniform1f(glGetUniformLocation(p, "modoPs1"), modoPs1);
		glUniform2f(glGetUniformLocation(p, "resolucaoPs1"), resolucaoPs1[0], resolucaoPs1[1]);
	}
}
