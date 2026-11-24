package entidades;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;

import org.junit.jupiter.api.Test;

import jogo.Regras;
import mundo.ConsultaAltura;
import mundo.GradeColisao;

class TiroTest {

	private static ConsultaAltura chao() {
		int[][] cm = new int[201][201];
		for (int[] l : cm) java.util.Arrays.fill(l, 100);
		return new ConsultaAltura(cm, 1f, 0.01f);
	}

	private static final float[] FRENTE = { 1, 0, 0 }, DIREITA = { 0, 0, 1 }, CIMA = { 0, 1, 0 };

	/** Monstro encarando a d metros à frente (+x) de quem atira em (50, 2.7, 100). */
	private static DragonKnight monstroA(float d, GradeColisao grade) {
		DragonKnight m = new DragonKnight(chao(), grade, null, null, 1);
		m.definirAlvo(50 + d, 2.7f, 100, 1, 0); // primeiro, para surgir perto deste ponto...
		boolean surgiu = false;
		for (int i = 0; i < 50 && !surgiu; i++) {
			m.forcarAparicao();
			m.SimulaSe(16);
			surgiu = m.visivel();
		}
		// ...depois, colocado exatamente onde o teste quer, virado para quem atira
		m.x = 50 + d;
		m.z = 100;
		m.y = 1;
		m.guinada = DragonKnight.guinadaPara(-1, 0);
		m.definirAlvo(50, 2.7f, 100, 1, 0);
		return m;
	}

	@Test
	void bagosFicamDentroDoCone() {
		Random r = new Random(1);
		double limite = Math.cos(Math.toRadians(Regras.ESPALHAMENTO_GRAUS)) - 1e-6;
		for (int i = 0; i < 2000; i++) {
			float[] d = Tiro.direcaoBago(FRENTE, DIREITA, CIMA, r);
			assertTrue(d[0] >= limite, "fora do cone: " + d[0]);
			assertEquals(1, Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]), 1e-5);
		}
	}

	@Test
	void dePertoUmTiroMata() {
		int mortes = 0;
		for (int s = 0; s < 20; s++) {
			DragonKnight m = monstroA(4 + DragonKnight.CAIXA_PADRAO[3], new GradeColisao(201));
			Tiro.disparar(new float[] { 50, 1.5f, 100 }, FRENTE, DIREITA, CIMA, m, new GradeColisao(201), chao(), new Random(s));
			if (m.estado() == DragonKnight.Estado.MORRENDO) mortes++;
		}
		assertEquals(20, mortes, "a 4 m da frente do dragão, todo tiro mata");
	}

	@Test
	void deLongeNormalmentePrecisaDeDois() {
		int mortesNoPrimeiro = 0;
		for (int s = 0; s < 40; s++) {
			DragonKnight m = monstroA(14 + DragonKnight.CAIXA_PADRAO[3], new GradeColisao(201));
			Tiro.disparar(new float[] { 50, 1.5f, 100 }, FRENTE, DIREITA, CIMA, m, new GradeColisao(201), chao(), new Random(s));
			if (m.estado() == DragonKnight.Estado.MORRENDO) mortesNoPrimeiro++;
		}
		assertTrue(mortesNoPrimeiro < 20, "a 14 m, menos da metade dos tiros mata sozinho: " + mortesNoPrimeiro);
	}

	@Test
	void troncoNaFrenteSeguraOsBagos() {
		GradeColisao grade = new GradeColisao(201);
		grade.adicionar(53, 100, 1.5f); // tronco largo entre quem atira e o monstro
		DragonKnight m = monstroA(10, grade);
		Tiro.Resultado r = Tiro.disparar(new float[] { 50, 1.5f, 100 }, FRENTE, DIREITA, CIMA, m, grade, chao(),
				new Random(3));
		assertEquals(0, r.acertos());
		assertTrue(r.impactos().stream().allMatch(i -> i.alvo() == Tiro.Alvo.TRONCO));
	}

	@Test
	void atirandoNoChaoOImpactoFicaNoChao() {
		float[] baixo = { 0.6f, -0.8f, 0 };
		Tiro.Resultado r = Tiro.disparar(new float[] { 50, 2.7f, 100 }, baixo, DIREITA, new float[] { 0.8f, 0.6f, 0 },
				null, new GradeColisao(201), chao(), new Random(4));
		assertEquals(Regras.BAGOS, r.impactos().size());
		for (Tiro.Impacto i : r.impactos()) {
			assertEquals(Tiro.Alvo.CHAO, i.alvo());
			assertEquals(1f, i.y(), 1e-4f);
		}
	}
}
