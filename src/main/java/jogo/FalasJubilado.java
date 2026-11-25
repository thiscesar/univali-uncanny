package jogo;

/**
 * O que o Jubilado fala quando vê o jogador (ou levanta depois de levar um tiro). Só lógica: a
 * tela toca a gravação (audio/voz_jubilado/, com o efeito de voz de doido) e mostra a legenda.
 *
 *  - A primeira vez que ele vê o jogador, se apresenta.
 *  - Depois, se o jogador já tem prova, alterna entre pedir a prova e ameaçar; sem prova, ameaça.
 *  - Não repete a fala anterior quando há outra possível.
 *  - No máximo uma fala a cada INTERVALO segundos; antes disso, ele só grita (devolve null).
 */
public final class FalasJubilado {

	public enum Fala {
		APRESENTACAO("me_chame_de_jubilado", "Pode me chamar de Jubilado."),
		PROVA("me_passa_a_prova", "Me passa essa prova, agora!"),
		AMEACA("vou_te_matar", "Não me deixaram me formar... agora eu vou te matar!");

		private final String arquivo, texto;

		Fala(String arquivo, String texto) {
			this.arquivo = arquivo;
			this.texto = texto;
		}

		/** A gravação, com o efeito (audio/voz_jubilado/...). */
		public String arquivo() {
			return "audio/voz_jubilado/" + arquivo + ".ogg";
		}

		/** A legenda, como as do professor. */
		public String legenda() {
			return "Jubilado: \"" + texto + "\"";
		}
	}

	/** Segundos mínimos entre duas falas. */
	public static final float INTERVALO = 20f;

	private boolean apresentou = false;
	private float ultima = -Float.MAX_VALUE;
	private Fala anterior = null;

	/** Ele viu o jogador no instante t (segundos de partida): a fala, ou null se falou há pouco. */
	public Fala aoVer(float t, int provasColetadas) {
		if (t - ultima < INTERVALO) return null;
		Fala fala;
		if (!apresentou) {
			fala = Fala.APRESENTACAO;
			apresentou = true;
		} else if (provasColetadas > 0) {
			fala = anterior == Fala.PROVA ? Fala.AMEACA : Fala.PROVA;
		} else {
			fala = Fala.AMEACA;
		}
		return falou(fala, t);
	}

	/** Ele levantou depois de levar um tiro: furioso, ameaça (ou pede a prova, se acabou de ameaçar). */
	public Fala aoLevantar(float t, int provasColetadas) {
		if (t - ultima < INTERVALO) return null;
		Fala fala = anterior == Fala.AMEACA && provasColetadas > 0 ? Fala.PROVA : Fala.AMEACA;
		return falou(fala, t);
	}

	private Fala falou(Fala fala, float t) {
		ultima = t;
		anterior = fala;
		return fala;
	}
}
