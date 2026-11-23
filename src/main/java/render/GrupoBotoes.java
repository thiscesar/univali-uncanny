package render;

import static org.lwjgl.glfw.GLFW.*;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.util.vector.Vector2f;

import entrada.Entrada;

/**
 * Um conjunto de botões que também funciona pelo teclado: as setas mudam o botão
 * selecionado e o Enter aciona. Passar o mouse sobre um botão também o seleciona.
 */
public class GrupoBotoes {

	private final List<Botao> botoes = new ArrayList<>();
	private int foco = 0;

	public Botao adicionar(Botao botao) {
		botoes.add(botao);
		return botao;
	}

	public List<Botao> botoes() {
		return botoes;
	}

	public int foco() {
		return foco;
	}

	/** Devolve o botão acionado neste quadro (por clique ou Enter), ou null. */
	public Botao atualizar(Entrada entrada) {
		int n = botoes.size();
		if (n == 0) {
			return null;
		}

		// teclado
		if (entrada.foiPressionada(GLFW_KEY_DOWN) || entrada.foiPressionada(GLFW_KEY_RIGHT)) {
			foco = (foco + 1) % n;
		}
		if (entrada.foiPressionada(GLFW_KEY_UP) || entrada.foiPressionada(GLFW_KEY_LEFT)) {
			foco = (foco - 1 + n) % n;
		}

		// mouse: só muda o foco quando o mouse se move, para não brigar com as setas
		Vector2f movimento = entrada.deltaMouse();
		if (movimento.x != 0 || movimento.y != 0) {
			for (int i = 0; i < n; i++) {
				if (botoes.get(i).contem(entrada.mouseX(), entrada.mouseY())) {
					foco = i;
				}
			}
		}

		Botao acionado = null;
		for (int i = 0; i < n; i++) {
			Botao b = botoes.get(i);
			b.focado = (i == foco);
			if (b.atualizar(entrada)) {
				acionado = b;
			}
		}
		if (acionado == null
				&& (entrada.foiPressionada(GLFW_KEY_ENTER) || entrada.foiPressionada(GLFW_KEY_KP_ENTER))) {
			acionado = botoes.get(foco);
		}
		return acionado;
	}

	public void desenhar(Overlay2D overlay) {
		for (Botao b : botoes) {
			b.desenhar(overlay);
		}
	}
}
