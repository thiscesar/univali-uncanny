package entidades;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import grafos.PlantaInterna;
import jogo.Regras;
import mundo.GradeColisao;

class DragonKnightTest {

	/** Um corredor de 4 m de largura (z de 3 a 7) e 40 m de comprimento, com três pontas. */
	private static PlantaInterna corredor() {
		GradeColisao colisao = new GradeColisao(100);
		colisao.adicionarRetangulo(0, 0, 40, 3);
		colisao.adicionarRetangulo(0, 7, 40, 10);
		List<float[]> pontas = List.of(new float[] { 36, 5 }, new float[] { 20, 5 }, new float[] { 3, 5 });
		return new PlantaInterna(colisao, new GradeColisao.Retangulo(0, 0, 40, 10), 0.9f, 0.5f, pontas, 1.6f);
	}

	/** O jogador em (x, 5), olhando para (fx, fz). */
	private static DragonKnight acordado(PlantaInterna planta, float x, float fx, float fz) {
		DragonKnight m = new DragonKnight(null, null, 42);
		m.definirAlvo(x, 1.7f, 5, fx, fz);
		m.despertar(planta, 0);
		return m;
	}

	/** Avança a simulação em passos de 16 ms. */
	private static void avancar(DragonKnight m, float segundos) {
		for (int i = 0; i < Math.round(segundos * 1000 / 16); i++) m.SimulaSe(16);
	}

	@Test
	void dormeAteAVoltaComAsProvas() {
		DragonKnight m = new DragonKnight(null, null, 1);
		avancar(m, 5);
		assertEquals(DragonKnight.Estado.ADORMECIDO, m.estado());
		assertFalse(m.visivel());
		assertFalse(m.lutando());
		assertEquals(-1, m.intersectaRaio(new float[] { 0, 1, 0 }, new float[] { 1, 0, 0 }));
	}

	@Test
	void surgeNaPrimeiraPontaProvocaEDepoisAvanca() {
		DragonKnight m = acordado(corredor(), 5, -1, 0); // de costas para ele: não mira
		assertEquals(DragonKnight.Estado.SURGINDO, m.estado());
		assertEquals(36, m.x, 1e-3f);
		avancar(m, Regras.CAVALEIRO_SURGINDO + 0.1f);
		assertEquals(DragonKnight.Estado.AVANCANDO, m.estado());
		avancar(m, 2);
		assertTrue(m.x < 36 - 6, "veio vindo: x = " + m.x);
	}

	@Test
	void avancaEmZigueZagueSemAtravessarParede() {
		PlantaInterna planta = corredor();
		DragonKnight m = acordado(planta, 5, -1, 0);
		float menorZ = Float.MAX_VALUE, maiorZ = -Float.MAX_VALUE;
		for (int i = 0; i < 6 * 60 && m.estado() != DragonKnight.Estado.ATACANDO; i++) {
			m.SimulaSe(16);
			assertTrue(planta.livre(m.x, m.z), "dentro da parede em " + m.x + ", " + m.z);
			if (m.estado() == DragonKnight.Estado.AVANCANDO) {
				menorZ = Math.min(menorZ, m.z);
				maiorZ = Math.max(maiorZ, m.z);
			}
		}
		assertTrue(maiorZ - menorZ > 0.6f, "foi para os lados: de " + menorZ + " a " + maiorZ);
	}

	@Test
	void alcancaOJogadorEAtaca() {
		DragonKnight m = acordado(corredor(), 5, -1, 0);
		avancar(m, 12);
		assertEquals(DragonKnight.Estado.ATACANDO, m.estado());
		assertTrue(m.ataqueTerminou());
	}

	@Test
	void desviaQuandoOJogadorMiraNele() {
		DragonKnight m = acordado(corredor(), 5, 1, 0); // olhando direto para o corredor, para ele
		boolean desviou = false;
		for (int i = 0; i < 8 * 60 && !desviou; i++) {
			m.definirAlvo(5, 1.7f, 5, m.x - 5, m.z - 5); // a mira acompanha ele
			m.SimulaSe(16);
			desviou = m.estado() == DragonKnight.Estado.DESVIANDO;
		}
		assertTrue(desviou, "com a mira em cima, ele pula para o lado");
	}

	@Test
	void umTiroFracoSoMarcaEUmGolpeOFazCambalear() {
		DragonKnight m = acordado(corredor(), 5, -1, 0);
		m.receberDano(Regras.DANO_BAGO * 2);
		assertEquals(DragonKnight.Estado.SURGINDO, m.estado(), "2 bagos não contam");
		assertEquals(0, m.golpes());
		m.receberDano(Regras.DANO_DE_GOLPE);
		assertEquals(DragonKnight.Estado.CAMBALEANDO, m.estado());
		assertEquals(1, m.golpes());
	}

	@Test
	void cambaleandoERecuandoOsTirosNaoContam() {
		DragonKnight m = acordado(corredor(), 5, -1, 0);
		m.receberDano(Regras.DANO_DE_GOLPE);
		assertEquals(1, m.golpes());
		m.receberDano(Regras.DANO_DE_GOLPE); // logo em seguida, ainda cambaleando
		assertEquals(1, m.golpes());
		assertTrue(m.atingivel(), "o tiro acerta (e ele sangra), só não conta");
		for (int i = 0; i < 10 * 60 && m.estado() != DragonKnight.Estado.RECUANDO; i++) m.SimulaSe(16);
		m.receberDano(Regras.DANO_DE_GOLPE);
		assertEquals(1, m.golpes(), "recuando também não conta");
	}

	@Test
	void depoisDoGolpeRecuaSomeESurgePorOutroLado() {
		DragonKnight m = acordado(corredor(), 5, -1, 0);
		avancar(m, 3);
		m.receberDano(Regras.DANO_DE_GOLPE);
		boolean escondeu = false;
		for (int i = 0; i < 15 * 60 && m.estado() != DragonKnight.Estado.SURGINDO; i++) {
			m.SimulaSe(16);
			escondeu |= m.estado() == DragonKnight.Estado.ESCONDIDO;
		}
		assertTrue(escondeu, "sumiu depois de recuar");
		assertEquals(DragonKnight.Estado.SURGINDO, m.estado(), "e voltou");
		assertTrue(m.x == 20 || m.x == 3, "por outra ponta: x = " + m.x);
	}

	@Test
	void tresGolpesDerrubamEOCorpoFica() {
		DragonKnight m = acordado(corredor(), 5, -1, 0);
		for (int golpe = 1; golpe <= Regras.GOLPES_PARA_MATAR; golpe++) {
			for (int i = 0; i < 20 * 60 && !m.vulneravel(); i++) m.SimulaSe(16);
			m.receberDano(Regras.DANO_DE_GOLPE);
			assertEquals(golpe, m.golpes());
		}
		assertEquals(DragonKnight.Estado.MORRENDO, m.estado());
		avancar(m, 2);
		assertEquals(DragonKnight.Estado.MORTO, m.estado());
		assertFalse(m.lutando());
		assertTrue(m.visivel(), "o corpo fica no chão");
		assertEquals(-1, m.intersectaRaio(new float[] { m.x - 5, 1, m.z }, new float[] { 1, 0, 0 }));
	}
}
