package md2;

import java.util.ArrayList;
import java.util.List;

/**
 * Uma animação do MD2: um trecho de quadros consecutivos.
 *
 * O MD2 não guarda animações, só quadros com nome ("stand01", "stand02"... "run1"...).
 * A convenção do Quake 2 é nome da animação + número: tirando os dígitos do fim e
 * juntando os quadros seguidos com o mesmo nome, saem as animações.
 *
 * @param fps quadros por segundo (o Quake 2 usa 9 na maioria)
 */
public record Animacao(String nome, int primeiro, int ultimo, float fps) {

	public static final float FPS_PADRAO = 9f;

	public int quantidade() {
		return ultimo - primeiro + 1;
	}

	/** A mesma animação com outra velocidade. */
	public Animacao comFps(float novoFps) {
		return new Animacao(nome, primeiro, ultimo, novoFps);
	}

	/** Agrupa os quadros pelo nome sem os dígitos do fim. */
	public static List<Animacao> agrupar(List<String> nomesQuadros) {
		List<Animacao> lista = new ArrayList<>();
		if (nomesQuadros.isEmpty()) {
			return lista;
		}
		String atual = semDigitosFinais(nomesQuadros.get(0));
		int inicio = 0;
		for (int i = 1; i < nomesQuadros.size(); i++) {
			String base = semDigitosFinais(nomesQuadros.get(i));
			if (!base.equals(atual)) {
				lista.add(new Animacao(atual, inicio, i - 1, FPS_PADRAO));
				atual = base;
				inicio = i;
			}
		}
		lista.add(new Animacao(atual, inicio, nomesQuadros.size() - 1, FPS_PADRAO));
		return lista;
	}

	static String semDigitosFinais(String nome) {
		int fim = nome.length();
		while (fim > 0 && Character.isDigit(nome.charAt(fim - 1))) {
			fim--;
		}
		return nome.substring(0, fim);
	}

	@Override
	public String toString() {
		return nome + " [" + primeiro + ".." + ultimo + ", " + quantidade() + " quadros]";
	}
}
