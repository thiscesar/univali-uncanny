package jogo;

import java.util.ArrayList;
import java.util.List;

import entidades.Prova;

/**
 * O estado da partida que não é de nenhuma entidade sozinha: por enquanto, as provas e quantas
 * já foram pegas. (O tempo, as estatísticas e a nota entram depois.) Só lógica.
 */
public class Partida {

	public final long seed;
	private final List<Prova> provas;

	public Partida(long seed, List<Prova> provas) {
		this.seed = seed;
		this.provas = List.copyOf(provas);
	}

	public List<Prova> provas() {
		return provas;
	}

	public int provasColetadas() {
		int n = 0;
		for (Prova p : provas) {
			if (p.coletada()) n++;
		}
		return n;
	}

	public boolean todasColetadas() {
		return provasColetadas() == provas.size();
	}

	/** Os nomes das provas que ainda faltam, na ordem (para "Faltam: M2, M3"). */
	public List<String> faltando() {
		List<String> nomes = new ArrayList<>();
		for (Prova p : provas) {
			if (!p.coletada()) nomes.add(p.nome);
		}
		return nomes;
	}

	/** A prova que o jogador (os pés em x, z) consegue pegar agora, ou null. */
	public Prova provaAlcancavel(float x, float z) {
		for (Prova p : provas) {
			if (p.alcancavel(x, z)) return p;
		}
		return null;
	}
}
