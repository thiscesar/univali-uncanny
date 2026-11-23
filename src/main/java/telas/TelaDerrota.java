package telas;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;

/** Tela de derrota provisória: Enter ou ESC voltam ao menu. */
public class TelaDerrota extends TelaProvisoria {

	public TelaDerrota(GerenciadorTelas gerenciador) {
		super(gerenciador, "REPROVADO", "Faltou na prova final. Enter: menu", 0.40f, 0.02f, 0.02f);
	}

	@Override
	public void atualizar(long diftime) {
		if (gerenciador.entrada().foiPressionada(GLFW_KEY_ENTER)
				|| gerenciador.entrada().foiPressionada(GLFW_KEY_ESCAPE)) {
			gerenciador.trocarPara(new TelaMenu(gerenciador));
		}
	}
}
