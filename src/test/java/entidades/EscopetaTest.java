package entidades;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import jogo.Regras;

class EscopetaTest {

	private static void avancar(Escopeta e, float segundos, boolean correndo) {
		for (int i = 0; i < Math.round(segundos / 0.016f); i++) {
			e.atualizar(0.016f, 0, 0, 0, correndo ? 6 : 0, correndo);
		}
	}

	private static List<String> eventos(Escopeta e) {
		List<String> lista = new ArrayList<>();
		e.definirEventos(new Escopeta.Eventos() {
			@Override public void disparou() { lista.add("tiro"); }
			@Override public void engatilhou() { lista.add("telha"); }
			@Override public void cartuchoInserido() { lista.add("cartucho"); }
			@Override public void cliqueSeco() { lista.add("seco"); }
		});
		return lista;
	}

	@Test
	void comecaComDoisNoCanoEOitoDeReserva() {
		Escopeta e = new Escopeta();
		assertEquals(Regras.CAPACIDADE_CANO, e.cano());
		assertEquals(Regras.RESERVA_INICIAL, e.reserva());
		assertEquals(Escopeta.Estado.PRONTA, e.estado());
	}

	@Test
	void atiraEngatilhaENaoAtiraDeNovoNoMeio() {
		Escopeta e = new Escopeta();
		List<String> ev = eventos(e);
		assertTrue(e.gatilho());
		assertEquals(Escopeta.Estado.ATIRANDO, e.estado());
		assertFalse(e.gatilho(), "no meio do tiro e da telha não sai outro");
		avancar(e, Regras.DURACAO_TIRO + 0.05f, false);
		assertEquals(List.of("tiro", "telha"), ev);
		assertEquals(1, e.cano());
		assertEquals(0, e.telha(), 1e-6f);
	}

	@Test
	void vaziaDaCliqueSeco() {
		Escopeta e = new Escopeta();
		List<String> ev = eventos(e);
		e.gatilho();
		avancar(e, 0.4f, false);
		e.gatilho();
		avancar(e, 0.4f, false);
		assertEquals(Escopeta.Estado.VAZIA, e.estado());
		assertFalse(e.gatilho());
		assertEquals("seco", ev.get(ev.size() - 1));
	}

	@Test
	void recarregaUmPorVezEEngatilhaSeOCanoEstavaVazio() {
		Escopeta e = new Escopeta();
		e.gatilho();
		avancar(e, 0.4f, false);
		e.gatilho();
		avancar(e, 0.4f, false);
		List<String> ev = eventos(e);
		e.recarregar();
		assertEquals(Escopeta.Estado.RECARREGANDO, e.estado());
		avancar(e, Regras.DURACAO_CARTUCHO + 0.02f, false);
		assertEquals(1, e.cano());
		assertEquals(Regras.RESERVA_INICIAL - 1, e.reserva());
		avancar(e, Regras.DURACAO_CARTUCHO + 0.4f, false);
		assertEquals(2, e.cano());
		assertEquals(Escopeta.Estado.PRONTA, e.estado());
		assertEquals(List.of("cartucho", "cartucho", "telha"), ev);
	}

	@Test
	void recargaEhInterrompidaPeloTiroSeHouverCartucho() {
		Escopeta e = new Escopeta();
		e.gatilho();
		avancar(e, 0.4f, false);
		e.recarregar();
		avancar(e, 0.1f, false);
		assertTrue(e.gatilho());
		assertEquals(0, e.cano());
		assertEquals(Regras.RESERVA_INICIAL, e.reserva(), "o cartucho que estava entrando não foi gasto");
	}

	@Test
	void naoRecarregaCheiaNemSemReserva() {
		Escopeta e = new Escopeta();
		e.recarregar();
		assertEquals(Escopeta.Estado.PRONTA, e.estado());
	}

	@Test
	void correndoAArmaAbaixaENaoAtira() {
		Escopeta e = new Escopeta();
		avancar(e, 0.5f, true);
		assertFalse(e.gatilho());
		assertTrue(e.giro()[0] < -0.2f, "cano apontando para baixo");
		avancar(e, 0.4f, false); // parou de correr: em ~0,2 s está de volta
		assertTrue(e.gatilho());
	}

	@Test
	void caixaDaSeisCartuchosUmaVezSo() {
		CaixaMunicao c = new CaixaMunicao(10, 0, 10, 0);
		assertTrue(c.alcancavel(10.5f, 11));
		assertFalse(c.alcancavel(12, 10));
		assertEquals(Regras.CARTUCHOS_POR_CAIXA, c.pegar());
		assertEquals(0, c.pegar());
		assertFalse(c.alcancavel(10, 10));
	}
}
