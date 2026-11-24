package entidades;

import jogo.Regras;
import obj.Object3D;

/**
 * Uma caixa de cartuchos no chão da floresta. Com o jogador a até 1,5 m, a tecla E pega a
 * caixa: +6 de reserva. Só lógica; o desenho (um VboCube do projeto original com a textura
 * da caixa e um brilho leve) fica na TelaJogo.
 *
 * Onde ficam: quem decide é o AG da fase (fase.ProblemaLayout).
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
}
