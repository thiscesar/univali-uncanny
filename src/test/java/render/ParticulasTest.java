package render;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ParticulasTest {

	@Test
	void somemNoFimDaVida() {
		Particulas p = new Particulas();
		p.faiscas(0, 1, 0, 10);
		p.fumaca(0, 1, 0, 5);
		assertEquals(15, p.quantidade());
		p.atualizar(0.5f);
		assertEquals(5, p.quantidade(), "as faíscas duram no máximo 0,4 s; a fumaça, mais");
		p.atualizar(2f);
		assertEquals(0, p.quantidade());
	}

	@Test
	void temLimite() {
		Particulas p = new Particulas();
		p.poeira(0, 0, 0, Particulas.MAXIMO + 50);
		assertEquals(Particulas.MAXIMO, p.quantidade());
	}
}
