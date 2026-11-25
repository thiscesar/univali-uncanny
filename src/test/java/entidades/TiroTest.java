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

	/** Monstro surgindo a d metros à frente (+x) de quem atira em (50, 2.7, 100), de frente para ele. */
	private static DragonKnight monstroA(float d, GradeColisao grade) {
		DragonKnight m = new DragonKnight(null, null, 1);
		m.definirAlvo(50, 2.7f, 100, 1, 0);
		var planta = new grafos.PlantaInterna(grade, new GradeColisao.Retangulo(0, 0, 200, 200), 0.9f, 2f,
				java.util.List.of(new float[] { 50 + d, 100 }), 2.6f);
		m.despertar(planta, 1);
		// colocado exatamente onde o teste quer, virado para quem atira
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
	void dePertoTodoTiroEUmGolpe() {
		for (int s = 0; s < 20; s++) {
			DragonKnight m = monstroA(4 + DragonKnight.CAIXA_PADRAO[3], new GradeColisao(201));
			Tiro.disparar(new float[] { 50, 1.5f, 100 }, FRENTE, DIREITA, CIMA, m, new GradeColisao(201), chao(), new Random(s));
			assertEquals(1, m.golpes(), "a 4 m da frente do dragão, todo tiro é um golpe");
			assertEquals(DragonKnight.Estado.CAMBALEANDO, m.estado());
		}
	}

	@Test
	void deLongeMenosBagosAcertam() {
		int perto = 0, longe = 0;
		for (int s = 0; s < 40; s++) {
			GradeColisao g = new GradeColisao(201);
			perto += Tiro.disparar(new float[] { 50, 1.5f, 100 }, FRENTE, DIREITA, CIMA,
					monstroA(4 + DragonKnight.CAIXA_PADRAO[3], g), g, chao(), new Random(s)).acertos();
			longe += Tiro.disparar(new float[] { 50, 1.5f, 100 }, FRENTE, DIREITA, CIMA,
					monstroA(30 + DragonKnight.CAIXA_PADRAO[3], g), g, chao(), new Random(s)).acertos();
		}
		assertTrue(longe < perto * 0.8f, "de longe o cone abre: " + longe + " contra " + perto + " bagos");
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
				(DragonKnight) null, new GradeColisao(201), chao(), new Random(4));
		assertEquals(Regras.BAGOS, r.impactos().size());
		for (Tiro.Impacto i : r.impactos()) {
			assertEquals(Tiro.Alvo.CHAO, i.alvo());
			assertEquals(1f, i.y(), 1e-4f);
		}
	}
}
