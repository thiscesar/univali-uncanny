package entidades;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import jogo.Regras;
import mundo.ConsultaAltura;
import mundo.GradeColisao;

/**
 * Um tiro de escopeta: 8 bagos saindo do centro da câmera (não da arma, para a mira ser fiel),
 * espalhados por igual num cone de 6°. Cada bago acerta o monstro, um tronco, o chão ou nada.
 * Só cálculo (não usa OpenGL): a TelaJogo usa o resultado para o dano e os efeitos.
 */
public final class Tiro {

	public enum Alvo { MONSTRO, TRONCO, CHAO }

	/** Onde um bago parou e no quê. */
	public record Impacto(Alvo alvo, float x, float y, float z) {}

	/** O resultado do tiro: quantos bagos acertaram o monstro e onde cada bago parou. */
	public record Resultado(int acertos, List<Impacto> impactos) {}

	/** De quanto em quanto o bago anda ao procurar o chão ou um tronco, em metros. */
	private static final float PASSO = 0.25f;

	private Tiro() {}

	/**
	 * Direção de um bago: um ponto sorteado num disco (raiz do sorteio no raio, para cair por igual
	 * na área, e não amontoado no meio), na frente da câmera, à distância 1. O raio do disco é a
	 * tangente do ângulo do cone. Todos os vetores da câmera devem ser unitários.
	 */
	static float[] direcaoBago(float[] frente, float[] direita, float[] cima, Random random) {
		float raio = (float) Math.tan(Math.toRadians(Regras.ESPALHAMENTO_GRAUS)) * (float) Math.sqrt(random.nextFloat());
		double angulo = random.nextDouble() * 2 * Math.PI;
		float a = raio * (float) Math.cos(angulo), b = raio * (float) Math.sin(angulo);
		float[] d = new float[3];
		for (int k = 0; k < 3; k++) {
			d[k] = frente[k] + direita[k] * a + cima[k] * b;
		}
		float t = (float) Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
		for (int k = 0; k < 3; k++) d[k] /= t;
		return d;
	}

	/** Dispara os bagos, aplica o dano no monstro e devolve o que aconteceu. */
	public static Resultado disparar(float[] origem, float[] frente, float[] direita, float[] cima, DragonKnight monstro,
			GradeColisao colisao, ConsultaAltura chao, Random random) {
		return disparar(origem, frente, direita, cima, monstro != null ? List.of(monstro) : List.of(), colisao, chao, random);
	}

	/**
	 * O mesmo, com vários alvos: cada bago fica no primeiro que encontrar (o mais perto), se ele
	 * vier antes de qualquer tronco ou do chão. Cada alvo recebe o dano dos bagos que acertou.
	 */
	public static Resultado disparar(float[] origem, float[] frente, float[] direita, float[] cima,
			List<? extends Atingivel> alvos, GradeColisao colisao, ConsultaAltura chao, Random random) {
		List<Impacto> impactos = new ArrayList<>();
		int acertos = 0;
		int[] acertosPorAlvo = new int[alvos.size()];
		for (int i = 0; i < Regras.BAGOS; i++) {
			float[] d = direcaoBago(frente, direita, cima, random);
			Impacto cenario = impactoNoCenario(origem, d, colisao, chao);
			float distanciaCenario = cenario == null ? Regras.ALCANCE_TIRO : distancia(origem, cenario);

			int atingido = -1;
			float distanciaMonstro = Float.MAX_VALUE;
			for (int a = 0; a < alvos.size(); a++) {
				float dist = alvos.get(a).intersectaRaio(origem, d);
				if (dist >= 0 && dist < distanciaMonstro) {
					distanciaMonstro = dist;
					atingido = a;
				}
			}
			if (atingido >= 0 && distanciaMonstro <= distanciaCenario) {
				// acertou um monstro antes de qualquer tronco ou do chão
				acertos++;
				acertosPorAlvo[atingido]++;
				impactos.add(new Impacto(Alvo.MONSTRO, origem[0] + d[0] * distanciaMonstro,
						origem[1] + d[1] * distanciaMonstro, origem[2] + d[2] * distanciaMonstro));
			} else if (cenario != null) {
				impactos.add(cenario);
			}
		}
		for (int a = 0; a < alvos.size(); a++) {
			if (acertosPorAlvo[a] > 0) {
				alvos.get(a).receberDano(acertosPorAlvo[a] * Regras.DANO_BAGO);
			}
		}
		return new Resultado(acertos, impactos);
	}

	/**
	 * Anda pelo raio em passos de 25 cm até bater num tronco (a grade de colisão) ou entrar
	 * no chão (a consulta de altura). Devolve null se chegar ao alcance sem bater em nada.
	 */
	static Impacto impactoNoCenario(float[] o, float[] d, GradeColisao colisao, ConsultaAltura chao) {
		for (float t = PASSO; t <= Regras.ALCANCE_TIRO; t += PASSO) {
			float x = o[0] + d[0] * t, y = o[1] + d[1] * t, z = o[2] + d[2] * t;
			if (!chao.dentroDoMapa(x, z, 0)) return null;
			if (y <= chao.alturaEm(x, z)) return new Impacto(Alvo.CHAO, x, chao.alturaEm(x, z), z);
			if (colisao.colide(x, z, 0)) return new Impacto(Alvo.TRONCO, x, y, z);
		}
		return null;
	}

	private static float distancia(float[] o, Impacto i) {
		float dx = i.x() - o[0], dy = i.y() - o[1], dz = i.z() - o[2];
		return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
}
