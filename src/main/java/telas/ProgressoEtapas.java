package telas;

import java.util.ArrayList;
import java.util.List;

/**
 * Progresso de um carregamento feito em etapas de durações diferentes.
 *
 * Cada etapa tem um peso (a fatia da barra que ela ocupa). O progresso total é a soma
 * dos pesos das etapas concluídas mais a fração já feita da etapa atual. Não usa
 * OpenGL nem threads (dá para testar).
 */
public class ProgressoEtapas {

	private record Etapa(String nome, float peso) {}

	private final List<Etapa> etapas = new ArrayList<>();
	private int atual = 0;
	private float fracaoAtual = 0;

	public ProgressoEtapas adicionar(String nome, float peso) {
		etapas.add(new Etapa(nome, peso));
		return this;
	}

	/** Quanto da etapa atual já foi feito, de 0 a 1. */
	public void definirFracao(float fracao) {
		fracaoAtual = Math.max(0, Math.min(1, fracao));
	}

	/** Conclui a etapa atual e passa para a próxima. */
	public void concluirEtapa() {
		if (atual < etapas.size()) {
			atual++;
			fracaoAtual = 0;
		}
	}

	public boolean terminou() {
		return atual >= etapas.size();
	}

	public int indiceAtual() {
		return atual;
	}

	/** Nome da etapa em andamento (vazio quando tudo terminou). */
	public String nomeAtual() {
		return terminou() ? "" : etapas.get(atual).nome();
	}

	/** Progresso total de 0 a 1. */
	public float total() {
		float soma = 0, feito = 0;
		for (int i = 0; i < etapas.size(); i++) {
			float peso = etapas.get(i).peso();
			soma += peso;
			if (i < atual) {
				feito += peso;
			} else if (i == atual) {
				feito += peso * fracaoAtual;
			}
		}
		return soma == 0 ? 1 : feito / soma;
	}
}
