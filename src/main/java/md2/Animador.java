package md2;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Toca as animações de um MD2: diz, a cada momento, quais dois quadros mostrar (A e B) e
 * quanto misturar um no outro (fator de 0 a 1). O vertex shader faz a mistura.
 *
 * O MD2 tem poucos quadros por segundo (9, no Quake 2). Mostrando só o quadro atual, o
 * movimento ficaria aos pulos; interpolando do quadro atual para o próximo, fica suave a
 * qualquer taxa de quadros da tela.
 *
 * - Em loop, depois do último quadro vem o primeiro de novo.
 * - Sem loop (morte, ataque), para no último quadro e avisa uma vez (aoTerminar).
 * - Ao trocar de animação, faz uma transição curta do quadro que estava na tela para o
 *   primeiro quadro da nova, em vez de pular.
 *
 * Não usa OpenGL (dá para testar).
 */
public class Animador {

	public static final float DURACAO_TRANSICAO = 0.12f; // segundos

	private final Map<String, Animacao> animacoes = new LinkedHashMap<>();
	private Animacao atual;
	private boolean loop;
	private float tempo;
	private boolean terminou;
	private Runnable aoTerminar;
	private float velocidade = 1;

	private int quadroOrigem = -1;  // quadro de onde a transição parte (-1 = sem transição)
	private float tempoTransicao;

	public Animador(List<Animacao> lista) {
		for (Animacao a : lista) {
			animacoes.put(a.nome(), a);
		}
	}

	public List<String> nomes() {
		return List.copyOf(animacoes.keySet());
	}

	public Animacao animacao(String nome) {
		return animacoes.get(nome);
	}

	/** Troca a velocidade de uma animação (quadros por segundo). */
	public void definirFps(String nome, float fps) {
		Animacao a = animacoes.get(nome);
		if (a != null) {
			animacoes.put(nome, a.comFps(fps));
			if (atual != null && atual.nome().equals(nome)) atual = animacoes.get(nome);
		}
	}

	public void tocar(String nome, boolean loop) {
		tocar(nome, loop, null);
	}

	/** Começa uma animação. aoTerminar é chamado uma vez quando uma animação sem loop chega ao fim. */
	public void tocar(String nome, boolean loop, Runnable aoTerminar) {
		Animacao nova = animacoes.get(nome);
		if (nova == null) {
			throw new IllegalArgumentException("animação inexistente: " + nome + " (existem: " + nomes() + ")");
		}
		if (atual != null) {
			quadroOrigem = quadroMostrado(); // a transição sai de onde a tela está agora
			tempoTransicao = 0;
		}
		atual = nova;
		this.loop = loop;
		this.aoTerminar = aoTerminar;
		tempo = 0;
		terminou = false;
	}

	/** Avança o tempo. dt em segundos. */
	public void atualizar(float dt) {
		if (atual == null) {
			return;
		}
		dt *= velocidade;
		if (quadroOrigem >= 0) {
			tempoTransicao += dt;
			if (tempoTransicao < DURACAO_TRANSICAO) {
				return;
			}
			dt = tempoTransicao - DURACAO_TRANSICAO; // o que sobrou vai para a animação nova
			quadroOrigem = -1;
		}
		tempo += dt;
		if (!loop) {
			float fim = (atual.quantidade() - 1) / atual.fps();
			if (tempo >= fim) {
				tempo = fim;
				if (!terminou) {
					terminou = true;
					if (aoTerminar != null) aoTerminar.run();
				}
			}
		}
	}

	private float posicao() {
		return tempo * atual.fps();
	}

	/** Quadro de onde a mistura parte. */
	public int quadroA() {
		if (quadroOrigem >= 0) return quadroOrigem;
		int n = atual.quantidade();
		int i = (int) Math.floor(posicao());
		return atual.primeiro() + (loop ? i % n : Math.min(i, n - 1));
	}

	/** Quadro para onde a mistura vai. */
	public int quadroB() {
		if (quadroOrigem >= 0) return atual.primeiro();
		int n = atual.quantidade();
		int i = (int) Math.floor(posicao()) + 1;
		return atual.primeiro() + (loop ? i % n : Math.min(i, n - 1));
	}

	/** Quanto do quadro B misturar no A (0 = só A, 1 = só B). */
	public float fator() {
		if (quadroOrigem >= 0) return tempoTransicao / DURACAO_TRANSICAO;
		if (terminou) return 0;
		float p = posicao();
		return p - (float) Math.floor(p);
	}

	/** O quadro mais parecido com o que está na tela. */
	public int quadroMostrado() {
		return fator() < 0.5f ? quadroA() : quadroB();
	}

	public String nomeAtual() {
		return atual == null ? "" : atual.nome();
	}

	public boolean terminou() {
		return terminou;
	}

	public boolean emLoop() {
		return loop;
	}

	/** Multiplicador da velocidade de todas as animações (1 = normal). */
	public void definirVelocidade(float velocidade) {
		this.velocidade = Math.max(0, velocidade);
	}

	public float velocidade() {
		return velocidade;
	}
}
