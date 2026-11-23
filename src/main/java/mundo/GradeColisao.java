package mundo;

import java.util.ArrayList;
import java.util.List;

/**
 * Obstáculos do chão (troncos de árvore) para colisão, guardados numa grade de células.
 *
 * Testar o jogador contra milhares de troncos a cada quadro seria lento. Com a grade,
 * cada tronco fica na lista da célula de 4 x 4 m onde está, e uma consulta só olha as
 * células em volta do ponto. Cada obstáculo é um círculo no plano XZ.
 */
public class GradeColisao {

	/** Um círculo no chão: centro (x, z) e raio, em metros. */
	public record Circulo(float x, float z, float raio) {}

	public static final float TAMANHO_CELULA = 4f;

	private final int celulas;
	private final List<List<Circulo>> grade;
	private int quantidade = 0;

	/** @param tamanhoMapa lado do mapa em metros (o mapa vai de 0 a este valor em x e z) */
	public GradeColisao(float tamanhoMapa) {
		celulas = (int) Math.ceil(tamanhoMapa / TAMANHO_CELULA) + 1;
		grade = new ArrayList<>(celulas * celulas);
		for (int i = 0; i < celulas * celulas; i++) {
			grade.add(new ArrayList<>());
		}
	}

	public void adicionar(float x, float z, float raio) {
		grade.get(indice(celula(x), celula(z))).add(new Circulo(x, z, raio));
		quantidade++;
	}

	public int quantidade() {
		return quantidade;
	}

	/** Um círculo de raio "raio" em (x, z) encosta em algum obstáculo? */
	public boolean colide(float x, float z, float raio) {
		for (Circulo c : proximos(x, z)) {
			float dx = x - c.x(), dz = z - c.z(), soma = raio + c.raio();
			if (dx * dx + dz * dz < soma * soma) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Empurra um círculo para fora dos obstáculos em que ele estiver entrando, na direção
	 * do centro do obstáculo para o círculo. Andando contra um tronco, o resultado é
	 * deslizar em volta dele em vez de parar seco. Devolve {x, z} corrigidos.
	 */
	public float[] resolver(float x, float z, float raio) {
		for (int volta = 0; volta < 3; volta++) { // algumas voltas resolvem troncos encostados
			boolean mexeu = false;
			for (Circulo c : proximos(x, z)) {
				float dx = x - c.x(), dz = z - c.z(), soma = raio + c.raio();
				float d2 = dx * dx + dz * dz;
				if (d2 < soma * soma) {
					float d = (float) Math.sqrt(d2);
					if (d < 1e-5f) { dx = 1; dz = 0; d = 1; } // exatamente no centro: sai para qualquer lado
					x = c.x() + dx / d * soma;
					z = c.z() + dz / d * soma;
					mexeu = true;
				}
			}
			if (!mexeu) {
				break;
			}
		}
		return new float[] { x, z };
	}

	/** Os obstáculos das 9 células em volta do ponto (suficiente para raios de até 4 m). */
	private List<Circulo> proximos(float x, float z) {
		List<Circulo> lista = new ArrayList<>();
		int cx = celula(x), cz = celula(z);
		for (int j = cz - 1; j <= cz + 1; j++) {
			for (int i = cx - 1; i <= cx + 1; i++) {
				if (i >= 0 && j >= 0 && i < celulas && j < celulas) {
					lista.addAll(grade.get(indice(i, j)));
				}
			}
		}
		return lista;
	}

	private int celula(float v) {
		return Math.max(0, Math.min(celulas - 1, (int) Math.floor(v / TAMANHO_CELULA)));
	}

	private int indice(int i, int j) {
		return j * celulas + i;
	}
}
