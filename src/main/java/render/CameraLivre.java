package render;

import static org.lwjgl.glfw.GLFW.*;

import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector4f;

import dados.Opcoes;
import entrada.Entrada;
import util.Utils3D;

/**
 * Câmera livre provisória, para voar sobre o terreno e inspecioná-lo.
 * Será substituída pela câmera em primeira pessoa do jogador, mas pode continuar útil
 * para depuração.
 *
 * Controles:
 *   W A S D           anda para frente, para trás e para os lados
 *   Espaço / Ctrl     sobe / desce
 *   Shift             acelera
 *   mouse ou setas    giram a visão
 *
 * Teclado e mouse vêm da classe Entrada. O mouse só gira a visão com o cursor preso.
 *
 * Orientação: a guinada (yaw) gira em torno do eixo Y e a arfagem (pitch) inclina para
 * cima e para baixo. Com os dois em zero, a câmera olha para -Z.
 */
public class CameraLivre {

	public float x, y, z;
	/** Guinada em radianos; positiva gira para a direita. */
	public float guinada;
	/** Arfagem em radianos; positiva olha para cima. */
	public float arfagem;

	private static final float VELOCIDADE = 10f;          // unidades por segundo
	private static final float FATOR_RAPIDO = 4f;         // multiplicador com Shift
	private static final float VELOCIDADE_GIRO = 1.5f;    // radianos por segundo, nas setas
	private static final float SENSIBILIDADE_MOUSE = 0.003f; // radianos por pixel
	private static final float ARFAGEM_MAXIMA = (float) Math.toRadians(85);

	/** Para onde a câmera olha. */
	public final Vector4f frente = new Vector4f();
	/** Lado direito da câmera. */
	public final Vector4f direita = new Vector4f();
	/** Topo da câmera. */
	public final Vector4f cima = new Vector4f();

	public CameraLivre(float x, float y, float z, float guinada, float arfagem) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.guinada = guinada;
		this.arfagem = arfagem;
		calcularBase();
	}

	/** Lê teclado e mouse e move a câmera. diftime em milissegundos, como no resto do projeto. */
	public void atualizar(Entrada entrada, long diftime) {
		float dt = diftime / 1000.0f;

		// giro pelas setas
		if (pressionada(entrada, GLFW_KEY_LEFT))  guinada -= VELOCIDADE_GIRO * dt;
		if (pressionada(entrada, GLFW_KEY_RIGHT)) guinada += VELOCIDADE_GIRO * dt;
		if (pressionada(entrada, GLFW_KEY_UP))    arfagem += VELOCIDADE_GIRO * dt;
		if (pressionada(entrada, GLFW_KEY_DOWN))  arfagem -= VELOCIDADE_GIRO * dt;

		// giro pelo mouse (o y da tela cresce para baixo, por isso o sinal invertido)
		if (entrada.cursorPreso()) {
			float sensibilidade = SENSIBILIDADE_MOUSE * Opcoes.atual().sensibilidade; // ajustável nas opções
			guinada += entrada.deltaMouse().x * sensibilidade;
			arfagem -= entrada.deltaMouse().y * sensibilidade;
		}

		// não deixa virar de cabeça para baixo
		arfagem = Math.max(-ARFAGEM_MAXIMA, Math.min(ARFAGEM_MAXIMA, arfagem));
		calcularBase();

		// movimento relativo à direção da câmera
		float passo = VELOCIDADE * dt;
		if (pressionada(entrada, GLFW_KEY_LEFT_SHIFT)) passo *= FATOR_RAPIDO;

		float avanco = 0, lado = 0, subida = 0;
		if (pressionada(entrada, GLFW_KEY_W)) avanco += 1;
		if (pressionada(entrada, GLFW_KEY_S)) avanco -= 1;
		if (pressionada(entrada, GLFW_KEY_D)) lado += 1;
		if (pressionada(entrada, GLFW_KEY_A)) lado -= 1;
		if (pressionada(entrada, GLFW_KEY_SPACE)) subida += 1;
		if (pressionada(entrada, GLFW_KEY_LEFT_CONTROL)) subida -= 1;

		x += (frente.x * avanco + direita.x * lado) * passo;
		y += (frente.y * avanco + direita.y * lado + subida) * passo;
		z += (frente.z * avanco + direita.z * lado) * passo;
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
	private void calcularBase() {
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

	private static boolean pressionada(Entrada entrada, int tecla) {
		return entrada.estaPressionada(tecla);
	}
}
