package jogo;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class PortasSozinhasTest {

	private static PortasSozinhas portas() {
		return new PortasSozinhas(List.of(new float[] { 0, 2 }, new float[] { 20, -2 }));
	}

	@Test
	void aDoInicioAbreSozinhaDepoisDeUnsSegundos() {
		PortasSozinhas p = portas();
		float t = 0;
		while (t < PortasSozinhas.TEMPO_DO_INICIO - 0.1f) {
			assertTrue(p.atualizar(0.05f, t, 0, 8).isEmpty(), "ainda fechada em " + t);
			t += 0.05f;
		}
		List<Integer> abriu = List.of();
		while (abriu.isEmpty()) {
			abriu = p.atualizar(0.05f, t, 0, 8);
			t += 0.05f;
		}
		assertEquals(List.of(0), abriu);
		assertTrue(p.fechada(1), "a do corredor espera o jogador");
	}

	@Test
	void aDoInicioAbreAntesSeOJogadorChegaPerto() {
		PortasSozinhas p = portas();
		assertEquals(List.of(0), p.atualizar(0.05f, 2, 0, 3.5f));
	}

	@Test
	void asOutrasAbremComOJogadorPertoEOAnguloEntreabreParaEEscancara() {
		PortasSozinhas p = portas();
		assertTrue(p.atualizar(0.05f, 2, 10, 0).isEmpty(), "a 10 m ainda não");
		assertTrue(p.atualizar(0.05f, 2, 14, 0).contains(1));
		for (int i = 0; i < 12; i++) p.atualizar(0.05f, 3, 14, 0); // 0,6 s
		assertEquals(12, p.angulo(1), 0.01f, "entreaberta, parada");
		for (int i = 0; i < 40; i++) p.atualizar(0.05f, 5, 14, 0);
		assertEquals(PortasSozinhas.ABERTURA, p.angulo(1), 0.01f, "escancarada");
	}
}
