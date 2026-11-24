package entidades;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import fase.Ponto;
import mundo.GradeColisao;

class PredioUnivaliTest {

	/** A trilha chega vindo do leste (+x) até (150, 150). */
	private static PredioUnivali predio() {
		List<Ponto> trilha = List.of(new Ponto(200, 150), new Ponto(180, 151), new Ponto(160, 149), new Ponto(150, 150));
		return PredioUnivali.naChegada(new Ponto(150, 150), trilha, 2, 1);
	}

	@Test
	void frenteViradaParaQuemChegaEAPortaPertoDoFimDaTrilha() {
		PredioUnivali p = predio();
		assertEquals(1, p.frenteX(), 1e-6);  // olha para o leste, de onde a trilha vem
		assertEquals(0, p.frenteZ(), 1e-6);
		float[] porta = p.porta();
		assertEquals(150 - 10 + PredioUnivali.PROFUNDIDADE / 2, porta[0], 1e-3);
		assertTrue(p.naEntrega(150, 150), "o fim da trilha fica na área da entrega");
		assertFalse(p.naEntrega(160, 150));
	}

	@Test
	void retanguloGiraJuntoComOPredio() {
		GradeColisao.Retangulo r = predio().retangulo();
		// virado para o leste, a largura (40 m) fica no eixo z
		assertEquals(PredioUnivali.PROFUNDIDADE, r.xMax() - r.xMin(), 1e-4);
		assertEquals(PredioUnivali.LARGURA, r.zMax() - r.zMin(), 1e-4);
		float[] porta = predio().porta();
		assertEquals(r.xMax(), porta[0], 1e-4); // a porta fica na parede da frente
	}

	@Test
	void janelasAcesasApagadasEUmaPiscando() {
		PredioUnivali p = predio();
		long acesas = p.janelas().stream().filter(PredioUnivali.Janela::acesa).count();
		assertTrue(p.janelas().size() > 50);
		assertTrue(acesas > 10 && acesas < p.janelas().size() - 10, "acesas: " + acesas);
		assertTrue(p.janelas().get(p.janelaPiscando()).acesa());
	}

	@Test
	void professorTrocaDeJanela() {
		PredioUnivali p = predio();
		int primeira = p.janelaProfessor();
		assertTrue(p.janelas().get(primeira).acesa());
		boolean sumiu = false, trocou = false;
		for (int i = 0; i < 60 * 40; i++) {
			p.SimulaSe(16);
			if (p.janelaProfessor() == -1) sumiu = true;
			if (p.janelaProfessor() >= 0 && p.janelaProfessor() != primeira) trocou = true;
		}
		assertTrue(sumiu && trocou);
	}

	@Test
	void aplainarDeixaOPisoRetoEARampaSuave() {
		int[][] cm = new int[101][101];
		for (int z = 0; z < 101; z++) for (int x = 0; x < 101; x++) cm[z][x] = 100 + x * 10; // rampa: 1 m a cada 10 m
		GradeColisao.Retangulo r = new GradeColisao.Retangulo(40, 40, 60, 50);
		float piso = PredioUnivali.aplainar(cm, 1, r);
		assertEquals(piso * 100, cm[45][50], 1);
		assertEquals(piso * 100, cm[45][41], 1);
		assertEquals(piso * 100, cm[45][59], 1);
		assertEquals(100 + 90 * 10, cm[45][90], 1, "longe do prédio, nada muda");
		assertTrue(cm[45][70] > piso * 100 && cm[45][70] < 100 + 70 * 10, "na rampa, entre os dois");
	}
}
