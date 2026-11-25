package jogo;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import jogo.FalasJubilado.Fala;

class FalasJubiladoTest {

	@Test
	void seApresentaPrimeiroEDepoisAmeacaSemProva() {
		FalasJubilado f = new FalasJubilado();
		assertEquals(Fala.APRESENTACAO, f.aoVer(30, 0));
		assertEquals(Fala.AMEACA, f.aoVer(60, 0));
		assertEquals(Fala.AMEACA, f.aoVer(90, 0), "sem prova, só tem a ameaça");
	}

	@Test
	void comProvaAlternaEntrePedirEAmeacar() {
		FalasJubilado f = new FalasJubilado();
		f.aoVer(10, 1);
		assertEquals(Fala.PROVA, f.aoVer(40, 1));
		assertEquals(Fala.AMEACA, f.aoVer(70, 2));
		assertEquals(Fala.PROVA, f.aoVer(100, 2));
	}

	@Test
	void falaNoMaximoUmaVezACadaIntervalo() {
		FalasJubilado f = new FalasJubilado();
		assertNotNull(f.aoVer(10, 0));
		assertNull(f.aoVer(10 + FalasJubilado.INTERVALO - 1, 0), "falou há pouco: só grita");
		assertNull(f.aoLevantar(10 + FalasJubilado.INTERVALO - 1, 0));
		assertNotNull(f.aoVer(10 + FalasJubilado.INTERVALO, 0));
	}

	@Test
	void levantaAmeacandoEPedeAProvaSeAcabouDeAmeacar() {
		FalasJubilado f = new FalasJubilado();
		f.aoVer(0, 1); // apresentação
		assertEquals(Fala.AMEACA, f.aoLevantar(30, 1));
		assertEquals(Fala.PROVA, f.aoLevantar(60, 1));
		assertEquals("audio/voz_jubilado/vou_te_matar.ogg", Fala.AMEACA.arquivo());
	}
}
