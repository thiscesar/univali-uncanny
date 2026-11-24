package render;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL14.GL_DEPTH_COMPONENT24;
import static org.lwjgl.opengl.GL14.GL_TEXTURE_COMPARE_FUNC;
import static org.lwjgl.opengl.GL14.GL_TEXTURE_COMPARE_MODE;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL33.GL_TIME_ELAPSED;

import java.nio.FloatBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector4f;

import util.Utils3D;

/**
 * A sombra da lanterna, por shadow mapping:
 *
 *  1. A cada quadro, a cena é desenhada do ponto de vista da lanterna (uma "câmera" na ponta da
 *     arma, olhando para onde a luz aponta, com a abertura do cone) num framebuffer que só tem
 *     profundidade: o mapa de sombra guarda, para cada direção, a distância até a primeira coisa
 *     que a luz encontra.
 *  2. Na cena normal, o comum.glsl leva cada ponto para o espaço da lanterna (vistaParaLuz) e
 *     compara a distância dele com a do mapa: se havia algo mais perto da luz, ele está na sombra.
 *
 * A textura usa a comparação da própria placa (GL_COMPARE_REF_TO_TEXTURE) com filtro linear: no
 * shader, um sampler2DShadow devolve direto de 0 a 1 o quanto o ponto está iluminado.
 *
 * As matrizes seguem o lwjgl_util (vetor-coluna: P * V * M * v); o shader multiplica vetor-linha
 * pela transposta, como em todo o projeto (storeTranspose).
 */
public class SombraLanterna {

	/** Margem em volta do cone externo, em graus (o balanço da luz não corta a sombra na borda). */
	public static final float MARGEM_CONE = 4f;
	public static final float PERTO = 0.3f;

	private int fbo = 0, textura = 0, lado = 0;
	private final int[] consultas = new int[2];
	private int quadro = 0;
	private float milissegundos = 0;

	/** Projeção e visão da lanterna, prontas para os desenhos da passada de profundidade. */
	public final FloatBuffer projecaoLuz = BufferUtils.createFloatBuffer(16);
	public final FloatBuffer visaoLuz = BufferUtils.createFloatBuffer(16);

	// ---------- matemática (sem OpenGL: testada) ----------

	/**
	 * A visão de uma câmera em "olho", olhando na direção "dir" (como o gluLookAt): leva o mundo
	 * para um espaço em que a câmera fica na origem olhando para -z.
	 */
	public static Matrix4f olharPara(float[] olho, float[] dir) {
		float[] f = normalizar(dir);
		float[] cima = Math.abs(f[1]) > 0.99f ? new float[] { 1, 0, 0 } : new float[] { 0, 1, 0 }; // olhando reto para cima ou para baixo
		float[] s = normalizar(vetorial(f, cima));
		float[] u = vetorial(s, f);
		Matrix4f m = new Matrix4f(); // no lwjgl_util, mXY é a coluna X, linha Y
		m.m00 = s[0]; m.m10 = s[1]; m.m20 = s[2]; m.m30 = -escalar(s, olho);
		m.m01 = u[0]; m.m11 = u[1]; m.m21 = u[2]; m.m31 = -escalar(u, olho);
		m.m02 = -f[0]; m.m12 = -f[1]; m.m22 = -f[2]; m.m32 = escalar(f, olho);
		m.m03 = 0; m.m13 = 0; m.m23 = 0; m.m33 = 1;
		return m;
	}

	/** Projeção da lanterna: quadrada, com abertura de 2 x (cone externo + margem), até o alcance. */
	public static Matrix4f projecao(float anguloExterno, float alcance) {
		float topo = PERTO * (float) Math.tan(Math.toRadians(anguloExterno + MARGEM_CONE));
		return Utils3D.setFrustum(-topo, topo, -topo, topo, PERTO, alcance);
	}

	/**
	 * As matrizes do quadro, a partir da visão da câmera e da lanterna em espaço da câmera.
	 * Devolve {visão da luz, projeção da luz, vistaParaLuz}: a última leva um ponto do espaço da
	 * câmera direto ao recorte da lanterna (projeção da luz x visão da luz x inversa da visão).
	 */
	public static Matrix4f[] matrizes(Matrix4f visaoCamera, float[] posVista, float[] dirVista, float anguloExterno,
			float alcance) {
		Matrix4f vistaParaMundo = Matrix4f.invert(visaoCamera, null);
		Vector4f p = Matrix4f.transform(vistaParaMundo, new Vector4f(posVista[0], posVista[1], posVista[2], 1), null);
		Vector4f d = Matrix4f.transform(vistaParaMundo, new Vector4f(dirVista[0], dirVista[1], dirVista[2], 0), null);
		Matrix4f visaoLuz = olharPara(new float[] { p.x, p.y, p.z }, new float[] { d.x, d.y, d.z });
		Matrix4f projecaoLuz = projecao(anguloExterno, alcance);
		Matrix4f mundoParaLuz = Matrix4f.mul(projecaoLuz, visaoLuz, null);
		Matrix4f vistaParaLuz = Matrix4f.mul(mundoParaLuz, vistaParaMundo, null);
		return new Matrix4f[] { visaoLuz, projecaoLuz, vistaParaLuz };
	}

	// ---------- OpenGL (só na thread principal) ----------

	/** Cria (ou recria, se o lado mudou) o framebuffer só de profundidade. */
	public void preparar(int ladoMapa) {
		if (fbo != 0 && ladoMapa == lado) return;
		liberar();
		lado = ladoMapa;
		textura = glGenTextures();
		glBindTexture(GL_TEXTURE_2D, textura);
		glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT24, lado, lado, 0, GL_DEPTH_COMPONENT, GL_FLOAT, 0L);
		// filtro linear + comparação: a placa compara 4 texels e mistura (bordas menos serrilhadas)
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_MODE, GL_COMPARE_REF_TO_TEXTURE);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_FUNC, GL_LEQUAL);
		glBindTexture(GL_TEXTURE_2D, 0);

		fbo = glGenFramebuffers();
		glBindFramebuffer(GL_FRAMEBUFFER, fbo);
		glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, textura, 0);
		glDrawBuffer(GL_NONE); // sem cor: só a profundidade é escrita
		glReadBuffer(GL_NONE);
		if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
			throw new IllegalStateException("framebuffer da sombra incompleto");
		}
		glBindFramebuffer(GL_FRAMEBUFFER, 0);
		consultas[0] = glGenQueries();
		consultas[1] = glGenQueries();
		quadro = 0; // consultas novas: ainda não há resultado anterior para ler
	}

	/** Guarda as matrizes do quadro e manda a vistaParaLuz para o ambiente. */
	public void calcular(Matrix4f visaoCamera, Lanterna lanterna, Ambiente ambiente) {
		Matrix4f[] m = matrizes(visaoCamera, lanterna.posicaoVista(), lanterna.direcaoVista(), ambiente.anguloExterno,
				ambiente.alcanceLanterna);
		guardar(m[0], visaoLuz);
		guardar(m[1], projecaoLuz);
		guardar(m[2], ambiente.vistaParaLuz);
		ambiente.texelSombra = 1f / lado;
	}

	/**
	 * Passa a desenhar no mapa: tira o mapa da unidade dele (ninguém lê enquanto se escreve),
	 * limpa a profundidade e liga o deslocamento de polígono (empurra a profundidade gravada um
	 * pouco para longe da luz, contra a "acne"). Mede o tempo na placa (aparece no F3).
	 */
	public void comecar(Ambiente ambiente) {
		glBeginQuery(GL_TIME_ELAPSED, consultas[quadro % 2]);
		glActiveTexture(GL_TEXTURE0 + Ambiente.UNIDADE_SOMBRA);
		glBindTexture(GL_TEXTURE_2D, 0);
		glActiveTexture(GL_TEXTURE0);
		glBindFramebuffer(GL_FRAMEBUFFER, fbo);
		glViewport(0, 0, lado, lado);
		glClear(GL_DEPTH_BUFFER_BIT);
		glEnable(GL_DEPTH_TEST);
		glEnable(GL_POLYGON_OFFSET_FILL);
		glPolygonOffset(1.5f, 3f);
		ambiente.passadaSombra = 1;
		ambiente.usaSombra = 0;
	}

	/** Volta ao normal e põe o mapa na unidade dele, para a cena ler. */
	public void terminar(Ambiente ambiente) {
		glDisable(GL_POLYGON_OFFSET_FILL);
		glBindFramebuffer(GL_FRAMEBUFFER, 0);
		ambiente.passadaSombra = 0;
		ambiente.usaSombra = 1;
		glActiveTexture(GL_TEXTURE0 + Ambiente.UNIDADE_SOMBRA);
		glBindTexture(GL_TEXTURE_2D, textura);
		glActiveTexture(GL_TEXTURE0);
		glEndQuery(GL_TIME_ELAPSED);
		// o resultado da consulta do quadro anterior (esperar pelo deste travaria a placa)
		int anterior = consultas[(quadro + 1) % 2];
		if (quadro > 0 && glGetQueryObjecti(anterior, GL_QUERY_RESULT_AVAILABLE) != 0) {
			milissegundos = glGetQueryObjecti(anterior, GL_QUERY_RESULT) / 1e6f;
		}
		quadro++;
	}

	/** Tempo da passada de profundidade na placa de vídeo, em ms (medido um quadro atrás). */
	public float milissegundos() {
		return milissegundos;
	}

	public int lado() {
		return lado;
	}

	public void liberar() {
		if (fbo != 0) {
			glDeleteFramebuffers(fbo);
			glDeleteTextures(textura);
			glDeleteQueries(consultas[0]);
			glDeleteQueries(consultas[1]);
			fbo = 0;
			textura = 0;
		}
	}

	private static void guardar(Matrix4f m, FloatBuffer b) {
		b.clear();
		m.storeTranspose(b);
		b.flip();
	}

	private static float[] normalizar(float[] v) {
		float t = (float) Math.sqrt(escalar(v, v));
		return new float[] { v[0] / t, v[1] / t, v[2] / t };
	}

	private static float escalar(float[] a, float[] b) {
		return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
	}

	private static float[] vetorial(float[] a, float[] b) {
		return new float[] { a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0] };
	}
}
