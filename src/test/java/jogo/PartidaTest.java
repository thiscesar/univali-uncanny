package jogo;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import entidades.Prova;
import mundo.ConsultaAltura;

class PartidaTest {

	private static ConsultaAltura chao() {
		int[][] cm = new int[101][101];
		for (int[] l : cm) java.util.Arrays.fill(l, 100);
		return new ConsultaAltura(cm, 1f, 0.01f);
	}

	@Test
	void contaAsProvasEDizQuaisFaltam() {
		ConsultaAltura c = chao();
		Prova m1 = new Prova("M1", 10, 10, c), m2 = new Prova("M2", 50, 50, c), m3 = new Prova("M3", 90, 90, c);
		Partida p = new Partida(7, List.of(m1, m2, m3));
		assertEquals(0, p.provasColetadas());
		assertNull(p.provaAlcancavel(30, 30));
		assertSame(m2, p.provaAlcancavel(50.5f, 51));
		assertTrue(m2.coletar());
		assertFalse(m2.coletar(), "a mesma prova não conta duas vezes");
		assertEquals(1, p.provasColetadas());
		assertEquals(List.of("M1", "M3"), p.faltando());
		assertNull(p.provaAlcancavel(50, 50), "a prova pega não pode ser pega de novo");
		m1.coletar();
		m3.coletar();
		assertTrue(p.todasColetadas());
	}

	@Test
	void folhaFlutuaPertoDeUmMetroDoChao() {
		Prova m1 = new Prova("M1", 10, 10, chao());
		float menor = Float.MAX_VALUE, maior = -Float.MAX_VALUE;
		for (int i = 0; i < 300; i++) {
			m1.SimulaSe(16);
			menor = Math.min(menor, m1.y);
			maior = Math.max(maior, m1.y);
		}
		assertEquals(1 + Prova.ALTURA - Prova.OSCILACAO, menor, 0.01f); // chão a 1 m
		assertEquals(1 + Prova.ALTURA + Prova.OSCILACAO, maior, 0.01f);
	}

	@Test
	void estatisticasContamTempoTirosEPrecisao() {
		ConsultaAltura c = chao();
		Prova m1 = new Prova("M1", 10, 10, c), m2 = new Prova("M2", 50, 50, c);
		Partida p = new Partida(42, List.of(m1, m2));
		p.avancar(30);
		p.avancar(15.5f);
		p.registrarTiro(8, 6);
		p.registrarTiro(8, 0);
		m1.coletar();
		Partida.Estatisticas e = p.estatisticas(3);
		assertEquals(42, e.seed());
		assertEquals(45.5f, e.segundos(), 1e-4f);
		assertEquals(2, e.tiros());
		assertEquals(38, e.precisao()); // 6 de 16
		assertEquals(1, e.provasColetadas());
		assertEquals(2, e.provasTotal());
		assertEquals(3, e.cacadasSobrevividas());
	}
}
