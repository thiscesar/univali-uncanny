package jogo;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class ObjetivosTest {

	@Test
	void sequenciaCompleta() {
		Objetivos o = new Objetivos();
		assertEquals(Objetivos.Etapa.ACHAR_A_SALA, o.etapa(0, 3));
		assertEquals("Encontre a sala do professor.", o.texto(0, 3));
		assertFalse(o.abrirPortaProfessor(), "sem o crachá não abre");
		assertTrue(o.texto(0, 3).contains("procure o crachá"));
		o.pegarCracha();
		assertTrue(o.abrirPortaProfessor());
		assertEquals(Objetivos.Etapa.FALAR_COM_O_PROFESSOR, o.etapa(0, 3));
		assertFalse(o.portaEntradaAberta(), "a porta da frente só abre depois da conversa");
		List<String> falas = o.falarSemAsProvas(List.of("M1", "M2", "M3"));
		assertEquals(3, falas.size());
		assertTrue(o.portaEntradaAberta());
		assertEquals(Objetivos.Etapa.BUSCAR_PROVAS, o.etapa(1, 3));
		assertEquals("Encontre as provas na floresta (1 de 3).", o.texto(1, 3));
		assertEquals(List.of("Professor: \"Ainda faltam: M2, M3.\""), o.falarSemAsProvas(List.of("M2", "M3")));
		assertEquals(Objetivos.Etapa.ENTREGAR, o.etapa(3, 3));
	}
}
