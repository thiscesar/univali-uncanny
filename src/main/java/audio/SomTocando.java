package audio;

/**
 * Identifica um som que foi mandado tocar, para depois parar ou mover (por exemplo,
 * o sussurro em loop de uma prova).
 *
 * Guarda a fonte usada e a "geração" dela: se a fonte for reaproveitada por outro som,
 * a geração muda e este identificador deixa de valer, sem parar o som errado.
 */
public final class SomTocando {

	final int indiceFonte;
	final long geracao;

	SomTocando(int indiceFonte, long geracao) {
		this.indiceFonte = indiceFonte;
		this.geracao = geracao;
	}
}
