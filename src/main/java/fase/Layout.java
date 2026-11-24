package fase;

import java.util.List;

/**
 * Um "indivíduo" do AG da fase: onde ficam o início do jogador, a Univali, as 3 provas e as
 * 3 caixas de munição. Imutável: o AG cria layouts novos em vez de alterar os antigos.
 */
public record Layout(Ponto inicio, Ponto univali, List<Ponto> provas, List<Ponto> caixas) {

	public Layout {
		provas = List.copyOf(provas);
		caixas = List.copyOf(caixas);
	}

	/** Quantos pontos o layout tem (o AG trata cada um como um "gene"). */
	public static final int PONTOS = 8;

	/** O ponto de número i: 0 início, 1 Univali, 2 a 4 provas, 5 a 7 caixas. */
	public Ponto ponto(int i) {
		return i == 0 ? inicio : i == 1 ? univali : i <= 4 ? provas.get(i - 2) : caixas.get(i - 5);
	}

	/** Uma cópia com o ponto i trocado. */
	public Layout com(int i, Ponto novo) {
		Ponto[] p = new Ponto[PONTOS];
		for (int k = 0; k < PONTOS; k++) p[k] = k == i ? novo : ponto(k);
		return deVetor(p);
	}

	public static Layout deVetor(Ponto[] p) {
		return new Layout(p[0], p[1], List.of(p[2], p[3], p[4]), List.of(p[5], p[6], p[7]));
	}
}
