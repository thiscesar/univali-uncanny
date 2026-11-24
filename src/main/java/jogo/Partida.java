package jogo;

import java.util.ArrayList;
import java.util.List;

import entidades.Prova;

/**
 * O estado da partida que não é de nenhuma entidade sozinha: as provas, o tempo jogado e as
 * estatísticas que aparecem no fim (tiros, bagos que acertaram, aparições sobrevividas). Só lógica.
 */
public class Partida {

	/** Uma "foto" das estatísticas, para as telas de fim. */
	public record Estatisticas(long seed, float segundos, int aparicoesSobrevividas, int tiros, int bagosDisparados,
			int bagosAcertados, int provasColetadas, int provasTotal) {

		/** Quantos por cento dos bagos acertaram o monstro (0 se não houve tiro). */
		public int precisao() {
			return bagosDisparados == 0 ? 0 : Math.round(100f * bagosAcertados / bagosDisparados);
		}
	}

	public final long seed;
	private final List<Prova> provas;
	private float segundos = 0;
	private int tiros = 0, bagosDisparados = 0, bagosAcertados = 0;

	public Partida(long seed, List<Prova> provas) {
		this.seed = seed;
		this.provas = List.copyOf(provas);
	}

	/** O tempo da partida anda (só enquanto se joga: a pausa e as cenas não contam). dt em segundos. */
	public void avancar(float dt) {
		segundos += dt;
	}

	public float segundos() {
		return segundos;
	}

	/** Um tiro com tantos bagos, dos quais tantos acertaram o monstro. */
	public void registrarTiro(int bagos, int acertos) {
		tiros++;
		bagosDisparados += bagos;
		bagosAcertados += acertos;
	}

	public Estatisticas estatisticas(int aparicoesSobrevividas) {
		return new Estatisticas(seed, segundos, aparicoesSobrevividas, tiros, bagosDisparados, bagosAcertados,
				provasColetadas(), provas.size());
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
