package fase;

import java.util.List;

/**
 * Um "indivíduo" do AG da fase: onde ficam a Univali, as 3 provas e as 3 caixas de munição.
 * A partida começa dentro da Univali: é de lá que o jogador sai para a floresta e para lá que
 * ele volta, então a Univali também é o ponto de partida das trilhas e das distâncias.
 * Imutável: o AG cria layouts novos em vez de alterar os antigos.
 */
public record Layout(Ponto univali, List<Ponto> provas, List<Ponto> caixas) {

	public Layout {
		provas = List.copyOf(provas);
		caixas = List.copyOf(caixas);
	}

	/** Quantos pontos o layout tem (o AG trata cada um como um "gene"). */
	public static final int PONTOS = 7;

	/** De onde se parte para a floresta: a Univali. */
	public Ponto inicio() {
		return univali;
	}

	/** O ponto de número i: 0 Univali, 1 a 3 provas, 4 a 6 caixas. */
	public Ponto ponto(int i) {
		return i == 0 ? univali : i <= 3 ? provas.get(i - 1) : caixas.get(i - 4);
	}

	/** Uma cópia com o ponto i trocado. */
	public Layout com(int i, Ponto novo) {
		Ponto[] p = new Ponto[PONTOS];
		for (int k = 0; k < PONTOS; k++) p[k] = k == i ? novo : ponto(k);
		return deVetor(p);
	}

	public static Layout deVetor(Ponto[] p) {
		return new Layout(p[0], List.of(p[1], p[2], p[3]), List.of(p[4], p[5], p[6]));
	}
}
