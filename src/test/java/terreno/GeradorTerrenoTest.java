package terreno;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class GeradorTerrenoTest {

	/**
	 * A mesma seed precisa dar o mesmo mapa, mesmo gerada em threads diferentes ao mesmo
	 * tempo (como no carregamento em segundo plano). As duas gerações rodam em paralelo
	 * para o teste levar o tempo de uma só.
	 */
	@Test
	void mesmaSeedGeraOMesmoTerrenoEmThreadsDiferentes() {
		AtomicInteger contagem = new AtomicInteger();
		AtomicInteger maior = new AtomicInteger();
		CompletableFuture<int[][]> a = CompletableFuture.supplyAsync(() -> GeradorTerreno.gerar(777, 32));
		CompletableFuture<int[][]> b = CompletableFuture.supplyAsync(() -> GeradorTerreno.gerar(777, 32, (g, t, f) -> {
			contagem.incrementAndGet();
			maior.set(g);
		}));
		assertArrayEquals(a.join(), b.join());

		// o ouvinte (que alimenta a barra de progresso) vê todas as gerações e chega a 100%
		assertEquals(GeradorTerreno.GERACOES, maior.get());
		assertEquals(GeradorTerreno.GERACOES + 1, contagem.get(), "geração 0 (população inicial) + todas as outras");
	}

	@Test
	void ouvinteRecebeTodasAsGeracoes() {
		AtomicInteger ultima = new AtomicInteger(-1);
		// cancela logo para o teste ser rápido, mas confere que as gerações chegam em ordem
		assertThrows(CancellationException.class, () -> GeradorTerreno.gerar(1, 16, (g, t, f) -> {
			assertEquals(ultima.get() + 1, g);
			ultima.set(g);
			if (g == 5) throw new CancellationException("teste");
		}));
		assertEquals(5, ultima.get());
	}

	/**
	 * O AG do relevo passou a usar os vários núcleos (as linhas do cruzamento e do morro, e o
	 * fitness dos filhos). O mapa precisa sair igual ao de antes, bit a bit: este número é o das
	 * alturas geradas pela versão anterior, de um núcleo só, com a mesma seed.
	 */
	@Test
	void oAGEmParaleloDaOMesmoMapaDaVersaoDeUmNucleo() {
		ProblemaTerreno problema = new ProblemaTerreno(48, 0.11, 0.0, 0.008, 0.02);
		Terreno t = new ag.AlgoritmoGenetico<>(problema, new ag.Configuracao(16, 25, 0.5, 3), new java.util.Random(2026))
				.executar((geracao, terreno, fitness) -> {});
		assertEquals(1124850324, java.util.Arrays.hashCode(t.copiarAlturas()));
	}
}
