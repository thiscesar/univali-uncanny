package entidades;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import jogo.Regras;
import mundo.ConsultaAltura;
import mundo.GradeColisao;
import obj.Object3D;

/**
 * Uma caixa de cartuchos no chão da floresta. Com o jogador a até 1,5 m, a tecla E pega a
 * caixa: +6 de reserva. Só lógica; o desenho (um VboCube do projeto original com a textura
 * da caixa e um brilho leve) fica na TelaJogo.
 *
 * Por enquanto as posições são sorteadas; depois, quem decide é o AG da fase.
 */
public class CaixaMunicao extends Object3D {

	/** Giro da caixa no chão, em radianos (cada uma num ângulo, para não parecerem iguais). */
	public final float giro;
	private boolean pega = false;

	public CaixaMunicao(float x, float y, float z, float giro) {
		super(x, y, z);
		this.giro = giro;
	}

	public boolean pega() {
		return pega;
	}

	/** O jogador (os pés em x, z) está perto o bastante para pegar? */
	public boolean alcancavel(float jogadorX, float jogadorZ) {
		float dx = jogadorX - x, dz = jogadorZ - z;
		return !pega && dx * dx + dz * dz <= Regras.DISTANCIA_PEGAR * Regras.DISTANCIA_PEGAR;
	}

	/** Pega a caixa: devolve quantos cartuchos ela dá (0 se já foi pega). */
	public int pegar() {
		if (pega) return 0;
		pega = true;
		vivo = false;
		return Regras.CARTUCHOS_POR_CAIXA;
	}

	/**
	 * Sorteia "quantidade" caixas em lugares válidos: terra firme, pouco inclinada, longe da
	 * borda e dos troncos, a pelo menos 15 m do início do jogador e 20 m umas das outras.
	 */
	public static List<CaixaMunicao> sortear(ConsultaAltura chao, GradeColisao colisao, float[] inicio, int quantidade,
			Random random) {
		List<CaixaMunicao> caixas = new ArrayList<>();
		float t = chao.tamanho();
		for (int tentativa = 0; tentativa < 2000 && caixas.size() < quantidade; tentativa++) {
			float x = 30 + random.nextFloat() * (t - 60), z = 30 + random.nextFloat() * (t - 60);
			if (chao.ehAgua(x, z) || chao.anguloEm(x, z) > 25 || colisao.colide(x, z, 0.8f)) continue;
			if (distancia2(x, z, inicio[0], inicio[1]) < 15 * 15) continue;
			boolean longe = true;
			for (CaixaMunicao c : caixas) {
				if (distancia2(x, z, c.x, c.z) < 20 * 20) longe = false;
			}
			if (longe) {
				caixas.add(new CaixaMunicao(x, chao.alturaEm(x, z), z, random.nextFloat() * (float) Math.PI));
			}
		}
		return caixas;
	}

	private static float distancia2(float ax, float az, float bx, float bz) {
		return (ax - bx) * (ax - bx) + (az - bz) * (az - bz);
	}
}
