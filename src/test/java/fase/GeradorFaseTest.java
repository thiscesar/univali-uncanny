package fase;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import grafos.AEstrela;
import grafos.GradeCaminhada;
import jogo.Regras;
import mundo.ConsultaAltura;
import terreno.RuidoFractal;

class GeradorFaseTest {

	/** Um terreno de 300 m com morros e lagos, feito de ruído fractal (bem mais rápido que o AG do terreno). */
	static ConsultaAltura terreno(long seed) {
		RuidoFractal r = new RuidoFractal(seed);
		int[][] cm = new int[301][301];
		for (int z = 0; z < 301; z++) {
			for (int x = 0; x < 301; x++) {
				double h = r.fbm(x / 70.0, z / 70.0, 4) * 900 + 250; // de uns -6 a +11 m
				cm[z][x] = (int) h;
			}
		}
		return new ConsultaAltura(cm, 1f, 0.01f);
	}

	@Test
	void vinteSeedsTodasCompletaveis() {
		for (long seed = 1; seed <= 20; seed++) {
			ConsultaAltura chao = terreno(seed);
			GeradorFase.Fase f = GeradorFase.gerar(chao, seed, null);
			Layout l = f.layout();
			GradeCaminhada fina = new GradeCaminhada(chao, 2);
			for (int i = 1; i < Layout.PONTOS; i++) {
				Ponto p = l.ponto(i);
				assertTrue(fina.ligados(l.inicio().x(), l.inicio().z(), p.x(), p.z()), "seed " + seed + ": ponto " + i + " sem caminho");
				assertNotNull(AEstrela.buscar(fina, l.inicio().x(), l.inicio().z(), p.x(), p.z(), Regras.CUSTO_INCLINACAO));
			}
			assertTrue(f.fitness() > 0.5, "seed " + seed + ": fitness " + f.fitness());
			assertTrue(f.trilhas().size() >= 4, "seed " + seed + ": trilhas " + f.trilhas().size());
		}
	}

	@Test
	void provasEspalhadasELongeDaUnivali() {
		ConsultaAltura chao = terreno(42);
		GeradorFase.Fase f = GeradorFase.gerar(chao, 42, null);
		Layout l = f.layout();
		for (int a = 0; a < 3; a++) {
			assertTrue(l.provas().get(a).distancia(l.univali()) > 40, "prova " + a + " perto da Univali");
			for (int b = a + 1; b < 3; b++) {
				assertTrue(l.provas().get(a).distancia(l.provas().get(b)) > 40);
			}
		}
	}

	@Test
	void trilhasSaemDaFrenteDaUnivaliESemAtravessarOPredio() {
		ConsultaAltura chao = terreno(8);
		GeradorFase.Fase f = GeradorFase.gerar(chao, 8, null);
		mundo.GradeColisao.Retangulo predio = new entidades.PredioUnivali(f.layout().univali().x(), 0,
				f.layout().univali().z(), f.giroPredio(), 8).retangulo();
		for (int i = 0; i < 3; i++) {
			List<Ponto> t = f.trilhas().get(i);
			assertTrue(t.get(0).distancia(f.saida()) < 3, "a trilha da prova " + i + " começa na saída");
			for (Ponto p : t) assertTrue(predio.distancia(p.x(), p.z()) > 1, "trilha dentro do prédio");
		}
	}

	@Test
	void mesmaSeedMesmaFase() {
		ConsultaAltura chao = terreno(7);
		assertEquals(GeradorFase.gerar(chao, 7, null).layout(), GeradorFase.gerar(chao, 7, null).layout());
	}

	@Test
	void placasDeTrilhaApontamParaOndeATrilhaSegue() {
		ConsultaAltura chao = terreno(3);
		GeradorFase.Fase f = GeradorFase.gerar(chao, 3, null);
		long eventos = f.placas().stream().filter(Placa::evento).count();
		assertTrue(eventos >= 1 && eventos <= 3, "placas do evento: " + eventos);
		for (Placa p : f.placas()) {
			assertFalse(chao.ehAgua(p.x(), p.z()));
			if (p.evento()) continue;
			// a seta (o +x do modelo girado) aponta para mais perto da prova do que a placa está
			Ponto prova = f.layout().provas().get(Integer.parseInt(p.texto().substring(1)) - 1);
			float sx = (float) Math.cos(p.giro()), sz = (float) -Math.sin(p.giro());
			Ponto adiante = new Ponto(p.x() + sx * 5, p.z() + sz * 5);
			assertTrue(adiante.distancia(prova) < new Ponto(p.x(), p.z()).distancia(prova) + 2.5f, "placa de " + p.texto());
		}
	}

	@Test
	void chaikinSuavizaSemMexerNasPontas() {
		List<Ponto> degrau = List.of(new Ponto(0, 0), new Ponto(2, 0), new Ponto(2, 2), new Ponto(4, 2));
		List<Ponto> s = GeradorFase.suavizar(degrau);
		assertEquals(new Ponto(0, 0), s.get(0));
		assertEquals(new Ponto(4, 2), s.get(s.size() - 1));
		assertEquals(2 + 2 * 3, s.size());
		assertFalse(s.contains(new Ponto(2, 0)), "o canto foi arredondado");
	}

	@Test
	void cortarFimTiraOsUltimosMetros() {
		List<Ponto> linha = List.of(new Ponto(0, 0), new Ponto(10, 0), new Ponto(20, 0), new Ponto(30, 0));
		List<Ponto> cortada = GeradorFase.cortarFim(linha, 12);
		assertEquals(10f, GeradorFase.comprimento(cortada), 1e-4f);
	}
}
