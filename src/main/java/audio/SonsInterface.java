package audio;

import util.Caminhos;

/**
 * Os sons da interface (menu, pausa, opções e telas de fim): passar de um botão para outro,
 * clicar e ajustar um controle. Se existir o arquivo audio/menu_passar.ogg (ou _clique, _ajuste),
 * ele é usado; senão, o som sintetizado pelo Sintetizador.
 */
public final class SonsInterface {

	private static final int TAXA = 44100;
	private static final float VOLUME_PASSAR = 0.5f, VOLUME_CLIQUE = 0.8f, VOLUME_AJUSTE = 0.35f;
	/** Intervalo mínimo entre dois tiques de ajuste (arrastar um deslizante muda o valor todo quadro). */
	private static final long INTERVALO_AJUSTE_MS = 60;

	/** As amostras sintetizadas, geradas uma vez só. */
	private static short[] passar, clique, ajuste;
	private static long ultimoAjuste;

	private SonsInterface() {}

	/** O foco passou para outro botão ou linha. */
	public static void passar(Audio audio) {
		if (passar == null) passar = Sintetizador.menuPassar(TAXA);
		audio.tocar(efeito(audio, "audio/menu_passar.ogg", passar), VOLUME_PASSAR);
	}

	/** Um botão foi acionado (clique, Enter ou fechar um painel). */
	public static void clique(Audio audio) {
		if (clique == null) clique = Sintetizador.menuClique(TAXA);
		audio.tocar(efeito(audio, "audio/menu_clique.ogg", clique), VOLUME_CLIQUE);
	}

	/** O valor de um controle mudou (deslizante, alternador ou seletor). */
	public static void ajuste(Audio audio) {
		long agora = System.currentTimeMillis();
		if (agora - ultimoAjuste < INTERVALO_AJUSTE_MS) return;
		ultimoAjuste = agora;
		if (ajuste == null) ajuste = Sintetizador.menuAjuste(TAXA);
		audio.tocar(efeito(audio, "audio/menu_ajuste.ogg", ajuste), VOLUME_AJUSTE);
	}

	/** O arquivo, se existir; senão, o som sintetizado (o Audio guarda os dois pelo nome). */
	private static Efeito efeito(Audio audio, String arquivo, short[] sintetizado) {
		return Caminhos.existe(arquivo) ? audio.carregarEfeito(arquivo)
				: audio.criarEfeito(arquivo + " (sintetizado)", sintetizado, TAXA);
	}
}
