package jogo;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import entidades.Jubilado;
import grafos.GradeCaminhada;
import jogo.DiretorDeSustos.Vez;
import mundo.ConsultaAltura;
import mundo.GradeColisao;

class DiretorDeSustosTest {

	/** Um mapa plano de 200 x 200 m e quatro Jubilados, com o jogador parado no meio. */
	private static final class Cena {
		final ConsultaAltura chao;
		final GradeColisao colisao = new GradeColisao(200);
		final List<Jubilado> jubilados = new ArrayList<>();
		final DiretorDeSustos diretor;
		float jx = 100, jz = 100;
		boolean escondido = false;

		Cena(long semente) {
			int[][] a = new int[201][201];
			for (int[] linha : a) java.util.Arrays.fill(linha, 100);
			chao = new ConsultaAltura(a, 1f, 0.01f);
			GradeCaminhada grade = new GradeCaminhada(chao, 2);
			float[][] lugares = { { 40, 40 }, { 160, 50 }, { 60, 160 }, { 150, 150 } };
			for (int i = 0; i < lugares.length; i++) {
				Jubilado j = new Jubilado(chao, colisao, grade, null, null, semente + i);
				j.x = lugares[i][0];
				j.z = lugares[i][1];
				jubilados.add(j);
			}
			diretor = new DiretorDeSustos(jubilados, new Random(semente));
		}

		/** Um quadro de 50 ms, como na TelaJogo: o diretor primeiro, depois os Jubilados. */
		void quadro(int provas) {
			diretor.atualizar(0.05f, provas, jx, jz, true);
			for (Jubilado j : jubilados) {
				j.definirAlvo(jx, 1.7f, jz, false, true, true, escondido);
				j.SimulaSe(50);
			}
		}

		int cacando() {
			int n = 0;
			for (Jubilado j : jubilados) {
				if (j.papel() == Jubilado.Papel.CACADOR) n++;
			}
			return n;
		}
	}

	@Test
	void aPrimeiraCalmariaEAcarenciaETodosSoRondam() {
		Cena c = new Cena(1);
		for (int i = 0; i < (Regras.CARENCIA - 1) * 20; i++) c.quadro(0);
		assertEquals(Vez.CALMARIA, c.diretor.vez());
		assertEquals(0, c.cacando());
		for (int i = 0; i < 2 * 20; i++) c.quadro(0);
		assertEquals(Vez.CACADA, c.diretor.vez(), "a carência acabou: vem a primeira caçada");
		assertEquals(1, c.cacando(), "sem provas, um caçador só");
	}

	@Test
	void caçadasEcalmariasSeAlternamENoMaximoDoisCacam() {
		Cena c = new Cena(7);
		int trocas = 0;
		Vez antes = Vez.CALMARIA;
		for (int i = 0; i < 15 * 60 * 20; i++) { // 15 minutos
			c.quadro(2);
			assertTrue(c.cacando() <= 2, "no máximo dois caçando");
			if (c.diretor.vez() == Vez.CALMARIA) assertEquals(0, c.cacando(), "na calmaria ninguém caça");
			// o jogador se defende: derruba quem caça a menos de 8 m
			for (Jubilado j : c.jubilados) {
				if (j.estado() == Jubilado.Estado.CACANDO && Math.hypot(j.x - c.jx, j.z - c.jz) < 8) j.receberDano(4.5f);
			}
			if (c.diretor.vez() != antes) trocas++;
			antes = c.diretor.vez();
		}
		assertTrue(c.diretor.cacadas() >= 8, "caçadas em 15 min: " + c.diretor.cacadas());
		assertTrue(trocas >= 2 * c.diretor.cacadas() - 1, "toda caçada termina em calmaria");
		assertTrue(c.diretor.sobrevividas() > 0);
	}

	@Test
	void doisCacadoresSoComProvasEMaisComMaisProvas() {
		Cena c = new Cena(11);
		int[] dois = new int[4];
		for (int provas = 0; provas < 4; provas++) {
			for (int t = 0; t < 2000; t++) {
				if (c.diretor.sortearQuantos(provas) == 2) dois[provas]++;
			}
		}
		assertEquals(0, dois[0], "sem provas, nunca dois");
		assertEquals(Regras.CHANCE_DOIS_CACADORES[3], dois[3] / 2000f, 0.04f);
		assertTrue(dois[1] < dois[3]);
	}

	@Test
	void aCacadaAcabaSeNinguemAcharOJogador() {
		Cena c = new Cena(3);
		c.escondido = true; // o jogador está na Univali: ninguém o acha
		c.diretor.forcar(0, c.jx, c.jz);
		assertEquals(Vez.CACADA, c.diretor.vez());
		Jubilado cacador = c.diretor.cacadores().get(0);
		assertEquals(Jubilado.Papel.CACADOR, cacador.papel());
		for (int i = 0; i < (Regras.VEZ_JUBILADO + 1) * 20; i++) c.quadro(0);
		assertEquals(Vez.CALMARIA, c.diretor.vez());
		assertEquals(Jubilado.Papel.OBSERVADOR, cacador.papel());
		assertEquals(0, c.diretor.sobrevividas(), "ninguém achou o jogador: não conta como sobrevivida");
	}

	@Test
	void derrubadoDuasVezesOCacadorDesiste() {
		Cena c = new Cena(9);
		c.diretor.forcar(0, c.jx, c.jz);
		Jubilado cacador = c.diretor.cacadores().get(0);
		cacador.receberDano(4.5f);
		c.quadro(0);
		assertEquals(Vez.CACADA, c.diretor.vez(), "uma queda: a caçada continua");
		for (int i = 0; i < (Regras.JUBILADO_CAIDO + 0.5f) * 20; i++) c.quadro(0);
		assertEquals(Jubilado.Estado.CACANDO, cacador.estado(), "levantou caçando");
		cacador.receberDano(4.5f);
		c.quadro(0);
		assertEquals(Vez.CALMARIA, c.diretor.vez(), "a segunda queda acaba a caçada");
		assertEquals(1, c.diretor.sobrevividas());
		for (int i = 0; i < (Regras.JUBILADO_CAIDO + 0.5f) * 20; i++) c.quadro(0);
		assertEquals(Jubilado.Estado.FUGINDO, cacador.estado(), "levanta e foge");
	}

	@Test
	void provocarQuemRondaComecaUmaCacadaComEleOuOJuntaAOutra() {
		Cena c = new Cena(4);
		Jubilado provocado = c.jubilados.get(0);
		provocado.x = c.jx + 4; // o jogador chegou perto demais
		provocado.z = c.jz;
		c.quadro(0); // ele percebe
		c.quadro(0); // o diretor fica sabendo
		assertEquals(Vez.CACADA, c.diretor.vez(), "a calmaria acabou na hora");
		assertEquals(List.of(provocado), c.diretor.cacadores());
		assertEquals(Jubilado.Estado.CACANDO, provocado.estado());

		Cena d = new Cena(5);
		d.diretor.forcar(0, d.jx, d.jz);
		Jubilado outro = d.jubilados.stream().filter(j -> !d.diretor.cacadores().contains(j)).findFirst().get();
		outro.x = d.jx - 4;
		outro.z = d.jz;
		d.quadro(0);
		d.quadro(0);
		assertTrue(d.diretor.cacadores().contains(outro), "entrou na caçada que já estava acontecendo");
		assertEquals(2, d.diretor.cacadores().size());
	}

	@Test
	void pegarUmaProvaEncurtaACalmariaEAVoltaEncerraTudo() {
		Cena c = new Cena(5);
		c.quadro(0);
		assertTrue(c.diretor.restante() > Regras.AMEACA_DEPOIS_DA_PROVA);
		c.diretor.provaColetada();
		assertTrue(c.diretor.restante() <= Regras.AMEACA_DEPOIS_DA_PROVA);
		c.diretor.forcar(3, c.jx, c.jz);
		c.diretor.encerrar();
		assertEquals(Vez.CALMARIA, c.diretor.vez());
		assertEquals(0, c.cacando());
	}
}
