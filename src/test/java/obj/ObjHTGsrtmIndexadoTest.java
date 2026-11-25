package obj;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ObjHTGsrtmIndexadoTest {

	/** Os triângulos do ObjHTGsrtm original (cantos por ponto da grade), célula por célula, linha por linha. */
	private static List<String> triangulosDoOriginal(int lado) {
		List<String> t = new ArrayList<>();
		for (int j = 0; j < lado - 1; j++) {
			for (int i = 0; i < lado - 1; i++) {
				int a = j * lado + i, b = a + 1, c = a + lado + 1, d = a + lado;
				t.add(a + "," + b + "," + c);
				t.add(c + "," + d + "," + a);
			}
		}
		return t;
	}

	@Test
	void osIndicesDaoOsMesmosTriangulosDoOriginal() {
		int lado = 13; // 12 x 12 células em blocos de 5: sobram blocos menores na borda
		ObjHTGsrtmIndexado.Indices ind = ObjHTGsrtmIndexado.indices(lado, 5);
		List<String> nossos = new ArrayList<>();
		int[] v = ind.indices();
		for (int k = 0; k < v.length; k += 3) nossos.add(v[k] + "," + v[k + 1] + "," + v[k + 2]);
		List<String> original = triangulosDoOriginal(lado);
		assertEquals(original.size(), nossos.size(), "a mesma quantidade de triângulos");
		assertEquals(Set.copyOf(original), new HashSet<>(nossos), "os mesmos triângulos, com os cantos na mesma ordem");
	}

	@Test
	void osBlocosCobremTudoSemSobrar() {
		ObjHTGsrtmIndexado.Indices ind = ObjHTGsrtmIndexado.indices(301, ObjHTGsrtmIndexado.BLOCO);
		assertEquals(15, ind.blocosPorLado()); // 300 células / 20
		int[] inicio = ind.inicioBloco();
		assertEquals(0, inicio[0]);
		assertEquals(300 * 300 * 6, inicio[inicio.length - 1]);
		for (int b = 0; b + 1 < inicio.length; b++) {
			assertEquals(20 * 20 * 6, inicio[b + 1] - inicio[b], "bloco " + b);
		}
	}

	@Test
	void blocoAtrasOuLongeFicaDeFora() {
		float raio = 14.2f; // meia diagonal de um bloco de 20 m
		// quem olha está em (100, 100), olhando para +x, enxergando até 60 m
		assertTrue(ObjHTGsrtmIndexado.blocoVisivel(130, 100, raio, 100, 100, 1, 0, 60), "na frente");
		assertTrue(ObjHTGsrtmIndexado.blocoVisivel(100, 100, raio, 100, 100, 1, 0, 60), "o bloco onde se está");
		assertTrue(ObjHTGsrtmIndexado.blocoVisivel(90, 100, raio, 100, 100, 1, 0, 60), "pouco atrás: pode aparecer de lado");
		assertFalse(ObjHTGsrtmIndexado.blocoVisivel(60, 100, raio, 100, 100, 1, 0, 60), "bem atrás");
		assertFalse(ObjHTGsrtmIndexado.blocoVisivel(180, 100, raio, 100, 100, 1, 0, 60), "longe demais");
		assertTrue(ObjHTGsrtmIndexado.blocoVisivel(60, 100, raio, 100, 100, 0, 0, 60), "olhando para baixo: só a distância conta");
	}
}
