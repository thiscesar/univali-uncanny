package entidades;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import grafos.GradeCaminhada;
import jogo.Regras;
import mundo.ConsultaAltura;
import mundo.GradeColisao;

class JubiladoTest {

	/** Mapa plano de 200 x 200 m (a grade de caminhada deixa 26 m de borda: sobra de 26 a 174). */
	private static ConsultaAltura chao() {
		int[][] a = new int[201][201];
		for (int[] linha : a) java.util.Arrays.fill(linha, 100);
		return new ConsultaAltura(a, 1f, 0.01f);
	}

	private static Jubilado novo(GradeColisao colisao) {
		ConsultaAltura c = chao();
		Jubilado j = new Jubilado(c, colisao, new GradeCaminhada(c, 2), null, null, 7);
		j.x = 100;
		j.z = 100;
		j.guinada = 0; // olhando para +x
		return j;
	}

	private static Jubilado novo() {
		return novo(new GradeColisao(200));
	}

	private static void simular(Jubilado j, float segundos) {
		for (int i = 0; i < Math.round(segundos * 1000 / 16); i++) j.SimulaSe(16);
	}

	@Test
	void vagaDevagarQuandoNaoPercebeNada() {
		Jubilado j = novo();
		j.definirAlvo(30, 1.7f, 30, false, false, false, true); // escondido na Univali: ele só vaga
		float maior = 0;
		for (int i = 0; i < 30 * 60; i++) {
			j.SimulaSe(16);
			maior = Math.max(maior, j.velocidade());
			assertEquals(Jubilado.Estado.VAGANDO, j.estado());
		}
		assertTrue(Math.hypot(j.x - 100, j.z - 100) > 5, "andou pelo mapa");
		assertTrue(maior <= Regras.JUBILADO_VAGANDO + 1e-4f, "sem pressa: " + maior);
	}

	@Test
	void longeEleSeAproximaRapidoEPertoVoltaAVagar() {
		Jubilado j = novo();
		j.definirAlvo(40, 1.7f, 40, false, false, false, true);
		simular(j, Regras.JUBILADO_CARENCIA); // a carência do começo: o jogador ainda está na Univali
		j.x = 100;
		j.z = 100;
		j.definirAlvo(40, 1.7f, 40, false, false, false, false); // a 85 m, parado (nem ouve nem vê)
		simular(j, 10);
		float d = (float) Math.hypot(j.x - 40, j.z - 40);
		assertTrue(d < 85 - 25, "em 10 s chegou bem mais perto que vagando (14 m): está a " + d + " m");
		simular(j, 15);
		d = (float) Math.hypot(j.x - 40, j.z - 40);
		assertTrue(d < Regras.JUBILADO_LONGE + 5, "ronda o jogador: " + d + " m");
	}

	@Test
	void depoisDeUmaCacadaDaUmRespiro() {
		Jubilado j = novo();
		j.definirAlvo(114, 1.7f, 100, false, false, false, false);
		j.SimulaSe(16);
		assertEquals(Jubilado.Estado.CACANDO, j.estado());
		j.definirAlvo(114, 1.7f, 100, false, false, false, true); // sumiu de vista
		simular(j, Regras.JUBILADO_PERDE + 0.5f);
		assertEquals(Jubilado.Estado.PROCURANDO, j.estado());
		assertTrue(j.respiro() > Regras.JUBILADO_RESPIRO - 1);
		// o jogador reaparece longe (sem ser visto nem ouvido): no respiro, ele não vem atrás
		j.definirAlvo(30, 1.7f, 30, false, false, false, false);
		float antes = (float) Math.hypot(j.x - 30, j.z - 30);
		simular(j, 15);
		float depois = (float) Math.hypot(j.x - 30, j.z - 30);
		assertTrue(depois > antes - 15, "não correu atrás: de " + antes + " para " + depois + " m");
	}

	@Test
	void veDeFrenteMasNaoDeCostasESenteDePerto() {
		Jubilado frente = novo();
		frente.definirAlvo(110, 1.7f, 100, false, false, false, false); // 10 m à frente, parado
		frente.SimulaSe(16);
		assertEquals(Jubilado.Estado.CACANDO, frente.estado());

		Jubilado costas = novo();
		costas.definirAlvo(90, 1.7f, 100, false, false, false, false); // 10 m atrás, parado
		costas.SimulaSe(16);
		assertEquals(Jubilado.Estado.VAGANDO, costas.estado());

		Jubilado perto = novo();
		perto.definirAlvo(97, 1.7f, 100, false, false, false, false); // 3 m atrás
		perto.SimulaSe(16);
		assertEquals(Jubilado.Estado.CACANDO, perto.estado(), "de tão perto, sente");
	}

	@Test
	void aLanternaDenunciaDeLonge() {
		Jubilado apagada = novo();
		apagada.definirAlvo(125, 1.7f, 100, false, false, false, false); // 25 m à frente
		apagada.SimulaSe(16);
		assertEquals(Jubilado.Estado.VAGANDO, apagada.estado());

		Jubilado acesa = novo();
		acesa.definirAlvo(125, 1.7f, 100, false, false, true, false);
		acesa.SimulaSe(16);
		assertEquals(Jubilado.Estado.CACANDO, acesa.estado());
	}

	@Test
	void umTroncoNoMeioEsconde() {
		GradeColisao colisao = new GradeColisao(200);
		colisao.adicionar(105, 100, 0.5f);
		Jubilado j = novo(colisao);
		j.definirAlvo(110, 1.7f, 100, false, false, false, false);
		j.SimulaSe(16);
		assertEquals(Jubilado.Estado.VAGANDO, j.estado());
	}

	@Test
	void ouveACorridaDeLongeEOsPassosSoDePerto() {
		Jubilado corrida = novo();
		corrida.definirAlvo(80, 1.7f, 100, true, true, false, false); // 20 m atrás, correndo
		corrida.SimulaSe(16);
		assertEquals(Jubilado.Estado.DESCONFIADO, corrida.estado());
		simular(corrida, 3);
		assertTrue(corrida.x < 97, "foi na direção do barulho: x = " + corrida.x);

		Jubilado andando = novo();
		andando.definirAlvo(80, 1.7f, 100, false, true, false, false); // 20 m atrás, só andando
		andando.SimulaSe(16);
		assertEquals(Jubilado.Estado.VAGANDO, andando.estado());
		andando.definirAlvo(94, 1.7f, 100, false, true, false, false); // 6 m atrás, andando
		andando.SimulaSe(16);
		assertEquals(Jubilado.Estado.DESCONFIADO, andando.estado());
	}

	@Test
	void cacaAlcancaEAtaca() {
		Jubilado j = novo();
		j.definirAlvo(108, 1.7f, 100, false, false, false, false);
		float tempo = 0;
		while (j.estado() != Jubilado.Estado.ATACANDO && tempo < 5) {
			j.SimulaSe(16);
			tempo += 0.016f;
		}
		assertEquals(Jubilado.Estado.ATACANDO, j.estado());
		assertTrue(tempo < 3, "8 m caçando a 5,2 m/s: " + tempo);
		assertFalse(j.ataqueTerminou());
		simular(j, 1);
		assertTrue(j.ataqueTerminou());
	}

	@Test
	void desisteQuandoPerdeOJogadorEDepoisVoltaAVagar() {
		Jubilado j = novo();
		j.definirAlvo(114, 1.7f, 100, false, false, false, false);
		j.SimulaSe(16);
		assertEquals(Jubilado.Estado.CACANDO, j.estado());
		j.definirAlvo(114, 1.7f, 100, false, false, false, true); // entrou na Univali
		simular(j, Regras.JUBILADO_PERDE + 0.5f);
		assertEquals(Jubilado.Estado.PROCURANDO, j.estado());
		simular(j, Regras.JUBILADO_PROCURA + 0.5f);
		assertEquals(Jubilado.Estado.VAGANDO, j.estado());
	}

	@Test
	void doisBagosDerrubamUmSoIrritaEDepoisEleLevanta() {
		Jubilado j = novo();
		j.definirAlvo(60, 1.7f, 100, false, false, false, false); // longe, atrás
		j.receberDano(Regras.DANO_BAGO);
		assertEquals(Jubilado.Estado.CACANDO, j.estado(), "um bago: só fica sabendo onde o jogador está");

		j.receberDano(2 * Regras.DANO_BAGO);
		assertEquals(Jubilado.Estado.CAIDO, j.estado());
		assertFalse(j.atingivel());
		float[] origem = { 90, 1, 100 }, direcao = { 1, 0, 0 };
		assertEquals(-1, j.intersectaRaio(origem, direcao), "caído, os bagos passam por cima");

		simular(j, Regras.JUBILADO_CAIDO + 0.1f);
		assertEquals(Jubilado.Estado.CACANDO, j.estado(), "levanta e vai atrás do jogador");
	}

	@Test
	void oCorpoBloqueiaOsBagos() {
		Jubilado j = novo();
		float[] origem = { 90, 1, 100 };
		assertEquals(9.6f, j.intersectaRaio(origem, new float[] { 1, 0, 0 }), 0.05f);
		assertEquals(-1, j.intersectaRaio(origem, new float[] { 0, 0, 1 }), 0.001f);
	}

	@Test
	void ouveTiroAte70Metros() {
		Jubilado perto = novo();
		perto.definirAlvo(30, 1.7f, 30, false, false, false, false);
		perto.ouvirTiro(150, 100); // 50 m
		assertEquals(Jubilado.Estado.DESCONFIADO, perto.estado());

		Jubilado longe = novo();
		longe.definirAlvo(30, 1.7f, 30, false, false, false, false);
		longe.ouvirTiro(30, 30); // 99 m
		assertEquals(Jubilado.Estado.VAGANDO, longe.estado());
	}

	@Test
	void naoChegaPertoDaUnivali() {
		Jubilado j = novo();
		j.z = 150;
		j.univali = new GradeColisao.Retangulo(90, 90, 110, 110);
		j.guinada = DragonKnight.guinadaPara(0, -1); // olhando para o prédio
		j.definirAlvo(100, 1.7f, 112, false, false, false, false); // o jogador colado na parede, do lado de fora
		float menor = Float.MAX_VALUE;
		for (int i = 0; i < 20 * 60; i++) {
			j.SimulaSe(16);
			menor = Math.min(menor, j.univali.distancia(j.x, j.z));
		}
		assertTrue(menor >= Regras.JUBILADO_LONGE_UNIVALI, "chegou a " + menor + " m do prédio");
	}

	@Test
	void quemRondaNaoCacaMesmoVendoOJogador() {
		Jubilado j = novo();
		j.encerrarCaca();
		j.definirAlvo(120, 1.7f, 100, true, true, false, false); // 20 m à frente, correndo
		j.ouvirTiro(120, 100);
		simular(j, 2);
		assertEquals(Jubilado.Papel.OBSERVADOR, j.papel());
		assertNotEquals(Jubilado.Estado.CACANDO, j.estado());
		assertNotEquals(Jubilado.Estado.DESCONFIADO, j.estado());
	}

	@Test
	void quemRondaRecuaEncarandoSeOJogadorChegaPerto() {
		Jubilado j = novo();
		j.encerrarCaca();
		j.definirAlvo(110, 1.7f, 100, false, true, false, false); // a 10 m, lanterna apagada
		j.SimulaSe(16);
		assertEquals(Jubilado.Estado.RECUANDO, j.estado());
		simular(j, 1.5f);
		assertTrue(j.x < 100, "recuou, para longe do jogador: x = " + j.x);
		assertTrue(j.velocidade() <= Regras.JUBILADO_RECUANDO + 1e-4f, "devagar: " + j.velocidade());
		assertEquals(0, Math.cos(j.guinada) - 1, 0.05, "de frente para o jogador, de costas para onde vai");
		assertFalse(j.foiProvocado(), "a 10 m ainda não é provocação");
	}

	@Test
	void quemRondaRecuaDaLuzDaLanterna() {
		Jubilado apagada = novo();
		apagada.encerrarCaca();
		apagada.definirAlvo(120, 1.7f, 100, false, false, false, false); // a 20 m, olhando para ele, lanterna apagada
		apagada.definirOlhar(-1, 0);
		apagada.SimulaSe(16);
		assertNotEquals(Jubilado.Estado.RECUANDO, apagada.estado());

		Jubilado acesa = novo();
		acesa.encerrarCaca();
		acesa.definirAlvo(120, 1.7f, 100, false, false, true, false);
		acesa.definirOlhar(-1, 0); // a luz bem nele
		acesa.SimulaSe(16);
		assertEquals(Jubilado.Estado.RECUANDO, acesa.estado());

		Jubilado deLado = novo();
		deLado.encerrarCaca();
		deLado.definirAlvo(120, 1.7f, 100, false, false, true, false);
		deLado.definirOlhar(0, 1); // a luz para outro lado
		deLado.SimulaSe(16);
		assertNotEquals(Jubilado.Estado.RECUANDO, deLado.estado());
	}

	@Test
	void aLuzPorUmInstanteNaoProvocaMasInsistirProvoca() {
		Jubilado j = novo();
		j.encerrarCaca();
		j.definirAlvo(120, 1.7f, 100, false, false, true, false);
		j.definirOlhar(-1, 0);
		simular(j, Regras.JUBILADO_PROVOCA * 0.5f);
		j.definirOlhar(0, 1); // tirou a luz dele
		simular(j, 3);
		assertFalse(j.foiProvocado(), "um instante de luz só o faz recuar");
		assertEquals(Jubilado.Estado.VAGANDO, j.estado(), "sem a luz, volta a rondar");

		j.definirOlhar((float) (j.x - 120), (float) (j.z - 100)); // a luz nele de novo, e fica
		simular(j, Regras.JUBILADO_PROVOCA + 0.2f);
		assertTrue(j.foiProvocado());
		assertFalse(j.foiProvocado(), "perguntar apaga o aviso");
	}

	@Test
	void chegarPertoDemaisOuAtirarEmQuemRondaProvoca() {
		Jubilado perto = novo();
		perto.encerrarCaca();
		perto.definirAlvo(100 + Regras.OBSERVA_PROVOCA_PERTO - 1, 1.7f, 100, false, true, false, false);
		perto.SimulaSe(16);
		assertTrue(perto.foiProvocado());

		Jubilado baleado = novo();
		baleado.encerrarCaca();
		baleado.definirAlvo(125, 1.7f, 100, false, false, false, false);
		baleado.receberDano(Regras.DANO_BAGO); // um bago só: não cai
		assertTrue(baleado.foiProvocado());
	}

	@Test
	void caçandoDePertoDaBotesMaisRapidosQueOJogadorCorrendo() {
		Jubilado j = novo();
		float px = 108; // o jogador a 8 m, fugindo em linha reta a 6 m/s
		j.definirAlvo(px, 1.7f, 100, true, true, false, false);
		j.SimulaSe(16);
		assertEquals(Jubilado.Estado.CACANDO, j.estado());
		float maior = 0, depois = 0;
		for (int i = 0; i < 4 * 60; i++) {
			px += 6 * 0.016f;
			j.definirAlvo(px, 1.7f, 100, true, true, false, false);
			j.SimulaSe(16);
			if (i < (Regras.INVESTIDA_DURA + 0.1f) * 60) maior = Math.max(maior, j.velocidade());
			if (i > (Regras.INVESTIDA_DURA + 0.6f) * 60 && i < (Regras.INVESTIDA_DURA + Regras.INVESTIDA_ESPERA) * 60) {
				depois = Math.max(depois, j.velocidade());
			}
		}
		assertTrue(maior > 6.5f, "no bote passa do jogador correndo: " + maior);
		assertTrue(depois <= Regras.JUBILADO_CACANDO + 1e-3f, "depois do bote, a corrida normal: " + depois);
	}

	@Test
	void enfurecidoViraCacadorJaCacando() {
		Jubilado j = novo();
		j.encerrarCaca();
		j.definirAlvo(115, 1.7f, 100, false, false, true, false);
		j.enfurecer();
		assertEquals(Jubilado.Papel.CACADOR, j.papel());
		assertEquals(Jubilado.Estado.CACANDO, j.estado());
	}

	@Test
	void quemRondaFicaNoAnelEmVoltaDoJogador() {
		Jubilado j = novo();
		j.encerrarCaca();
		j.definirAlvo(100, 1.7f, 150, false, false, false, false); // a 50 m, parado
		float soma = 0;
		int amostras = 0;
		for (int i = 0; i < 60 * 60; i++) {
			j.SimulaSe(16);
			if (i > 20 * 60 && i % 60 == 0) {
				soma += (float) Math.hypot(j.x - 100, j.z - 150);
				amostras++;
			}
		}
		float media = soma / amostras;
		assertTrue(media > Regras.OBSERVA_RECUA && media < Regras.OBSERVA_LONGE + 12, "distância média " + media);
	}

	@Test
	void caidoQuemRondaLevantaFugindoEOCacadorLevantaCacando() {
		Jubilado observador = novo();
		observador.encerrarCaca();
		observador.definirAlvo(60, 1.7f, 100, false, false, false, false);
		observador.receberDano(3 * Regras.DANO_BAGO);
		assertEquals(Jubilado.Estado.CAIDO, observador.estado());
		simular(observador, Regras.JUBILADO_CAIDO + 0.1f);
		assertEquals(Jubilado.Estado.FUGINDO, observador.estado());

		Jubilado cacador = novo();
		cacador.encerrarCaca();
		cacador.liberarCaca();
		cacador.definirAlvo(108, 1.7f, 100, false, false, false, false);
		cacador.SimulaSe(16);
		assertEquals(Jubilado.Estado.CACANDO, cacador.estado());
	}

	@Test
	void farejandoSussurraQuandoChegaPerto() {
		Jubilado j = novo();
		int[] sussurros = { 0 };
		j.definirEventos(new Jubilado.Eventos() {
			@Override
			public void sussurrou(Jubilado quem) {
				sussurros[0]++;
			}
		});
		j.encerrarCaca();
		j.definirAlvo(100, 1.7f, 160, false, false, false, false); // a 60 m, parado e quieto
		j.liberarCaca(); // a vez dele: vem farejando
		simular(j, 2);
		assertEquals(0, sussurros[0], "longe ainda não sussurra");
		simular(j, 12);
		assertTrue(sussurros[0] >= 1, "chegando perto, sussurra");
	}

	@Test
	void pausadoFicaParado() {
		Jubilado j = novo();
		j.pausado = true;
		j.definirAlvo(108, 1.7f, 100, false, false, false, false);
		simular(j, 3);
		assertEquals(100, j.x, 1e-4f);
		assertEquals(Jubilado.Estado.VAGANDO, j.estado());
	}

	@Test
	void comecaLongeDoJogador() {
		Jubilado j = novo();
		j.posicionarLongeDe(60, 60);
		assertTrue(Math.hypot(j.x - 60, j.z - 60) >= Regras.JUBILADO_INICIO);
	}
}
