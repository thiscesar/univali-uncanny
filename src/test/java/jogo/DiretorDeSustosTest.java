package jogo;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import entidades.DragonKnight;
import entidades.Jubilado;
import grafos.GradeCaminhada;
import jogo.DiretorDeSustos.Vez;
import mundo.ConsultaAltura;
import mundo.GradeColisao;

class DiretorDeSustosTest {

	/** Um mapa plano de 200 x 200 m, o Dragon Knight e três Jubilados, com o jogador parado no meio. */
	private static final class Cena {
		final ConsultaAltura chao;
		final GradeColisao colisao = new GradeColisao(200);
		final DragonKnight dragon;
		final List<Jubilado> jubilados = new ArrayList<>();
		final DiretorDeSustos diretor;
		float jx = 100, jz = 100;
		boolean escondido = false;

		Cena(long semente) {
			int[][] a = new int[201][201];
			for (int[] linha : a) java.util.Arrays.fill(linha, 100);
			chao = new ConsultaAltura(a, 1f, 0.01f);
			GradeCaminhada grade = new GradeCaminhada(chao, 2);
			dragon = new DragonKnight(chao, colisao, null, null, semente);
			float[][] lugares = { { 40, 40 }, { 160, 50 }, { 60, 160 } };
			for (int i = 0; i < 3; i++) {
				Jubilado j = new Jubilado(chao, colisao, grade, null, null, semente + i);
				j.x = lugares[i][0];
				j.z = lugares[i][1];
				jubilados.add(j);
			}
			diretor = new DiretorDeSustos(dragon, jubilados, new Random(semente));
		}

		/** Um quadro de 50 ms, como na TelaJogo: o diretor primeiro, depois os monstros. */
		void quadro(int provas) {
			diretor.atualizar(0.05f, provas, jx, jz, true);
			dragon.definirAlvo(jx, 1.7f, jz, 1, 0);
			dragon.SimulaSe(50);
			for (Jubilado j : jubilados) {
				j.definirAlvo(jx, 1.7f, jz, false, true, true, escondido);
				j.pausado = dragon.estado() != DragonKnight.Estado.AUSENTE;
				j.SimulaSe(50);
			}
		}

		boolean jubiladoAmeacando() {
			for (Jubilado j : jubilados) {
				Jubilado.Estado e = j.estado();
				if (e == Jubilado.Estado.CACANDO || e == Jubilado.Estado.ATACANDO) return true;
			}
			return false;
		}
	}

	@Test
	void aPrimeiraCalmariaEAcarenciaETodosSoRondam() {
		Cena c = new Cena(1);
		for (int i = 0; i < (Regras.CARENCIA - 1) * 20; i++) c.quadro(0);
		assertEquals(Vez.CALMARIA, c.diretor.vez());
		assertEquals(DragonKnight.Estado.AUSENTE, c.dragon.estado());
		for (Jubilado j : c.jubilados) assertEquals(Jubilado.Papel.OBSERVADOR, j.papel());
		for (int i = 0; i < 2 * 20; i++) c.quadro(0);
		assertNotEquals(Vez.CALMARIA, c.diretor.vez(), "a carência acabou: vem a primeira ameaça");
	}

	@Test
	void osDoisNuncaAtacamJuntosENuncaTresVezesAMesmaAmeaca() {
		Cena c = new Cena(7);
		List<Vez> vezes = new ArrayList<>();
		Vez antes = Vez.CALMARIA;
		for (int i = 0; i < 15 * 60 * 20; i++) { // 15 minutos
			c.quadro(1);
			assertFalse(c.dragon.estado() != DragonKnight.Estado.AUSENTE && c.jubiladoAmeacando(), "os dois juntos");
			// o jogador se defende: mata o Dragon Knight na contagem e derruba o Jubilado de perto
			if (c.dragon.estado() == DragonKnight.Estado.ENCARANDO && c.dragon.contagem() < 4) c.dragon.matar();
			for (Jubilado j : c.jubilados) {
				if (j.estado() == Jubilado.Estado.CACANDO && Math.hypot(j.x - c.jx, j.z - c.jz) < 8) j.receberDano(4.5f);
			}
			if (c.diretor.vez() != antes && c.diretor.vez() != Vez.CALMARIA) vezes.add(c.diretor.vez());
			antes = c.diretor.vez();
		}
		assertTrue(vezes.size() >= 8, "ameaças em 15 min: " + vezes);
		assertTrue(vezes.contains(Vez.JUBILADO) && vezes.contains(Vez.DRAGON_KNIGHT), vezes.toString());
		for (int i = 2; i < vezes.size(); i++) {
			assertFalse(vezes.get(i) == vezes.get(i - 1) && vezes.get(i) == vezes.get(i - 2), "três seguidas: " + vezes);
		}
	}

	@Test
	void oDragonKnightComecaRaroEFicaMaisComumComAsProvas() {
		int[] dragon = new int[4];
		Cena c = new Cena(11);
		for (int provas = 0; provas < 4; provas++) {
			for (int t = 0; t < 2000; t++) {
				if (c.diretor.sortear(provas) == Vez.DRAGON_KNIGHT) dragon[provas]++;
			}
		}
		assertEquals(Regras.CHANCE_DRAGON_KNIGHT[0], dragon[0] / 2000f, 0.04f);
		assertEquals(Regras.CHANCE_DRAGON_KNIGHT[3], dragon[3] / 2000f, 0.04f);
		assertTrue(dragon[0] < dragon[3]);
	}

	@Test
	void aVezDoJubiladoAcabaSeEleNaoAcharOJogador() {
		Cena c = new Cena(3);
		c.escondido = true; // o jogador está na Univali: ninguém o acha
		c.diretor.forcar(Vez.JUBILADO, c.jx, c.jz);
		Jubilado cacador = c.diretor.cacador();
		assertNotNull(cacador);
		assertEquals(Jubilado.Papel.CACADOR, cacador.papel());
		for (int i = 0; i < (Regras.VEZ_JUBILADO + 1) * 20; i++) c.quadro(0);
		assertEquals(Vez.CALMARIA, c.diretor.vez());
		assertEquals(Jubilado.Papel.OBSERVADOR, cacador.papel());
	}

	@Test
	void derrubadoDuasVezesOJubiladoDesiste() {
		Cena c = new Cena(9);
		c.diretor.forcar(Vez.JUBILADO, c.jx, c.jz);
		Jubilado cacador = c.diretor.cacador();
		cacador.receberDano(4.5f);
		c.quadro(0);
		assertEquals(Vez.JUBILADO, c.diretor.vez(), "uma queda: ainda é a vez dele");
		for (int i = 0; i < (Regras.JUBILADO_CAIDO + 0.5f) * 20; i++) c.quadro(0);
		assertEquals(Jubilado.Estado.CACANDO, cacador.estado(), "levantou caçando");
		cacador.receberDano(4.5f);
		c.quadro(0);
		assertEquals(Vez.CALMARIA, c.diretor.vez(), "a segunda queda acaba a vez");
		for (int i = 0; i < (Regras.JUBILADO_CAIDO + 0.5f) * 20; i++) c.quadro(0);
		assertEquals(Jubilado.Estado.FUGINDO, cacador.estado(), "levanta e foge");
	}

	@Test
	void pegarUmaProvaEncurtaACalmaria() {
		Cena c = new Cena(5);
		c.quadro(0);
		assertTrue(c.diretor.restante() > Regras.AMEACA_DEPOIS_DA_PROVA);
		c.diretor.provaColetada();
		assertTrue(c.diretor.restante() <= Regras.AMEACA_DEPOIS_DA_PROVA);
	}
}
