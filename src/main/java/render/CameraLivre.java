package render;

import static org.lwjgl.glfw.GLFW.*;

import dados.Opcoes;
import entrada.Entrada;

/**
 * Câmera livre, para voar pelo mapa e inspecioná-lo (depuração): atravessa o chão e
 * não tem gravidade.
 *
 * Controles:
 *   W A S D           anda para frente, para trás e para os lados
 *   Espaço / Ctrl     sobe / desce
 *   Shift             acelera
 *   mouse ou setas    giram a visão
 */
public class CameraLivre extends Camera {

	private static final float VELOCIDADE = 10f;          // metros por segundo
	private static final float FATOR_RAPIDO = 4f;         // multiplicador com Shift
	private static final float VELOCIDADE_GIRO = 1.5f;    // radianos por segundo, nas setas
	public static final float SENSIBILIDADE_MOUSE = 0.003f; // radianos por pixel

	public CameraLivre(float x, float y, float z, float guinada, float arfagem) {
		super(x, y, z, guinada, arfagem);
	}

	/** Lê teclado e mouse e move a câmera. diftime em milissegundos, como no resto do projeto. */
	public void atualizar(Entrada entrada, long diftime) {
		float dt = diftime / 1000.0f;

		float dGuinada = 0, dArfagem = 0;
		if (entrada.estaPressionada(GLFW_KEY_LEFT))  dGuinada -= VELOCIDADE_GIRO * dt;
		if (entrada.estaPressionada(GLFW_KEY_RIGHT)) dGuinada += VELOCIDADE_GIRO * dt;
		if (entrada.estaPressionada(GLFW_KEY_UP))    dArfagem += VELOCIDADE_GIRO * dt;
		if (entrada.estaPressionada(GLFW_KEY_DOWN))  dArfagem -= VELOCIDADE_GIRO * dt;
		// mouse (o y da tela cresce para baixo, por isso o sinal invertido)
		if (entrada.cursorPreso()) {
			float sensibilidade = SENSIBILIDADE_MOUSE * Opcoes.atual().sensibilidade; // ajustável nas opções
			dGuinada += entrada.deltaMouse().x * sensibilidade;
			dArfagem -= entrada.deltaMouse().y * sensibilidade;
		}
		girar(dGuinada, dArfagem);

		// movimento relativo à direção da câmera
		float passo = VELOCIDADE * dt;
		if (entrada.estaPressionada(GLFW_KEY_LEFT_SHIFT)) passo *= FATOR_RAPIDO;

		float avanco = 0, lado = 0, subida = 0;
		if (entrada.estaPressionada(GLFW_KEY_W)) avanco += 1;
		if (entrada.estaPressionada(GLFW_KEY_S)) avanco -= 1;
		if (entrada.estaPressionada(GLFW_KEY_D)) lado += 1;
		if (entrada.estaPressionada(GLFW_KEY_A)) lado -= 1;
		if (entrada.estaPressionada(GLFW_KEY_SPACE)) subida += 1;
		if (entrada.estaPressionada(GLFW_KEY_LEFT_CONTROL)) subida -= 1;

		x += (frente.x * avanco + direita.x * lado) * passo;
		y += (frente.y * avanco + direita.y * lado + subida) * passo;
		z += (frente.z * avanco + direita.z * lado) * passo;
	}
}
