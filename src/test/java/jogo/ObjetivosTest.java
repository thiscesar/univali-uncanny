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
		o.pegarCracha();
		assertFalse(o.temCracha(), "antes de bater na porta, o crachá ainda não conta");
		assertFalse(o.podePegarCracha());
		assertFalse(o.abrirPortaProfessor(), "sem o crachá não abre");
		assertEquals(2, o.respostaDaPorta().size(), "ele responde lá de dentro");
		assertEquals(List.of(), o.respostaDaPorta(), "só na primeira vez");
		assertTrue(o.texto(0, 3).contains("crachá"));
		assertTrue(o.podePegarCracha());
		o.pegarCracha();
		assertTrue(o.temCracha());
		assertFalse(o.podePegarCracha());
		assertTrue(o.abrirPortaProfessor());
		assertEquals(Objetivos.Etapa.FALAR_COM_O_PROFESSOR, o.etapa(0, 3));
		assertFalse(o.portaEntradaAberta(), "a porta da frente só abre depois da conversa");
		List<String> falas = o.falarSemAsProvas(List.of("M1", "M2", "M3"));
		assertEquals(3, falas.size());
		assertTrue(o.portaEntradaAberta());
		assertEquals(Objetivos.Etapa.BUSCAR_PROVAS, o.etapa(1, 3));
		assertEquals("Encontre as provas na floresta (1 de 3).", o.texto(1, 3));
		assertEquals(List.of("Professor: \"Hm, deixa eu ver... ainda faltam: M2, M3.\""), o.falarSemAsProvas(List.of("M2", "M3")));
		assertEquals(Objetivos.Etapa.ENTREGAR, o.etapa(3, 3));
	}

	@Test
	void cadaFalaComASuaGravacao() {
		assertEquals("audio/voz_professor/quem_esta_ai.ogg", Objetivos.arquivoDeVoz("Quem está aí?"));
		assertEquals("audio/voz_professor/faltam_M2_M3.ogg", Objetivos.arquivoDeVoz("Hm, deixa eu ver... ainda faltam: M2, M3."));
		assertEquals("audio/voz_professor/faltam_M1.ogg", Objetivos.arquivoDeVoz("Hm, deixa eu ver... ainda faltam: M1."));
		assertNull(Objetivos.arquivoDeVoz("..."), "sem gravação: a voz sussurrada");
		// toda fala que o jogo usa tem o arquivo
		Objetivos o = new Objetivos();
		o.abrirPortaProfessor();
		List<String> falas = new java.util.ArrayList<>(o.respostaDaPorta());
		falas.addAll(o.falarSemAsProvas(List.of("M1", "M2", "M3")));
		falas.addAll(o.falarSemAsProvas(List.of("M1", "M3")));
		for (String f : falas) {
			String texto = f.substring(f.indexOf('"') + 1, f.lastIndexOf('"'));
			String arquivo = Objetivos.arquivoDeVoz(texto);
			assertNotNull(arquivo, texto);
			assertTrue(new java.io.File(arquivo).exists(), "falta " + arquivo);
		}
	}
}
