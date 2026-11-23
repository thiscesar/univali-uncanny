package md2;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class AnimadorTest {

	// "parado": quadros 0 a 3; "morte": quadros 4 a 7. Ambas a 10 quadros por segundo.
	private final Animador a = new Animador(List.of(new Animacao("parado", 0, 3, 10), new Animacao("morte", 4, 7, 10)));

	private void avancar(float segundos) {
		for (int i = 0; i < Math.round(segundos / 0.01f); i++) a.atualizar(0.01f);
	}

	@Test
	void emLoopMisturaOQuadroAtualComOProximoEDaAVolta() {
		a.tocar("parado", true);
		avancar(0.15f);
		assertEquals(1, a.quadroA());
		assertEquals(2, a.quadroB());
		assertEquals(0.5f, a.fator(), 0.02f);
		avancar(0.2f); // posição 3,5: do último quadro para o primeiro
		assertEquals(3, a.quadroA());
		assertEquals(0, a.quadroB());
	}

	@Test
	void semLoopParaNoUltimoQuadroEAvisaUmaVez() {
		AtomicInteger avisos = new AtomicInteger();
		a.tocar("morte", false, avisos::incrementAndGet);
		avancar(0.25f);
		assertFalse(a.terminou());
		avancar(1f);
		assertTrue(a.terminou());
		assertEquals(7, a.quadroA());
		assertEquals(7, a.quadroB());
		assertEquals(0f, a.fator());
		assertEquals(1, avisos.get());
	}

	@Test
	void trocarDeAnimacaoFazTransicaoDoQuadroDaTela() {
		a.tocar("parado", true);
		avancar(0.21f); // mostrando ~ quadro 2
		int naTela = a.quadroMostrado();
		a.tocar("morte", false);
		assertEquals(naTela, a.quadroA(), "a transição sai do quadro que estava na tela");
		assertEquals(4, a.quadroB(), "e vai para o primeiro quadro da nova");
		avancar(Animador.DURACAO_TRANSICAO / 2);
		assertEquals(0.5f, a.fator(), 0.1f);
		avancar(Animador.DURACAO_TRANSICAO);
		assertEquals(4, a.quadroA(), "depois da transição, a animação nova segue normal");
	}

	@Test
	void velocidadeMultiplicaOTempo() {
		a.tocar("parado", true);
		a.definirVelocidade(2);
		avancar(0.12f); // 0,12 s em dobro = 2,4 quadros
		assertEquals(2, a.quadroA());
	}

	@Test
	void fpsPorAnimacao() {
		a.definirFps("parado", 20);
		a.tocar("parado", true);
		avancar(0.12f); // 0,12 s a 20 quadros por segundo = 2,4 quadros
		assertEquals(2, a.quadroA());
	}

	@Test
	void animacaoInexistenteDaErroComALista() {
		var erro = assertThrows(IllegalArgumentException.class, () -> a.tocar("voar", true));
		assertTrue(erro.getMessage().contains("parado"));
	}
}
