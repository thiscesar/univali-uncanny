package entidades;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import jogo.Regras;
import mundo.ConsultaAltura;
import mundo.GradeColisao;

class DragonKnightTest {

	/** Mapa plano de 101 x 101 m a 1 m de altura; o jogador no meio, olhando para +x. */
	private static ConsultaAltura chao() {
		int[][] cm = new int[101][101];
		for (int[] linha : cm) java.util.Arrays.fill(linha, 100);
		return new ConsultaAltura(cm, 1f, 0.01f);
	}

	private static DragonKnight monstro(GradeColisao grade) {
		DragonKnight m = new DragonKnight(chao(), grade, null, null, 42);
		m.definirAlvo(50, 2.7f, 50, 1, 0);
		return m;
	}

	/** Avança a simulação em passos de 16 ms. */
	private static void avancar(DragonKnight m, float segundos) {
		for (int i = 0; i < Math.round(segundos * 1000 / 16); i++) {
			m.SimulaSe(16);
		}
	}

	@Test
	void respeitaACarenciaESurgeEntre8E15Metros() {
		DragonKnight m = monstro(new GradeColisao(101));
		avancar(m, Regras.CARENCIA - 1);
		assertEquals(DragonKnight.Estado.AUSENTE, m.estado());
		avancar(m, 1.1f);
		assertEquals(DragonKnight.Estado.SURGINDO, m.estado());
		float d = (float) Math.hypot(m.x - 50, m.z - 50);
		assertTrue(d >= 8 && d <= 15, "distância " + d);
		assertEquals(1f, m.y, 1e-4f); // em cima do chão
	}

	@Test
	void comALanternaLigadaACarenciaAcabaMaisCedo() {
		DragonKnight m = monstro(new GradeColisao(101));
		m.lanternaLigada = true;
		avancar(m, Regras.CARENCIA / Regras.LANTERNA_ATRAI + 0.1f);
		assertEquals(DragonKnight.Estado.SURGINDO, m.estado(), "a luz atrai");
	}

	@Test
	void surgeVirandoParaOJogador() {
		DragonKnight m = monstro(new GradeColisao(101));
		m.forcarAparicao();
		m.SimulaSe(16);
		// a frente do modelo (+x) girada pela guinada aponta para o jogador
		float fx = (float) Math.cos(m.guinada), fz = (float) -Math.sin(m.guinada);
		float dx = 50 - m.x, dz = 50 - m.z, t = (float) Math.hypot(dx, dz);
		assertEquals(1f, (fx * dx + fz * dz) / t, 1e-4f);
	}

	@Test
	void semDanoAContagemAcabaNoAtaqueNaFrenteDoJogador() {
		DragonKnight m = monstro(new GradeColisao(101));
		List<String> eventos = new ArrayList<>();
		m.definirEventos(new DragonKnight.Eventos() {
			@Override public void surgiu(DragonKnight d) { eventos.add("surgiu"); }
			@Override public void atacou(DragonKnight d) { eventos.add("atacou"); }
		});
		m.forcarAparicao();
		avancar(m, Regras.DURACAO_SURGIMENTO + 0.05f);
		assertEquals(DragonKnight.Estado.ENCARANDO, m.estado());
		avancar(m, Regras.TEMPO_CONTAGEM);
		assertEquals(DragonKnight.Estado.ATACANDO, m.estado());
		assertEquals(List.of("surgiu", "atacou"), eventos);
		// a frente da caixa fica a 1,5 m dos olhos, na direção em que o jogador olha (+x)
		assertEquals(50 + Regras.DISTANCIA_ATAQUE + DragonKnight.CAIXA_PADRAO[3], m.x, 1e-3f);
		assertEquals(50, m.z, 1e-3f);
		avancar(m, 1f);
		assertTrue(m.ataqueTerminou());
	}

	@Test
	void danoNaoParaAContagemEMorrerContaUmaAparicao() {
		DragonKnight m = monstro(new GradeColisao(101));
		m.forcarAparicao();
		avancar(m, Regras.DURACAO_SURGIMENTO + 0.05f);
		float antes = m.contagem();
		m.receberDano(4);
		assertTrue(m.ferido());
		assertEquals(1f, m.marcaDano());
		avancar(m, 0.5f);
		assertEquals(antes - 0.5f, m.contagem(), 0.05f);
		assertFalse(m.ferido());
		m.receberDano(Regras.VIDA_MONSTRO);
		assertEquals(DragonKnight.Estado.MORRENDO, m.estado());
		assertEquals(1, m.aparicoesSobrevividas());
		assertEquals(-1, m.intersectaRaio(new float[] { 50, 1.5f, 50 }, new float[] { 1, 0, 0 }));
	}

	@Test
	void depoisDaMorteSomeEVoltaNoIntervalo() {
		DragonKnight m = monstro(new GradeColisao(101));
		m.forcarAparicao();
		avancar(m, 1);
		m.matar();
		avancar(m, 1.0f + Regras.ESPERA_DEPOIS_DA_MORTE + 0.1f); // morte (substituto) + 2 s
		assertEquals(DragonKnight.Estado.AUSENTE, m.estado());
		float intervalo = m.cronometro();
		float minimo = Regras.INTERVALOS[0] * (1 - Regras.VARIACAO_INTERVALO);
		float maximo = Regras.INTERVALOS[0] * (1 + Regras.VARIACAO_INTERVALO);
		assertTrue(intervalo >= minimo - 0.1f && intervalo <= maximo, "intervalo " + intervalo);
	}

	@Test
	void intervaloDiminuiComAsProvas() {
		assertEquals(60f, Regras.intervaloAparicao(0, 0.5f), 1e-4f);
		assertEquals(15f, Regras.intervaloAparicao(3, 0.5f), 1e-4f);
		assertEquals(48f, Regras.intervaloAparicao(0, 0f), 1e-4f);
		assertEquals(18f, Regras.intervaloAparicao(9, 1f), 1e-4f);
	}

	@Test
	void naoSurgeAtrasDeTroncoNaAguaNemNaZonaSegura() {
		GradeColisao grade = new GradeColisao(101);
		grade.adicionar(55, 50, 1f);
		DragonKnight m = monstro(grade);
		assertFalse(m.lugarServe(60, 50), "tronco no caminho");
		assertTrue(m.lugarServe(40, 50));
		m.zonaSegura = new GradeColisao.Retangulo(30, 45, 34, 55);
		assertFalse(m.lugarServe(40, 50), "perto da Univali");
		assertFalse(m.lugarServe(5, 50), "na borda");
	}

	@Test
	void semLugarTentaDeNovoEm2Segundos() {
		GradeColisao grade = new GradeColisao(101);
		// um anel de troncos em volta do jogador: nenhuma linha livre
		for (int i = 0; i < 64; i++) {
			double a = i * Math.PI * 2 / 64;
			grade.adicionar(50 + (float) Math.cos(a) * 3, 50 + (float) Math.sin(a) * 3, 0.4f);
		}
		DragonKnight m = monstro(grade);
		m.forcarAparicao();
		m.SimulaSe(16);
		assertEquals(DragonKnight.Estado.AUSENTE, m.estado());
		assertEquals(Regras.ESPERA_SEM_LUGAR, m.cronometro(), 0.02f);
	}

	@Test
	void tiroAcertaOCorpoInteiroDoDragao() {
		DragonKnight m = monstro(new GradeColisao(101));
		m.forcarAparicao();
		m.SimulaSe(16);
		float[] olhos = { 50, 2.7f, 50 };
		float dx = m.x - 50, dz = m.z - 50, t = (float) Math.hypot(dx, dz);
		// mirando no centro: acerta a frente da caixa, 2 m (o focinho) antes do centro
		float d = m.intersectaRaio(new float[] { 50, 1.2f, 50 }, new float[] { dx / t, 0, dz / t });
		assertEquals(t - DragonKnight.CAIXA_PADRAO[3], d, 1e-3f);
		// mirando 1 m ao lado do centro (ainda no corpo largo do dragão): acerta
		float lx = -dz / t, lz = dx / t;
		float[] dir = normalizar(dx + lx * 1f, 0, dz + lz * 1f);
		assertTrue(m.intersectaRaio(new float[] { 50, 1.2f, 50 }, dir) > 0);
		// mirando para cima, por cima da cabeça do cavaleiro: erra
		assertEquals(-1, m.intersectaRaio(olhos, normalizar(dx, 3 * t, dz)));
		// de costas para ele: erra
		assertEquals(-1, m.intersectaRaio(olhos, new float[] { -dx / t, 0, -dz / t }));
	}

	@Test
	void raioCaixaComecandoDentroDevolveZero() {
		float[] caixa = { -1, -1, -1, 1, 1, 1 };
		assertEquals(0f, DragonKnight.raioCaixa(new float[] { 0, 0, 0 }, new float[] { 1, 0, 0 }, caixa));
		assertEquals(4f, DragonKnight.raioCaixa(new float[] { -5, 0, 0 }, new float[] { 1, 0, 0 }, caixa), 1e-5f);
		assertEquals(-1f, DragonKnight.raioCaixa(new float[] { -5, 2, 0 }, new float[] { 1, 0, 0 }, caixa));
	}

	@Test
	void provaColetadaApressaAProximaAparicao() {
		DragonKnight m = monstro(new GradeColisao(101));
		m.apressarProximaAparicao(Regras.APARICAO_DEPOIS_DA_PROVA);
		assertEquals(Regras.APARICAO_DEPOIS_DA_PROVA, m.cronometro(), 1e-4f, "a carência de 45 s vira 15 s");
		// em cena: vale para a próxima aparição, depois que ele morrer e sumir
		m.forcarAparicao();
		avancar(m, 1);
		m.apressarProximaAparicao(5);
		m.matar();
		avancar(m, 1.0f + Regras.ESPERA_DEPOIS_DA_MORTE + 0.1f);
		assertEquals(DragonKnight.Estado.AUSENTE, m.estado());
		assertTrue(m.cronometro() <= 5f, "cronômetro " + m.cronometro());
	}

	@Test
	void comMaisProvasOIntervaloEncolhe() {
		DragonKnight m = monstro(new GradeColisao(101));
		m.provasColetadas = 3;
		m.forcarAparicao();
		avancar(m, 1);
		m.matar();
		avancar(m, 1.0f + Regras.ESPERA_DEPOIS_DA_MORTE + 0.1f);
		assertTrue(m.cronometro() <= Regras.INTERVALOS[3] * (1 + Regras.VARIACAO_INTERVALO), "cronômetro " + m.cronometro());
	}

	private static float[] normalizar(float x, float y, float z) {
		float t = (float) Math.sqrt(x * x + y * y + z * z);
		return new float[] { x / t, y / t, z / t };
	}
}
