package jogo;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class NotaTest {

	@Test
	void rapidoESemAparicoesTiraDez() {
		assertEquals(10f, Nota.calcular(5 * 60, 0));
		assertEquals(10f, Nota.calcular(8 * 60, 0), "8 minutos ainda não descontam");
	}

	@Test
	void perdeMeioPontoPorMinutoAcimaDeOito() {
		assertEquals(9.5f, Nota.calcular(9 * 60, 0));
		assertEquals(8.0f, Nota.calcular(12 * 60, 0));
		assertEquals(8.8f, Nota.calcular(10 * 60 + 24, 0), "frações de minuto contam: 2,4 min a mais");
	}

	@Test
	void bonusPorAparicaoAteUmPonto() {
		assertEquals(8.5f, Nota.calcular(12 * 60, 2));
		assertEquals(9.0f, Nota.calcular(12 * 60, 4));
		assertEquals(9.0f, Nota.calcular(12 * 60, 9), "o bônus para em +1");
		assertEquals(10f, Nota.calcular(5 * 60, 3), "nunca passa de 10");
	}

	@Test
	void nuncaAbaixoDeSeis() {
		assertEquals(6f, Nota.calcular(60 * 60, 0));
		assertEquals(6f, Nota.calcular(30 * 60, 2));
	}

	@Test
	void comentarioEFormato() {
		assertEquals("Passou raspando.", Nota.comentario(7.4f));
		assertEquals("Nada mal.", Nota.comentario(7.5f));
		assertEquals("Aluno destaque!", Nota.comentario(9f));
		assertEquals("8,7", Nota.formatar(8.7f));
	}
}
