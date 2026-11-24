package fase;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import grafos.GradeCaminhada;
import mundo.ConsultaAltura;

class ProblemaLayoutTest {

	private static ConsultaAltura plano() {
		int[][] cm = new int[301][301];
		for (int[] l : cm) java.util.Arrays.fill(l, 100);
		return new ConsultaAltura(cm, 1f, 0.01f);
	}

	private static Layout layout(float ix, float iz, float ux, float uz) {
		return new Layout(new Ponto(ix, iz), new Ponto(ux, uz),
				List.of(new Ponto(100, 70), new Ponto(150, 200), new Ponto(240, 150)),
				List.of(new Ponto(125, 70), new Ponto(150, 230), new Ponto(240, 120)));
	}

	@Test
	void univaliLongeEhMelhorQuePerto() {
		ConsultaAltura c = plano();
		ProblemaLayout p = new ProblemaLayout(new GradeCaminhada(c, 4), c);
		double perto = p.fitness(layout(60, 60, 80, 80));
		double longe = p.fitness(layout(60, 60, 240, 240));
		assertTrue(longe > perto, "longe " + longe + " perto " + perto);
	}

	@Test
	void pontoNaAguaLevaPenalidade() {
		int[][] cm = new int[301][301];
		for (int z = 0; z < 301; z++) for (int x = 0; x < 301; x++) cm[z][x] = x > 200 && z > 200 ? -300 : 100;
		ConsultaAltura c = new ConsultaAltura(cm, 1f, 0.01f);
		ProblemaLayout p = new ProblemaLayout(new GradeCaminhada(c, 4), c);
		assertTrue(p.fitness(layout(60, 60, 250, 250)) < 0.1, "a Univali no lago");
		assertTrue(p.fitness(layout(60, 60, 150, 260)) > 0.5);
	}

	@Test
	void caixaEmCimaDaProvaEhRuim() {
		ConsultaAltura c = plano();
		ProblemaLayout p = new ProblemaLayout(new GradeCaminhada(c, 4), c);
		Layout boa = layout(60, 60, 240, 240);
		Layout ruim = new Layout(boa.inicio(), boa.univali(), boa.provas(), boa.provas());
		assertEquals(1.0, p.termoCaixas(boa), 1e-6);
		assertEquals(0.0, p.termoCaixas(ruim), 1e-6);
	}

	@Test
	void mutacaoECruzamentoMantemPontosCaminhaveis() {
		ConsultaAltura c = plano();
		GradeCaminhada g = new GradeCaminhada(c, 4);
		ProblemaLayout p = new ProblemaLayout(g, c);
		Random r = new Random(1);
		Layout a = p.criarAleatorio(r), b = p.criarAleatorio(r);
		for (int i = 0; i < 200; i++) {
			Layout filho = p.mutar(p.cruzar(a, b, r), r);
			for (int k = 0; k < Layout.PONTOS; k++) {
				assertTrue(g.caminhavelEm(filho.ponto(k).x(), filho.ponto(k).z()));
			}
			a = filho;
		}
	}
}
