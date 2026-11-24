package mundo;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Onde ficam as provas e a Univali, sorteado a partir da seed (a mesma seed dá os mesmos
 * lugares). É provisório: depois, quem decide é o AG da fase, procurando um bom percurso.
 *
 * Roda na thread de carregamento, depois do relevo e ANTES das árvores: cada lugar pede uma
 * clareira sem árvores em volta. Só dados (dá para testar).
 */
public final class SorteioLugares {

	/** Distância mínima entre as provas, e entre cada prova e o início do jogador. */
	public static final float DISTANCIA_ENTRE_PROVAS = 60f;
	/** As provas ficam fora da faixa da muralha de árvores da borda. */
	public static final float MARGEM_PROVAS = 30f;
	/** A Univali: longe do início e da borda. */
	public static final float DISTANCIA_UNIVALI_INICIO = 150f;
	public static final float MARGEM_UNIVALI = 40f;
	/** Uma prova não fica tão perto da Univali (a clareira do prédio). */
	public static final float DISTANCIA_PROVA_UNIVALI = 40f;
	public static final float DECLIVE_MAXIMO_PROVA = 25f;

	/** Uma placa: do evento (Computer on the Beach) ou de trilha (seta pintada com o nome de uma prova). */
	public record Placa(boolean evento, float x, float z, float giro, String texto) {}

	public static final int PLACAS_EVENTO = 3, PLACAS_TRILHA_POR_PROVA = 2;
	/** A que distância da prova ficam as placas de trilha: perto o bastante para ajudar, longe para não entregar. */
	public static final float TRILHA_MINIMA = 25f, TRILHA_MAXIMA = 45f;

	private SorteioLugares() {}

	/**
	 * As placas. Três do evento: uma a uns 10 m do início, virada para o jogador (o clima logo
	 * de cara), e duas longe dela e uma da outra, viradas para um lado qualquer. Duas de trilha
	 * por prova, a 25–45 m dela, com a seta apontando para a prova.
	 *
	 * O giro é a rotação em volta do eixo vertical: a placa do evento mostra a frente (+z) na
	 * direção (sen g, cos g); a seta da placa de trilha aponta para o +x do modelo, que girado
	 * fica em (cos g, -sen g).
	 */
	public static List<Placa> placas(ConsultaAltura chao, float[] inicio, List<float[]> provas, Random random) {
		List<Placa> placas = new ArrayList<>();
		float t = chao.tamanho();

		// a do início
		for (int tentativa = 0; tentativa < 200; tentativa++) {
			double a = random.nextDouble() * 2 * Math.PI;
			float x = inicio[0] + 10 * (float) Math.cos(a), z = inicio[1] + 10 * (float) Math.sin(a);
			if (!bomParaPlaca(chao, x, z, 20)) continue;
			placas.add(new Placa(true, x, z, (float) Math.atan2(inicio[0] - x, inicio[1] - z), "evento"));
			break;
		}
		// as outras do evento
		for (int tentativa = 0; tentativa < 3000 && placas.size() < PLACAS_EVENTO; tentativa++) {
			float x = 35 + random.nextFloat() * (t - 70), z = 35 + random.nextFloat() * (t - 70);
			if (!bomParaPlaca(chao, x, z, 20) || pertoDeOutra(placas, x, z, 70)) continue;
			if (distancia(x, z, inicio[0], inicio[1]) < 50) continue;
			placas.add(new Placa(true, x, z, random.nextFloat() * 6.283f, "evento"));
		}
		// as de trilha: em volta de cada prova, apontando para ela
		for (int i = 0; i < provas.size(); i++) {
			float[] p = provas.get(i);
			int feitas = 0;
			for (int tentativa = 0; tentativa < 500 && feitas < PLACAS_TRILHA_POR_PROVA; tentativa++) {
				double a = random.nextDouble() * 2 * Math.PI;
				float d = TRILHA_MINIMA + random.nextFloat() * (TRILHA_MAXIMA - TRILHA_MINIMA);
				float x = p[0] + d * (float) Math.cos(a), z = p[1] + d * (float) Math.sin(a);
				if (!bomParaPlaca(chao, x, z, 25) || pertoDeOutra(placas, x, z, 15)) continue;
				float giro = (float) Math.atan2(-(p[1] - z), p[0] - x); // +x do modelo aponta para a prova
				placas.add(new Placa(false, x, z, giro, "M" + (i + 1)));
				feitas++;
			}
		}
		return placas;
	}

	private static boolean bomParaPlaca(ConsultaAltura chao, float x, float z, float margem) {
		return chao.dentroDoMapa(x, z, margem) && chao.alturaEm(x, z) > 0.2f && chao.anguloEm(x, z) < 22;
	}

	private static boolean pertoDeOutra(List<Placa> placas, float x, float z, float distancia) {
		for (Placa p : placas) {
			if (distancia(x, z, p.x(), p.z()) < distancia) return true;
		}
		return false;
	}

	/**
	 * O lugar da Univali {x, z}: terra firme, plano (no centro e a 15 m em volta), a mais de 150 m
	 * do início e a 40 m da borda. Sem um lugar perfeito, o melhor encontrado (o mais plano).
	 */
	public static float[] univali(ConsultaAltura chao, float[] inicio, Random random) {
		float t = chao.tamanho();
		float[] melhor = null;
		float melhorDesnivel = Float.MAX_VALUE;
		for (int tentativa = 0; tentativa < 4000; tentativa++) {
			float x = MARGEM_UNIVALI + random.nextFloat() * (t - 2 * MARGEM_UNIVALI);
			float z = MARGEM_UNIVALI + random.nextFloat() * (t - 2 * MARGEM_UNIVALI);
			if (distancia(x, z, inicio[0], inicio[1]) < DISTANCIA_UNIVALI_INICIO) continue;
			if (chao.ehAgua(x, z)) continue;
			float desnivel = desnivelEmVolta(chao, x, z, 15f);
			if (desnivel < melhorDesnivel) {
				melhorDesnivel = desnivel;
				melhor = new float[] { x, z };
				if (desnivel < 1f) break; // plano o bastante
			}
		}
		if (melhor == null) { // mapa estranho: o canto mais longe do início
			melhor = new float[] { inicio[0] < t / 2 ? t - MARGEM_UNIVALI : MARGEM_UNIVALI,
					inicio[1] < t / 2 ? t - MARGEM_UNIVALI : MARGEM_UNIVALI };
		}
		return melhor;
	}

	/**
	 * Os lugares das provas {x, z}: terra firme, pouco inclinada, longe da borda, a pelo menos
	 * 60 m umas das outras e do início, e a 40 m da Univali. Se o mapa não deixar, a distância
	 * exigida vai diminuindo aos poucos (sempre sai uma resposta).
	 */
	public static List<float[]> provas(ConsultaAltura chao, float[] inicio, float[] univali, int quantidade,
			Random random) {
		float t = chao.tamanho();
		float minima = DISTANCIA_ENTRE_PROVAS;
		while (true) {
			List<float[]> lugares = new ArrayList<>();
			for (int tentativa = 0; tentativa < 3000 && lugares.size() < quantidade; tentativa++) {
				float x = MARGEM_PROVAS + random.nextFloat() * (t - 2 * MARGEM_PROVAS);
				float z = MARGEM_PROVAS + random.nextFloat() * (t - 2 * MARGEM_PROVAS);
				if (chao.ehAgua(x, z) || chao.anguloEm(x, z) > DECLIVE_MAXIMO_PROVA) continue;
				if (distancia(x, z, inicio[0], inicio[1]) < minima) continue;
				if (univali != null && distancia(x, z, univali[0], univali[1]) < DISTANCIA_PROVA_UNIVALI) continue;
				boolean longe = true;
				for (float[] p : lugares) {
					if (distancia(x, z, p[0], p[1]) < minima) longe = false;
				}
				if (longe) lugares.add(new float[] { x, z });
			}
			if (lugares.size() == quantidade || minima < 10) return lugares;
			minima *= 0.85f;
		}
	}

	/** A maior diferença de altura entre o centro e 8 pontos num círculo de raio r. */
	static float desnivelEmVolta(ConsultaAltura chao, float x, float z, float r) {
		float centro = chao.alturaEm(x, z), maior = 0;
		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI / 4;
			float h = chao.alturaEm(x + r * (float) Math.cos(a), z + r * (float) Math.sin(a));
			maior = Math.max(maior, Math.abs(h - centro));
		}
		return maior;
	}

	private static float distancia(float ax, float az, float bx, float bz) {
		return (float) Math.hypot(ax - bx, az - bz);
	}
}
