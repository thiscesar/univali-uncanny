package entidades;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import mundo.ConsultaAltura;
import mundo.GradeColisao;

class JogadorTest {

	/** Mapa plano de 100 x 100 m, 1 m entre pontos, a 1 m de altura (alturas em centímetros). */
	private static int[][] plano(int alturaCm) {
		int[][] a = new int[101][101];
		for (int[] linha : a) java.util.Arrays.fill(linha, alturaCm);
		return a;
	}

	private static ConsultaAltura chao(int[][] cm) {
		return new ConsultaAltura(cm, 1f, 0.01f);
	}

	/** Roda a simulação por "segundos", em quadros de 16 ms. Devolve os passos dados. */
	private static int simular(Jogador j, float segundos) {
		int passos = 0;
		int quadros = Math.round(segundos * 1000 / 16);
		for (int i = 0; i < quadros; i++) {
			j.SimulaSe(16);
			passos += j.passosNoQuadro();
		}
		return passos;
	}

	@Test
	void andaParaFrenteNaVelocidadeCertaComAceleracaoSuave() {
		ConsultaAltura c = chao(plano(100));
		Jogador j = new Jogador(50, 80, c, new GradeColisao(100));
		j.comandar(1, 0, false);
		j.SimulaSe(16);
		assertTrue(j.velocidade() < 1.0, "não sai já na velocidade máxima");
		simular(j, 2);
		assertEquals(Jogador.VELOCIDADE_ANDANDO, j.velocidade(), 0.05);
		assertTrue(j.z < 80, "com guinada 0, frente é -z");
		assertEquals(50, j.x, 1e-3);
	}

	@Test
	void freiaAosPoucosAoSoltarAsTeclas() {
		Jogador j = new Jogador(50, 80, chao(plano(100)), new GradeColisao(100));
		j.comandar(1, 0, false);
		simular(j, 1);
		j.comandar(0, 0, false);
		j.SimulaSe(16);
		assertTrue(j.velocidade() > 0.5, "não para instantaneamente");
		simular(j, 1);
		assertEquals(0, j.velocidade(), 0.01);
	}

	@Test
	void diagonalNaoEMaisRapida() {
		Jogador j = new Jogador(50, 80, chao(plano(100)), new GradeColisao(100));
		j.comandar(1, 1, false);
		simular(j, 2);
		assertEquals(Jogador.VELOCIDADE_ANDANDO, j.velocidade(), 0.05);
	}

	@Test
	void correSemLimiteComEstaminaInfinita() {
		ConsultaAltura c = chao(plano(100));
		Jogador j = new Jogador(50, 90, c, new GradeColisao(100));
		j.girar((float) Math.PI / 2, 0);
		j.comandar(1, 0, true);
		simular(j, 20); // bem mais que os 5 s da estamina antiga (bate na borda, mas segue "correndo")
		assertTrue(j.correndo());
		assertEquals(1, j.estamina(), 0.001);
		assertFalse(j.ofegante());
	}

	@Test
	void correrGastaEstaminaEDepoisElaVolta() {
		Jogador.estaminaInfinita = false; // a regra antiga continua no código: testa ela também
		try {
			correrGastaEstaminaEDepoisElaVoltaSemInfinita();
		} finally {
			Jogador.estaminaInfinita = true;
		}
	}

	private void correrGastaEstaminaEDepoisElaVoltaSemInfinita() {
		ConsultaAltura c = chao(plano(100));
		Jogador j = new Jogador(50, 90, c, new GradeColisao(100));
		j.girar((float) Math.PI / 2, 0); // olha para +x: espaço para correr
		j.comandar(1, 0, true);
		j.SimulaSe(16);
		simular(j, 1);
		assertTrue(j.correndo());
		assertEquals(Jogador.VELOCIDADE_CORRENDO, j.velocidade(), 0.1);

		float correu = 1.016f;
		while (j.estamina() > 0 && correu < 10) { // corre até esvaziar
			j.SimulaSe(16);
			correu += 0.016f;
		}
		assertEquals(Jogador.DURACAO_ESTAMINA, correu, 0.05, "5 s de corrida");
		assertTrue(j.ofegante());
		j.SimulaSe(16);
		assertFalse(j.correndo(), "sem estamina, não corre mais, mesmo segurando o Shift");

		// a partir daqui não corre: a recuperação só começa depois de 1 s
		simular(j, 0.9f);
		assertEquals(0, j.estamina(), 0.01, "espera 1 s antes de recuperar");
		assertEquals(Jogador.VELOCIDADE_ANDANDO, j.velocidade(), 0.15, "anda em vez de correr");
		j.comandar(0, 0, false);
		simular(j, 8.2f);
		assertEquals(1, j.estamina(), 0.01, "recupera em 8 s");
		assertFalse(j.ofegante());
	}

	@Test
	void naoEntraEmAguaFundaMasEntraNaRasa() {
		int[][] cm = plano(100);
		for (int z = 0; z <= 100; z++) {
			for (int x = 60; x <= 100; x++) cm[z][x] = -100;  // lago fundo a partir de x = 60
			for (int x = 40; x < 50; x++) cm[z][x] = -20;     // poça rasa entre 40 e 50
		}
		ConsultaAltura c = chao(cm);
		Jogador j = new Jogador(30, 50, c, new GradeColisao(100));
		j.girar((float) Math.PI / 2, 0); // anda para +x
		j.comandar(1, 0, false);
		simular(j, 12);
		assertTrue(j.x > 50, "atravessou a poça rasa");
		assertTrue(j.x < 60, "parou antes do lago fundo");
		assertTrue(c.profundidadeAgua(j.x, j.z) <= Jogador.PROFUNDIDADE_MAXIMA);
	}

	@Test
	void deslizaAoLongoDaMargemEmVezDeParar() {
		int[][] cm = plano(100);
		for (int z = 0; z <= 100; z++) for (int x = 60; x <= 100; x++) cm[z][x] = -100;
		Jogador j = new Jogador(55, 50, chao(cm), new GradeColisao(100));
		j.girar((float) Math.toRadians(45), 0); // na diagonal, contra a margem
		j.comandar(1, 0, false);
		simular(j, 2);
		assertTrue(j.z < 46, "continua andando em z ao longo da margem");
		assertTrue(j.x < 60);
	}

	@Test
	void naoSobeBarrancoMasDesce() {
		int[][] cm = plano(0);
		for (int z = 0; z <= 100; z++) for (int x = 50; x <= 100; x++) cm[z][x] = (x - 50) * 150; // 56°
		ConsultaAltura c = chao(cm);
		Jogador subindo = new Jogador(40, 50, c, new GradeColisao(100));
		subindo.girar((float) Math.PI / 2, 0);
		subindo.comandar(1, 0, false);
		simular(subindo, 6);
		assertTrue(subindo.x < 51, "não sobe o barranco de 56 graus");

		Jogador descendo = new Jogador(55, 50, c, new GradeColisao(100));
		descendo.girar((float) -Math.PI / 2, 0); // anda para -x, morro abaixo
		descendo.comandar(1, 0, false);
		simular(descendo, 3);
		assertTrue(descendo.x < 50, "desce sem problema");
	}

	@Test
	void ficaA10MetrosDaBorda() {
		Jogador j = new Jogador(50, 50, chao(plano(100)), new GradeColisao(100));
		j.comandar(1, 0, true);
		simular(j, 20);
		assertEquals(Jogador.MARGEM_BORDA, j.z, 0.01);
	}

	@Test
	void desviaDeTroncoSemAtravessar() {
		GradeColisao g = new GradeColisao(100);
		g.adicionar(50.1f, 45, 0.35f); // tronco logo à frente, um pouco para o lado
		Jogador j = new Jogador(50, 50, chao(plano(100)), g);
		j.comandar(1, 0, false);
		for (int i = 0; i < 180; i++) {
			j.SimulaSe(16);
			float d = (float) Math.hypot(j.x - 50.1f, j.z - 45);
			assertTrue(d >= 0.35f + Jogador.RAIO - 0.01f, "entrou no tronco no quadro " + i);
		}
		assertTrue(j.z < 44, "passou pelo tronco deslizando");
	}

	@Test
	void olhosA170DoChaoSemSolavanco() {
		int[][] cm = plano(0);
		for (int z = 0; z <= 100; z++) for (int x = 0; x <= 100; x++) cm[z][x] = (100 - z) * 10; // sobe 10 cm por m
		ConsultaAltura c = chao(cm);
		Jogador j = new Jogador(50, 80, c, new GradeColisao(100));
		j.comandar(1, 0, false);
		float alturaAntes = j.camera().y;
		for (int i = 0; i < 120; i++) {
			j.SimulaSe(16);
			assertTrue(Math.abs(j.camera().y - alturaAntes) < 0.1f, "solavanco no quadro " + i);
			alturaAntes = j.camera().y;
		}
		j.comandar(0, 0, false);
		simular(j, 2);
		assertEquals(c.alturaEm(j.x, j.z) + Jogador.ALTURA_OLHOS, j.camera().y, 0.02);
	}

	@Test
	void umPassoACada1Metro40EUmRitmoDeCaminhada() {
		Jogador j = new Jogador(50, 90, chao(plano(100)), new GradeColisao(100));
		j.comandar(1, 0, false);
		int passos = simular(j, 4);
		float andou = 90 - j.z;
		assertEquals(andou / Jogador.PASSO_ANDANDO, passos, 1.5);
		float porSegundo = passos / 4f;
		assertTrue(porSegundo > 2 && porSegundo < 3, "passos por segundo: " + porSegundo);
	}

	@Test
	void paradoNaoDaPassosNemBalanca() {
		Jogador j = new Jogador(50, 50, chao(plano(100)), new GradeColisao(100));
		assertEquals(0, simular(j, 2));
		assertEquals(1 + Jogador.ALTURA_OLHOS, j.camera().y, 1e-4);
	}

	@Test
	void olharParaCimaNaoPassaDe85Graus() {
		Jogador j = new Jogador(50, 50, chao(plano(100)), new GradeColisao(100));
		j.girar(0, (float) Math.toRadians(200));
		assertEquals(85, Math.toDegrees(j.camera().arfagem), 1e-3);
	}

	@Test
	void pontoDeInicioFogeDaAguaEDaBorda() {
		int[][] cm = plano(100);
		for (int z = 40; z <= 60; z++) for (int x = 40; x <= 60; x++) cm[z][x] = -200; // lago no centro
		ConsultaAltura c = chao(cm);
		float[] p = Jogador.pontoDeInicio(c, new GradeColisao(100), 50, 50);
		assertFalse(c.ehAgua(p[0], p[1]));
		assertTrue(c.dentroDoMapa(p[0], p[1], Jogador.MARGEM_BORDA));
	}
}
