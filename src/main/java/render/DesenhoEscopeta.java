package render;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.GL_TEXTURE1;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL20.*;

import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

import dados.Recursos;
import entidades.Escopeta;
import obj.ObjModelGrupos;
import shaders.ShaderObjeto;
import util.Caminhos;
import util.TextureLoader;
import util.Utils3D;

/**
 * Desenha a escopeta presa à câmera (o "viewmodel"), por cima da cena:
 *  - limpa só o depth buffer: a arma nunca atravessa uma árvore encostada nela;
 *  - projeção própria, com campo de visão menor (55°) e near de 1 cm, para não deformar;
 *  - matriz view = identidade, porque a arma já está em espaço da câmera; a matriz model
 *    junta a posição de descanso (canto inferior direito) com a pose da animação;
 *  - shader de objetos com normal map; luz de preenchimento só para ela (a lanterna sai da
 *    frente da arma e não a ilumina); o clarão do tiro a ilumina por inteiro.
 *
 * O modelo é modelos/escopeta/escopeta.obj (grupos "corpo" e "telha") se existir; senão, a
 * escopeta montada por código (MalhaEscopeta). A pose de descanso pode ser ajustada ao vivo
 * (com o F3: setas, Page Up e Page Down) e é impressa no console para virar constante.
 */
public class DesenhoEscopeta {

	public static final String PASTA = "modelos/escopeta/";
	private static final float CAMPO_DE_VISAO = 55f, PERTO = 0.01f, LONGE = 10f;
	private static final float PREENCHIMENTO = 0.45f;

	/** Onde a arma descansa, em espaço da câmera (metros), o giro em y (radianos) e a escala. */
	public final float[] descanso = { 0.16f, -0.17f, -0.30f };
	public float giroDescanso = 0.02f;
	public float escala = 1f;

	private final ModeloArma modelo;
	private final int texturaCor, texturaNormal;
	private final Particulas chama = new Particulas();
	private final FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
	private Matrix4f matriz = new Matrix4f();

	/** Só na thread principal. */
	public DesenhoEscopeta() {
		boolean temObj = Caminhos.existe(PASTA + "escopeta.obj");
		modelo = temObj ? new ObjModelGrupos(Caminhos.caminho(PASTA + "escopeta.obj")) : new MalhaEscopeta();
		BufferedImage cor = Caminhos.existe(PASTA + "cor.png") ? TextureLoader.loadImage(Caminhos.caminho(PASTA + "cor.png"))
				: MalhaEscopeta.textura();
		texturaCor = Recursos.texturaGerada("escopeta: cor", () -> cor, true, false).id();
		texturaNormal = Caminhos.existe(PASTA + "normal.png") ? Recursos.textura(PASTA + "normal.png")
				: Recursos.texturaGerada("escopeta: normal", () -> GeradorNormalMap.aPartirDaCor(cor, 3f), true, false).id();
	}

	// ---------- pose ----------

	/** Monta a matriz da arma: descanso + deslocamento da animação, depois os giros, depois a escala. */
	public void atualizarPose(Escopeta e) {
		float[] d = e.deslocamento(), g = e.giro();
		Matrix4f m = new Matrix4f();
		m.translate(new Vector3f(descanso[0] + d[0], descanso[1] + d[1], descanso[2] + d[2]));
		m.rotate(giroDescanso + g[1], new Vector3f(0, 1, 0));
		m.rotate(g[0], new Vector3f(1, 0, 0));
		m.rotate(g[2], new Vector3f(0, 0, 1));
		m.scale(new Vector3f(escala, escala, escala));
		matriz = m;
	}

	/** Um ponto do modelo levado ao espaço da câmera pela matriz da arma. */
	private float[] ponto(float[] local) {
		Vector4f v = Matrix4f.transform(matriz, new Vector4f(local[0], local[1], local[2], 1), null);
		return new float[] { v.x, v.y, v.z };
	}

	/** Uma direção do modelo levada ao espaço da câmera (só o giro, sem a posição). */
	private float[] direcao(float[] local) {
		Vector4f v = Matrix4f.transform(matriz, new Vector4f(local[0], local[1], local[2], 0), null);
		float t = (float) Math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z);
		return new float[] { v.x / t, v.y / t, v.z / t };
	}

	/** A boca do cano, em espaço da câmera (onde sai a chama e acontece o clarão). */
	public float[] boca() {
		return ponto(MalhaEscopeta.BOCA_DO_CANO);
	}

	/** A lente da lanterna, em espaço da câmera. */
	public float[] lente() {
		return ponto(MalhaEscopeta.LENTE_LANTERNA);
	}

	/** Para onde o cano aponta, em espaço da câmera (a luz da lanterna vai junto). */
	public float[] direcaoDoCano() {
		return direcao(new float[] { 0, 0, -1 });
	}

	// ---------- efeitos ----------

	/** O tiro: a chama na boca do cano. */
	public void disparar() {
		chama.chama(boca(), direcaoDoCano());
	}

	public void atualizar(float dt) {
		chama.atualizar(dt);
	}

	// ---------- desenho ----------

	/**
	 * Desenha a arma e a chama. Chamar depois da cena, ainda dentro da imagem do modo PS1
	 * (se ligado). proporcao = largura / altura da imagem; alturaImagem em pixels.
	 */
	public void desenhar(Escopeta e, Ambiente ambiente, float proporcao, int alturaImagem) {
		glClear(GL_DEPTH_BUFFER_BIT); // daqui em diante, a arma fica na frente de tudo
		float topo = PERTO * (float) Math.tan(Math.toRadians(CAMPO_DE_VISAO / 2));
		FloatBuffer projecao = BufferUtils.createFloatBuffer(16);
		Utils3D.setFrustum(-topo * proporcao, topo * proporcao, -topo, topo, PERTO, LONGE).storeTranspose(projecao);
		projecao.flip();
		FloatBuffer identidade = BufferUtils.createFloatBuffer(16);
		new Matrix4f().storeTranspose(identidade);
		identidade.flip();

		// luz só da arma; sem o "tremido" do PS1 (perto assim, ela pularia de pixel em pixel)
		float modoAntes = ambiente.modoPs1, preenchimentoAntes = ambiente.intensidadePreenchimento;
		ambiente.modoPs1 = 0;
		ambiente.intensidadePreenchimento = PREENCHIMENTO;

		ShaderObjeto shader = Recursos.shaderObjeto();
		shader.start();
		int p = shader.programID;
		glUniformMatrix4fv(glGetUniformLocation(p, "projection"), false, projecao);
		glUniformMatrix4fv(glGetUniformLocation(p, "view"), false, identidade);
		glUniform1f(glGetUniformLocation(p, "emissividade"), 0f);
		glUniform1i(glGetUniformLocation(p, "tex"), 0);
		glUniform1i(glGetUniformLocation(p, "texNormal"), 1);
		glUniform1i(glGetUniformLocation(p, "usaNormalMap"), 1);
		ambiente.enviar(shader);
		glActiveTexture(GL_TEXTURE0);
		glBindTexture(GL_TEXTURE_2D, texturaCor);
		glActiveTexture(GL_TEXTURE1);
		glBindTexture(GL_TEXTURE_2D, texturaNormal);
		glActiveTexture(GL_TEXTURE0);

		int local = glGetUniformLocation(p, "model");
		glUniformMatrix4fv(local, false, guardar(matriz));
		if (modelo.temGrupo("corpo")) {
			modelo.desenharGrupo("corpo");
		} else if (modelo instanceof ObjModelGrupos obj) {
			obj.draw(); // OBJ sem grupos: tudo de uma vez, sem a telha andando
		}
		if (modelo.temGrupo("telha")) {
			// a telha anda para trás (+z) ao longo do cano
			Matrix4f t = new Matrix4f(matriz);
			t.translate(new Vector3f(0, 0, e.telha() * Escopeta.CURSO_TELHA));
			glUniformMatrix4fv(local, false, guardar(t));
			modelo.desenharGrupo("telha");
		}
		glUniform1i(glGetUniformLocation(p, "usaNormalMap"), 0);
		shader.stop();

		chama.desenhar(projecao, identidade, ambiente, alturaImagem);
		ambiente.modoPs1 = modoAntes;
		ambiente.intensidadePreenchimento = preenchimentoAntes;
	}

	private FloatBuffer guardar(Matrix4f m) {
		buffer.clear();
		m.storeTranspose(buffer);
		buffer.flip();
		return buffer;
	}

	/** Texto para colar no código depois de ajustar a pose ao vivo. */
	public String poseComoTexto() {
		return String.format(java.util.Locale.ROOT, "descanso = { %.3ff, %.3ff, %.3ff }; giroDescanso = %.3ff; escala = %.3ff;",
				descanso[0], descanso[1], descanso[2], giroDescanso, escala);
	}

	public void liberar() {
		modelo.liberar();
		chama.liberar();
	}
}
