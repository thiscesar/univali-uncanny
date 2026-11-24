package telas;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class HistoriaTest {

	@Test
	void todaLinhaCabeNaFolha() {
		for (String[] p : Historia.PAGINAS) {
			for (String l : p) assertTrue(l.length() <= 42, "linha longa: " + l);
		}
	}

	@Test
	void umaTeclaPorLetraUmEspacoPorEspacoEUmRetornoPorLinha() {
		Historia h = new Historia();
		List<Historia.Som> sons = new ArrayList<>();
		while (h.pagina() == 0) sons.addAll(h.atualizar(0.016f));
		String[] p = Historia.PAGINAS.get(0);
		long letras = 0, espacos = 0;
		for (String l : p) for (char c : l.toCharArray()) if (c == ' ') espacos++; else letras++;
		assertEquals(letras, sons.stream().filter(s -> s == Historia.Som.TECLA).count());
		assertEquals(espacos, sons.stream().filter(s -> s == Historia.Som.ESPACO).count());
		assertEquals(p.length, sons.stream().filter(s -> s == Historia.Som.RETORNO).count());
		assertEquals(Historia.Som.PAGINA, sons.get(sons.size() - 1), "a folha troca");
	}

	@Test
	void letrasAparecemAosPoucosEASegundaLinhaDepoisDaPrimeira() {
		Historia h = new Historia();
		h.atualizar(0.8f);
		assertTrue(h.letrasVisiveis(0) > 0 && h.letrasVisiveis(0) < h.linhas()[0].length());
		assertEquals(0, h.letrasVisiveis(1));
		assertFalse(h.paginaPronta());
	}

	@Test
	void enterTerminaAPaginaEDepoisPassaSemRajadaDeTeclas() {
		Historia h = new Historia();
		h.atualizar(0.5f);
		h.pular();
		assertTrue(h.paginaPronta());
		for (int l = 0; l < h.linhas().length; l++) assertEquals(h.linhas()[l].length(), h.letrasVisiveis(l));
		assertTrue(h.atualizar(0.016f).stream().noneMatch(s -> s == Historia.Som.TECLA), "as letras puladas não tocam");
		h.pular();
		assertEquals(1, h.pagina());
		for (int i = 1; i < Historia.PAGINAS.size(); i++) {
			h.pular();
			h.pular();
		}
		assertTrue(h.terminou());
	}

	@Test
	void historiaInteiraLevaUmMinutoEPoucoSozinha() {
		Historia h = new Historia();
		float t = 0;
		while (!h.terminou() && t < 300) {
			h.atualizar(0.05f);
			t += 0.05f;
		}
		assertTrue(h.terminou());
		assertTrue(t > 40 && t < 120, "levou " + t + " s");
	}
}
