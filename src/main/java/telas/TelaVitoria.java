package telas;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;

/** Tela de vitória provisória: Enter ou ESC voltam ao menu. */
public class TelaVitoria extends TelaProvisoria {

	public TelaVitoria(GerenciadorTelas gerenciador) {
		super(gerenciador, "APROVADO", "Avaliação concluída. Enter: menu", 0.05f, 0.35f, 0.10f);
	}

	@Override
	public void atualizar(long diftime) {
		if (gerenciador.entrada().foiPressionada(GLFW_KEY_ENTER)
				|| gerenciador.entrada().foiPressionada(GLFW_KEY_ESCAPE)) {
			gerenciador.trocarPara(new TelaMenu(gerenciador));
		}
	}
}
