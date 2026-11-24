package entidades;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import fase.AndarUnivali;
import fase.Ponto;
import mundo.GradeColisao;

class PredioUnivaliTest {

	/** A Univali perto da borda oeste de um mapa de 300 m: a frente olha para o leste (o centro). */
	private static PredioUnivali predio() {
		Ponto u = new Ponto(60, 150);
		return new PredioUnivali(u.x(), 2, u.z(), PredioUnivali.giroParaOCentro(u, 300), 1);
	}

	@Test
	void frenteViradaParaOCentroESaidaNaFrenteDaPorta() {
		PredioUnivali p = predio();
		assertEquals(1, p.frenteX(), 1e-6);
		assertEquals(0, p.frenteZ(), 1e-6);
		Ponto saida = PredioUnivali.saida(new Ponto(60, 150), p.giro);
		assertEquals(60 + PredioUnivali.PROFUNDIDADE / 2 + 6, saida.x(), 1e-4);
		assertFalse(p.dentro(saida.x(), saida.z()));
	}

	@Test
	void retanguloEParedesGiramJunto() {
		PredioUnivali p = predio();
		GradeColisao.Retangulo r = p.retangulo();
		// virado para o leste, a largura (48 m) fica no eixo z
		assertEquals(PredioUnivali.PROFUNDIDADE, r.xMax() - r.xMin(), 1e-4);
		assertEquals(PredioUnivali.LARGURA, r.zMax() - r.zMin(), 1e-4);
		for (GradeColisao.Retangulo w : p.paredes()) {
			assertTrue(w.xMin() >= r.xMin() - 1e-3 && w.xMax() <= r.xMax() + 1e-3 && w.zMin() >= r.zMin() - 1e-3
					&& w.zMax() <= r.zMax() + 1e-3, "parede fora do prédio");
		}
		GradeColisao.Retangulo porta = p.portaEntrada();
		assertEquals(r.xMax(), porta.xMax(), 1e-3); // a porta fica na parede da frente
	}

	@Test
	void comecaDentroLongeDoProfessorEComACadeiraNaSalaDele() {
		PredioUnivali p = predio();
		float[] i = p.inicioJogador();
		assertTrue(p.dentro(i[0], i[2]));
		GradeColisao g = new GradeColisao(300);
		for (GradeColisao.Retangulo w : p.paredes()) g.adicionarRetangulo(w);
		assertFalse(g.colide(i[0], i[2], 0.35f), "o jogador não começa dentro de uma parede");
		float[] prof = p.professorNoModelo();
		AndarUnivali.Sala sala = p.andar.professores();
		assertTrue(prof[0] > sala.xMin() && prof[0] < sala.xMax() && prof[1] > sala.zMin() && prof[1] < sala.zMax(),
				"o professor fica na sala dele");
	}

	/**
	 * Andando a partir do início (com a porta do professor aberta e a da frente trancada), dá para
	 * chegar perto do crachá, do professor e dentro de toda sala que não está trancada. Busca em
	 * largura numa grade de 25 cm dentro do prédio, com o raio do jogador.
	 */
	@Test
	void doInicioChegaEmTudoQueImporta() {
		for (long seed = 1; seed <= 15; seed++) {
			PredioUnivali p = new PredioUnivali(150, 0, 150, 0, seed); // giro 0: o modelo é o mundo deslocado
			GradeColisao g = new GradeColisao(300);
			for (GradeColisao.Retangulo w : p.paredes()) g.adicionarRetangulo(w);
			g.adicionarRetangulo(p.portaEntrada());
			float passo = 0.25f, x0 = 150 - PredioUnivali.LARGURA / 2, z0 = 150 - PredioUnivali.PROFUNDIDADE / 2;
			int nx = (int) (PredioUnivali.LARGURA / passo), nz = (int) (PredioUnivali.PROFUNDIDADE / passo);
			boolean[][] visto = new boolean[nx][nz];
			float[] i = p.inicioJogador();
			java.util.ArrayDeque<int[]> fila = new java.util.ArrayDeque<>();
			int ix = (int) ((i[0] - x0) / passo), iz = (int) ((i[2] - z0) / passo);
			visto[ix][iz] = true;
			fila.add(new int[] { ix, iz });
			while (!fila.isEmpty()) {
				int[] c = fila.poll();
				for (int[] d : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) {
					int a = c[0] + d[0], b = c[1] + d[1];
					if (a < 0 || b < 0 || a >= nx || b >= nz || visto[a][b]) continue;
					if (g.colide(x0 + (a + 0.5f) * passo, z0 + (b + 0.5f) * passo, 0.3f)) continue;
					visto[a][b] = true;
					fila.add(new int[] { a, b });
				}
			}
			java.util.function.BiPredicate<float[], Float> alcanca = (alvo, raio) -> {
				for (int a = 0; a < nx; a++) for (int b = 0; b < nz; b++) {
					if (!visto[a][b]) continue;
					float dx = x0 + (a + 0.5f) * passo - (150 + alvo[0]), dz = z0 + (b + 0.5f) * passo - (150 + alvo[1]);
					if (dx * dx + dz * dz <= raio * raio) return true;
				}
				return false;
			};
			assertTrue(alcanca.test(p.chaveNoModelo(), PredioUnivali.ALCANCE_USO - 0.3f), "seed " + seed + ": crachá");
			assertTrue(alcanca.test(p.professorNoModelo(), PredioUnivali.ALCANCE_USO - 0.3f), "seed " + seed + ": professor");
			assertTrue(alcanca.test(p.centroPortaEntrada(), PredioUnivali.ALCANCE_USO - 0.3f), "seed " + seed + ": saída");
			for (AndarUnivali.Sala s : p.andar.salas()) {
				if (s.trancada() && s.tipo() != AndarUnivali.Tipo.PROFESSORES) {
					assertFalse(alcanca.test(s.centro(), 1f), "seed " + seed + ": " + s.nome() + " devia estar fechada");
				} else {
					float[] dentro = { s.porta()[0], s.porta()[1] + (s.naFrente() ? 1.5f : -1.5f) };
					assertTrue(alcanca.test(dentro, 0.5f), "seed " + seed + ": " + s.nome());
				}
			}
		}
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
	void vultoTrocaDeJanela() {
		PredioUnivali p = predio();
		int primeira = p.janelaVulto();
		assertTrue(p.janelas().get(primeira).acesa());
		boolean sumiu = false, trocou = false;
		for (int i = 0; i < 60 * 40; i++) {
			p.SimulaSe(16);
			if (p.janelaVulto() == -1) sumiu = true;
			if (p.janelaVulto() >= 0 && p.janelaVulto() != primeira) trocou = true;
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
