package ag;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * Testa o motor do algoritmo genético com um problema de brinquedo, o "OneMax":
 * o indivíduo é uma sequência de bits e o fitness é a quantidade de bits ligados.
 * A resposta ótima é conhecida (todos ligados), então dá para conferir se o AG chega nela.
 */
class AlgoritmoGeneticoTest {

	/** O problema de brinquedo. Cruzar e mutar sempre criam vetores novos, sem mexer nos pais. */
	static class ProblemaUns implements Problema<boolean[]> {
		private final int tamanho;

		ProblemaUns(int tamanho) {
			this.tamanho = tamanho;
		}

		@Override
		public boolean[] criarAleatorio(Random random) {
			boolean[] bits = new boolean[tamanho];
			for (int i = 0; i < tamanho; i++) {
				bits[i] = random.nextBoolean();
			}
			return bits;
		}

		@Override
		public double fitness(boolean[] bits) {
			return contarUns(bits);
		}

		@Override
		public boolean[] cruzar(boolean[] pai1, boolean[] pai2, Random random) {
			int corte = random.nextInt(tamanho);
			boolean[] filho = new boolean[tamanho];
			for (int i = 0; i < tamanho; i++) {
				filho[i] = i < corte ? pai1[i] : pai2[i];
			}
			return filho;
		}

		@Override
		public boolean[] mutar(boolean[] bits, Random random) {
			boolean[] copia = bits.clone();
			int i = random.nextInt(tamanho);
			copia[i] = !copia[i];
			return copia;
		}
	}

	static int contarUns(boolean[] bits) {
		int conta = 0;
		for (boolean b : bits) {
			if (b) conta++;
		}
		return conta;
	}

	@Test
	void encontraASolucaoOtima() {
		AlgoritmoGenetico<boolean[]> ag = new AlgoritmoGenetico<>(
				new ProblemaUns(20), new Configuracao(30, 100, 0.5, 3), new Random(42));

		boolean[] melhor = ag.executar((geracao, individuo, fitness) -> {});

		assertEquals(20, contarUns(melhor));
	}

	@Test
	void mesmaSeedDaOMesmoResultado() {
		Configuracao config = new Configuracao(10, 5, 0.5, 3);

		boolean[] a = new AlgoritmoGenetico<>(new ProblemaUns(40), config, new Random(7))
				.executar((geracao, individuo, fitness) -> {});
		boolean[] b = new AlgoritmoGenetico<>(new ProblemaUns(40), config, new Random(7))
				.executar((geracao, individuo, fitness) -> {});

		assertArrayEquals(a, b);
	}

	@Test
	void avaliarEmParaleloDaOMesmoResultadoQueEmSequencia() {
		Configuracao config = new Configuracao(30, 40, 0.5, 3);
		List<Double> sequencia = new ArrayList<>(), paralelo = new ArrayList<>();
		boolean[] a = new AlgoritmoGenetico<>(new ProblemaUns(60), config, new Random(11))
				.executar((geracao, individuo, fitness) -> sequencia.add(fitness));
		ProblemaUns emParalelo = new ProblemaUns(60) {
			@Override
			public boolean avaliarEmParalelo() {
				return true;
			}
		};
		boolean[] b = new AlgoritmoGenetico<>(emParalelo, config, new Random(11))
				.executar((geracao, individuo, fitness) -> paralelo.add(fitness));

		assertArrayEquals(a, b);
		assertEquals(sequencia, paralelo, "geração a geração, o mesmo melhor");
	}

	@Test
	void elitismoNuncaDeixaOMelhorPiorar() {
		List<Double> historico = new ArrayList<>();
		new AlgoritmoGenetico<>(new ProblemaUns(30), new Configuracao(20, 50, 0.5, 3), new Random(1))
				.executar((geracao, individuo, fitness) -> historico.add(fitness));

		// o ouvinte é chamado na população inicial (geração 0) e em cada uma das 50 gerações
		assertEquals(51, historico.size());
		for (int i = 1; i < historico.size(); i++) {
			assertTrue(historico.get(i) >= historico.get(i - 1),
					"o fitness caiu na geração " + i);
		}
	}
}
