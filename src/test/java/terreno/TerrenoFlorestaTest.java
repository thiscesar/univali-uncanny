package terreno;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;

import org.junit.jupiter.api.Test;

/** O perfil FLORESTA sem rodar o AG (rápido): só a conversão para centímetros e a borda. */
class TerrenoFlorestaTest {

	private static Terreno aleatorio(int n, long seed) {
		Random r = new Random(seed);
		float[] a = new float[n * n];
		for (int i = 0; i < a.length; i++) a[i] = 0.2f + r.nextFloat() * 0.6f; // tem água e terra
		return new Terreno(n, n, a);
	}

	@Test
	void bordaDe15MetrosFicaPlanaMeioMetroAcimaDaAgua() {
		PerfilTerreno p = PerfilTerreno.FLORESTA.comTamanho(121);
		int[][] cm = GeradorTerreno.paraAlturas(aleatorio(32, 1), p, new RuidoFractal(1));
		int n = cm.length;
		for (int j = 0; j < n; j++) {
			for (int i = 0; i < n; i++) {
				int d = Math.min(Math.min(i, j), Math.min(n - 1 - i, n - 1 - j));
				if (d <= 15) {
					assertEquals(50, cm[j][i], "ponto (" + i + ", " + j + ") da borda");
				}
			}
		}
	}

	@Test
	void alturasEmCentimetrosTemDetalheMenorQueUmMetro() {
		int[][] cm = GeradorTerreno.paraAlturas(aleatorio(32, 2), PerfilTerreno.FLORESTA.comTamanho(121),
				new RuidoFractal(2));
		boolean temFracao = false;
		for (int[] linha : cm) for (int v : linha) if (v % 100 != 0) temFracao = true;
		assertTrue(temFracao, "em centímetros, as alturas não ficam presas a metros inteiros (sem degraus)");
	}

	@Test
	void maiorLagoContaSoAMaiorRegiaoConectada() {
		float[] a = {
				0.1f, 0.1f, 0.9f, 0.9f,
				0.1f, 0.9f, 0.9f, 0.1f,
				0.9f, 0.9f, 0.9f, 0.1f,
				0.9f, 0.9f, 0.9f, 0.9f };
		Terreno t = new Terreno(4, 4, a);
		// lago de 3 células no canto e outro de 2 à direita: o maior ocupa 3/16 do mapa
		assertEquals(3.0 / 16, t.maiorRegiaoAbaixo(Terreno.NIVEL_AGUA), 1e-9);
	}
}
