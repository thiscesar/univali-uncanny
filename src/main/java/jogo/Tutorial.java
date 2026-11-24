package jogo;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * As dicas dos primeiros minutos: uma de cada vez, esmaecida, embaixo da tela. Cada dica some
 * quando o jogador faz o que ela ensina (ou depois de um tempo, para não ficar insistindo). Uma
 * dica cujo gesto o jogador já fez sozinho nem aparece. Algumas só aparecem depois de um
 * acontecimento (as da floresta, quando ele sai do prédio).
 *
 * Só dados: a TelaJogo avisa as ações e desenha o texto com o alfa daqui.
 */
public class Tutorial {

	/** O que o jogador fez (ou o que aconteceu com ele). */
	public enum Acao { ANDOU, LANTERNA, USOU, SAIU, ATIROU }

	/**
	 * Uma dica: o texto, a ação que a conclui (null = só o tempo), a que precisa ter acontecido
	 * antes de ela aparecer (null = desde o início) e o tempo máximo na tela, em segundos.
	 */
	record Dica(String texto, Acao conclui, Acao libera, float duracaoMaxima) {}

	static final List<Dica> DICAS = List.of(
			new Dica("WASD para andar, mouse para olhar", Acao.ANDOU, null, 20),
			new Dica("F liga e desliga a lanterna", Acao.LANTERNA, null, 15),
			new Dica("E para usar: portas, crachá, professor", Acao.USOU, null, 25),
			new Dica("Encontre M1, M2 e M3 na floresta", null, Acao.SAIU, 6),
			new Dica("Clique para atirar, R para recarregar", Acao.ATIROU, Acao.SAIU, 10));

	/** Espera antes da primeira dica, e entre uma e outra, em segundos. */
	static final float ESPERA = 1.5f;
	/** Tempo para aparecer e para sumir, em segundos. */
	static final float ESMAECER = 0.6f;

	private final Set<Acao> feitas = EnumSet.noneOf(Acao.class);
	private int atual = 0;
	/** Tempo da dica atual na tela (negativo = esperando para mostrar). */
	private float tempo = -ESPERA;
	/** Tempo desde que a dica atual foi concluída (negativo = ainda não foi). */
	private float saindo = -1;

	public void registrar(Acao acao) {
		feitas.add(acao);
	}

	public void atualizar(float dt) {
		while (atual < DICAS.size()) {
			Dica d = DICAS.get(atual);
			if (d.libera() != null && !feitas.contains(d.libera())) return; // ainda não é hora
			if (tempo < 0 && d.conclui() != null && feitas.contains(d.conclui())) {
				proxima(); // já fez antes de a dica aparecer: pula
				continue;
			}
			break;
		}
		if (atual >= DICAS.size()) return;
		Dica d = DICAS.get(atual);
		tempo += dt;
		if (saindo < 0 && tempo > 0) {
			boolean feita = d.conclui() != null && feitas.contains(d.conclui());
			if (feita || tempo >= d.duracaoMaxima()) saindo = 0;
		}
		if (saindo >= 0) {
			saindo += dt;
			if (saindo >= ESMAECER) proxima();
		}
	}

	private void proxima() {
		atual++;
		tempo = -ESPERA;
		saindo = -1;
	}

	/** O texto da dica na tela (null = nenhuma). */
	public String texto() {
		return atual < DICAS.size() && tempo > 0 ? DICAS.get(atual).texto() : null;
	}

	/** De 0 a 1: aparece em ESMAECER segundos e some no mesmo tempo depois de concluída. */
	public float alfa() {
		if (texto() == null) return 0;
		float entrando = Math.min(1, tempo / ESMAECER);
		float sumindo = saindo < 0 ? 1 : Math.max(0, 1 - saindo / ESMAECER);
		return Math.min(entrando, sumindo);
	}

	public boolean terminou() {
		return atual >= DICAS.size();
	}
}
