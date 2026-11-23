package render;

import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector4f;

import util.Utils3D;

/**
 * Uma câmera: posição e orientação, e a matriz de visão que vem delas.
 *
 * A orientação usa dois ângulos: a guinada (yaw) gira em torno do eixo Y, para os lados;
 * a arfagem (pitch) inclina para cima e para baixo, limitada a 85 graus para a câmera
 * não virar de cabeça para baixo. Com os dois em zero, a câmera olha para -Z.
 * Dos ângulos saem três vetores unitários: frente, direita e cima.
 */
public class Camera {

	public static final float ARFAGEM_MAXIMA = (float) Math.toRadians(85);

	public float x, y, z;
	/** Guinada em radianos; positiva gira para a direita. */
	public float guinada;
	/** Arfagem em radianos; positiva olha para cima. */
	public float arfagem;

	/** Para onde a câmera olha. */
	public final Vector4f frente = new Vector4f();
	/** Lado direito da câmera (sempre na horizontal). */
	public final Vector4f direita = new Vector4f();
	/** Topo da câmera. */
	public final Vector4f cima = new Vector4f();

	public Camera(float x, float y, float z, float guinada, float arfagem) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.guinada = guinada;
		this.arfagem = arfagem;
		calcularBase();
	}

	/** Gira a câmera (em radianos), limitando a arfagem. */
	public void girar(float dGuinada, float dArfagem) {
		guinada += dGuinada;
		arfagem = Math.max(-ARFAGEM_MAXIMA, Math.min(ARFAGEM_MAXIMA, arfagem + dArfagem));
		calcularBase();
	}

	/**
	 * Matriz de visão no formato do Utils3D.setLookAtMatrix do projeto original.
	 *
	 * Nessa função, o vetor "front" vira o eixo Z da câmera. Como a projeção (setFrustum)
	 * enxerga o lado -Z, o vetor passado como "front" é o oposto de para onde olhamos.
	 * A posição também precisa ir já projetada nos três eixos da câmera, como o Main3D
	 * original fazia com o personagem.
	 */
	public Matrix4f matrizVisao() {
		Vector4f tras = new Vector4f(-frente.x, -frente.y, -frente.z, 0);
		Vector4f posicao = new Vector4f(
				x * direita.x + y * direita.y + z * direita.z,
				x * cima.x + y * cima.y + z * cima.z,
				x * tras.x + y * tras.y + z * tras.z,
				1);
		return Utils3D.setLookAtMatrix(posicao, tras, cima, direita);
	}

	/** Recalcula frente, direita e cima a partir da guinada e da arfagem. */
	public void calcularBase() {
		arfagem = Math.max(-ARFAGEM_MAXIMA, Math.min(ARFAGEM_MAXIMA, arfagem));
		float cosArfagem = (float) Math.cos(arfagem);
		frente.set((float) Math.sin(guinada) * cosArfagem,
				(float) Math.sin(arfagem),
				(float) -Math.cos(guinada) * cosArfagem,
				0);

		// direita = frente x (0, 1, 0): fica sempre na horizontal
		direita.set(-frente.z, 0, frente.x, 0);
		Utils3D.vec3dNormilize(direita);

		// cima = direita x frente
		cima.set(direita.y * frente.z - direita.z * frente.y,
				direita.z * frente.x - direita.x * frente.z,
				direita.x * frente.y - direita.y * frente.x,
				0);
	}
}
