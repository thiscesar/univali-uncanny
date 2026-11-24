package mundo;

import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import fase.Ponto;

class RegioesTest {

	@Test
	void asQuatroRegioesAparecemEmManchas() {
		Regioes r = new Regioes(5);
		Map<Regioes.Tipo, Integer> conta = new EnumMap<>(Regioes.Tipo.class);
		int trocas = 0;
		Regioes.Tipo anterior = null;
		for (int x = 0; x < 300; x += 2) {
			for (int z = 0; z < 300; z += 10) {
				Regioes.Tipo t = r.em(x, z);
				conta.merge(t, 1, Integer::sum);
			}
			Regioes.Tipo t = r.em(x, 150);
			if (anterior != null && t != anterior) trocas++;
			anterior = t;
		}
		assertEquals(4, conta.size(), "regiões que apareceram: " + conta);
		assertTrue(trocas < 15, "manchas grandes, não ruído: " + trocas + " trocas em 300 m");
	}

	@Test
	void pesosSomamUmEVenceAMaior() {
		Regioes r = new Regioes(9);
		float[] p = r.pesos(123, 45);
		float soma = 0;
		for (float v : p) soma += v;
		assertEquals(1f, soma, 1e-4f);
		int maior = 0;
		for (int i = 1; i < p.length; i++) if (p[i] > p[maior]) maior = i;
		assertEquals(r.em(123, 45).ordinal(), maior);
	}

	@Test
	void mapaDeDetalhesMarcaATrilha() {
		List<List<Ponto>> trilhas = List.of(List.of(new Ponto(50, 100), new Ponto(250, 100)));
		byte[] rgb = MapaDetalhes.gerar(300, trilhas, null, 1);
		int lado = MapaDetalhes.LADO;
		int naTrilha = (Math.round(100f / 300 * lado) * lado + lado / 2) * 3;
		int longe = (Math.round(200f / 300 * lado) * lado + lado / 2) * 3;
		assertTrue((rgb[naTrilha] & 0xFF) > 200, "trilha: " + (rgb[naTrilha] & 0xFF));
		assertEquals(0, rgb[longe] & 0xFF);
	}
}
