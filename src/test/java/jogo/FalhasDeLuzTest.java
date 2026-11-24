package jogo;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class FalhasDeLuzTest {

	// lâmpada 0: acesa e firme; 1: pisca; 2: queimada; 3: pisca
	private static FalhasDeLuz luzes() {
		return new FalhasDeLuz(new boolean[] { true, true, false, true }, new boolean[] { false, true, false, true }, 5);
	}

	@Test
	void queimadaNuncaAcendeEAsQuePiscamApagamDeVezEmQuando() {
		FalhasDeLuz f = luzes();
		boolean apagou1 = false, apagou3 = false, firmeApagou = false;
		int rajadas = 0;
		for (int i = 0; i < 20 * 30; i++) { // 30 s, fora do prédio (sem falha geral)
			rajadas += f.atualizar(0.05f, false).size();
			assertEquals(0, f.brilho(2));
			if (f.brilho(0) < 1) firmeApagou = true;
			if (f.brilho(1) < 0.5f) apagou1 = true;
			if (f.brilho(3) < 0.5f) apagou3 = true;
		}
		assertTrue(apagou1 && apagou3, "as que piscam falham");
		assertFalse(firmeApagou, "a firme, fora da falha geral, fica acesa");
		assertTrue(rajadas >= 4 && rajadas <= 30, "rajadas em 30 s: " + rajadas);
	}

	@Test
	void falhaGeralSoComOJogadorDentroEApagaTodas() {
		FalhasDeLuz f = luzes();
		for (int i = 0; i < 20 * 120; i++) {
			assertTrue(f.atualizar(0.05f, false).stream().noneMatch(FalhasDeLuz.Evento::geral));
		}
		boolean geral = false, firmeApagou = false;
		for (int i = 0; i < 20 * 120 && !firmeApagou; i++) {
			List<FalhasDeLuz.Evento> e = f.atualizar(0.05f, true);
			if (e.stream().anyMatch(FalhasDeLuz.Evento::geral)) geral = true;
			if (f.falhaGeral() && f.brilho(0) < 0.5f) firmeApagou = true;
		}
		assertTrue(geral && firmeApagou, "a falha geral pega até as lâmpadas firmes");
	}

	@Test
	void forcarAFalhaGeralComecaNaHora() {
		FalhasDeLuz f = luzes();
		f.forcarFalhaGeral();
		assertTrue(f.atualizar(0.05f, true).stream().anyMatch(FalhasDeLuz.Evento::geral));
		assertTrue(f.falhaGeral());
	}
}
