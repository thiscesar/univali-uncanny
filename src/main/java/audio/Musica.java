package audio;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.stb.STBVorbis.*;

import java.nio.ShortBuffer;

import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryUtil;

import util.Caminhos;

/**
 * Música em streaming: o arquivo OGG não é decodificado inteiro (seriam dezenas de MB),
 * mas aos pedaços, numa fila de buffers. Uma thread própria acompanha a fila: quando o
 * OpenAL termina de tocar um buffer, ela decodifica o próximo pedaço nele e o devolve
 * ao fim da fila. A mesma thread aplica o volume e os fades.
 *
 * Por ser uma thread separada, a música continua tocando mesmo que a thread principal
 * fique ocupada (por exemplo, gerando o mapa). Os métodos públicos só deixam pedidos;
 * todo o trabalho com OpenAL e com o decodificador acontece na thread da música.
 */
public class Musica {

	private static final int NUMERO_BUFFERS = 4;
	private static final int PAUSA_THREAD_MS = 10;

	/** false quando o jogo está sem som: os pedidos são aceitos, mas nada toca. */
	private final boolean ativa;
	private final int fonte;
	private final int[] buffers = new int[NUMERO_BUFFERS];
	private final Thread thread;
	private volatile boolean rodando = true;

	// ---- pedidos da thread principal (protegidos pelo synchronized) ----
	private String arquivoPedido;
	private boolean loopPedido;
	private float inicioPedido;
	private boolean pararPedido;
	private float fadeAlvo = 1, fadeInicio = 1;
	private long fadeComeco = 0, fadeDuracao = 0;
	private float volume = 1;
	/** Abafamento (ducking): a música baixa para "abafarNivel" entre abafarDesde e abafarAte (ms). */
	private float abafarNivel = 1;
	private long abafarDesde = 0, abafarAte = 0;

	/** Quanto tempo a música leva para baixar e para voltar depois de abafada, em ms. */
	static final long ABAFAR_DESCE = 150, ABAFAR_SOBE = 600;

	// ---- estado da thread da música ----
	private long decodificador = 0;
	private int canais, taxa;
	private boolean loop;
	private boolean acabou;
	private ShortBuffer pcm;

	/** @param ativa false cria uma música "muda", sem OpenAL (jogo sem placa de som) */
	Musica(boolean ativa) {
		this.ativa = ativa;
		if (!ativa) {
			fonte = 0;
			thread = null;
			return;
		}
		fonte = alGenSources();
		alSourcei(fonte, AL_SOURCE_RELATIVE, AL_TRUE); // não depende de onde está o ouvinte
		for (int i = 0; i < NUMERO_BUFFERS; i++) {
			buffers[i] = alGenBuffers();
		}
		thread = new Thread(this::laco, "musica");
		thread.setDaemon(true);
		thread.start();
	}

	/** Começa a tocar um OGG (caminho relativo, ex.: "audio/musica_menu.ogg"). Arquivo ausente = silêncio. */
	public synchronized void tocar(String relativo, boolean loop) {
		tocar(relativo, loop, 0);
	}

	/**
	 * O mesmo, começando "inicio" segundos depois do começo (para continuar uma música de onde
	 * parou). Com loop, passar do fim dá a volta: o início vale módulo a duração.
	 */
	public synchronized void tocar(String relativo, boolean loop, float inicio) {
		arquivoPedido = relativo;
		loopPedido = loop;
		inicioPedido = inicio;
		pararPedido = false;
		fadeAlvo = 1;
		fadeInicio = 1;
		fadeDuracao = 0;
	}

	public synchronized void parar() {
		pararPedido = true;
		arquivoPedido = null;
	}

	/** O volume vai de 0 ao normal em ms milissegundos. Chame logo depois de tocar(). */
	public synchronized void fadeIn(long ms) {
		iniciarFade(0, 1, ms);
	}

	/** O volume vai do atual a 0 em ms milissegundos; depois a música para. */
	public synchronized void fadeOut(long ms) {
		iniciarFade(fatorFade(fadeInicio, fadeAlvo, fadeComeco, fadeDuracao, System.currentTimeMillis()), 0, ms);
	}

	/**
	 * Abafa a música (ducking, como no rádio quando o locutor fala): ela baixa até "nivel" (0 a 1)
	 * em 0,15 s, fica assim por "ms" milissegundos e volta ao normal em 0,6 s. Para uma fala se
	 * ouvir por cima da música sem precisar pará-la. Se ela já está abafada (duas falas, ou um
	 * susto com uma fala), os dois se somam: vale o nível mais baixo e o fim mais tarde.
	 */
	public synchronized void abafar(float nivel, long ms) {
		Abafo a = juntar(new Abafo(abafarNivel, abafarDesde, abafarAte), nivel, ms, System.currentTimeMillis());
		abafarNivel = a.nivel();
		abafarDesde = a.desde();
		abafarAte = a.ate();
	}

	/** Um abafamento: o nível e de quando a quando (ms). */
	record Abafo(float nivel, long desde, long ate) {}

	/**
	 * Um pedido novo de abafar ("nivel" por "ms" a partir de "agora") junto com o que está valendo.
	 * Ainda abafada: fica o nível mais baixo e o fim mais tarde, sem recomeçar a descida (a música
	 * não sobe e desce de novo no meio). Já livre: um abafamento novo. Função pura, para testar.
	 */
	static Abafo juntar(Abafo atual, float nivel, long ms, long agora) {
		if (agora < atual.ate()) {
			return new Abafo(Math.min(atual.nivel(), nivel), atual.desde(), Math.max(atual.ate(), agora + ms));
		}
		return new Abafo(nivel, agora, agora + ms);
	}

	/**
	 * O fator do abafamento no instante "agora" (0 a 1): desce de 1 a "nivel" em ABAFAR_DESCE ms,
	 * fica em "nivel" até "ate" e sobe de volta a 1 em ABAFAR_SOBE ms. Função pura, para poder testar.
	 */
	static float fatorAbafar(float nivel, long desde, long ate, long agora) {
		if (agora < desde) return 1;
		if (agora < ate) {
			float t = Math.min(1, (float) (agora - desde) / ABAFAR_DESCE);
			return 1 + (nivel - 1) * t;
		}
		float t = Math.min(1, (float) (agora - ate) / ABAFAR_SOBE);
		return nivel + (1 - nivel) * t;
	}

	/** Volume da música (volume de música das opções), de 0 a 1. */
	public synchronized void definirVolume(float volume) {
		this.volume = volume;
	}

	private void iniciarFade(float de, float para, long ms) {
		fadeInicio = de;
		fadeAlvo = para;
		fadeComeco = System.currentTimeMillis();
		fadeDuracao = Math.max(1, ms);
	}

	/**
	 * Quanto do volume aplicar durante um fade (0 a 1), interpolando linearmente de
	 * "inicio" a "alvo" ao longo da duração. Função pura, para poder testar.
	 */
	static float fatorFade(float inicio, float alvo, long comeco, long duracao, long agora) {
		if (duracao <= 0) {
			return alvo;
		}
		float t = Math.max(0, Math.min(1, (float) (agora - comeco) / duracao));
		return inicio + (alvo - inicio) * t;
	}

	/** Encerra a thread e apaga a fonte e os buffers. Chamado pelo Audio ao fechar. */
	void encerrar() {
		if (!ativa) {
			return;
		}
		rodando = false;
		try {
			thread.join(500);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		fechar();
		alDeleteSources(fonte);
		for (int b : buffers) {
			alDeleteBuffers(b);
		}
	}

	// ---------- tudo abaixo roda na thread da música ----------

	private void laco() {
		while (rodando) {
			String abrir;
			boolean loopNovo, parar;
			float inicio;
			float fator, vol, abafo;
			synchronized (this) {
				abrir = arquivoPedido;
				arquivoPedido = null;
				loopNovo = loopPedido;
				inicio = inicioPedido;
				parar = pararPedido;
				pararPedido = false;
				fator = fatorFade(fadeInicio, fadeAlvo, fadeComeco, fadeDuracao, System.currentTimeMillis());
				vol = volume;
				abafo = fatorAbafar(abafarNivel, abafarDesde, abafarAte, System.currentTimeMillis());
			}
			if (parar) {
				fechar();
			}
			if (abrir != null) {
				abrir(abrir, loopNovo, inicio);
			}
			if (decodificador != 0) {
				alSourcef(fonte, AL_GAIN, vol * fator * abafo);
				if (fator <= 0 && fadeAlvoZero()) {
					fechar(); // fade-out terminou
				} else {
					alimentarFila();
				}
			}
			try {
				Thread.sleep(PAUSA_THREAD_MS);
			} catch (InterruptedException e) {
				return;
			}
		}
	}

	private synchronized boolean fadeAlvoZero() {
		return fadeAlvo == 0;
	}

	private void abrir(String relativo, boolean comLoop, float inicio) {
		fechar();
		if (!Caminhos.arquivo(relativo).exists()) {
			return; // o aviso já foi dado pelo Caminhos; fica em silêncio
		}
		int[] erro = new int[1];
		decodificador = stb_vorbis_open_filename(Caminhos.caminho(relativo), erro, null);
		if (decodificador == 0) {
			System.out.println("[Musica] não foi possível abrir " + relativo + " (erro " + erro[0] + ")");
			return;
		}
		try (STBVorbisInfo info = STBVorbisInfo.malloc()) {
			stb_vorbis_get_info(decodificador, info);
			canais = info.channels();
			taxa = info.sample_rate();
		}
		if (inicio > 0) {
			int total = stb_vorbis_stream_length_in_samples(decodificador); // amostras por canal
			long amostra = (long) (inicio * taxa);
			stb_vorbis_seek(decodificador, (int) (total > 0 ? amostra % total : amostra));
		}
		loop = comLoop;
		acabou = false;
		pcm = MemoryUtil.memAllocShort(taxa / 4 * canais); // um quarto de segundo por buffer

		for (int b : buffers) {
			if (encher(b)) {
				alSourceQueueBuffers(fonte, b);
			}
		}
		alSourcePlay(fonte);
	}

	/** Recoloca na fila os buffers que já tocaram, com o próximo pedaço da música. */
	private void alimentarFila() {
		int prontos = alGetSourcei(fonte, AL_BUFFERS_PROCESSED);
		for (int i = 0; i < prontos; i++) {
			int b = alSourceUnqueueBuffers(fonte);
			if (!acabou && encher(b)) {
				alSourceQueueBuffers(fonte, b);
			}
		}
		int naFila = alGetSourcei(fonte, AL_BUFFERS_QUEUED);
		if (naFila == 0 && acabou) {
			fechar(); // a música (sem loop) chegou ao fim
		} else if (naFila > 0 && alGetSourcei(fonte, AL_SOURCE_STATE) != AL_PLAYING) {
			alSourcePlay(fonte); // a fila esvaziou por atraso: retoma
		}
	}

	/** Decodifica o próximo pedaço no buffer. Devolve false quando a música acabou. */
	private boolean encher(int buffer) {
		pcm.clear();
		int amostras = stb_vorbis_get_samples_short_interleaved(decodificador, canais, pcm);
		if (amostras == 0 && loop) {
			stb_vorbis_seek_start(decodificador); // volta ao começo, sem pausa
			amostras = stb_vorbis_get_samples_short_interleaved(decodificador, canais, pcm);
		}
		if (amostras == 0) {
			acabou = true;
			return false;
		}
		pcm.limit(amostras * canais);
		alBufferData(buffer, canais == 1 ? AL_FORMAT_MONO16 : AL_FORMAT_STEREO16, pcm, taxa);
		return true;
	}

	private void fechar() {
		alSourceStop(fonte);
		int naFila = alGetSourcei(fonte, AL_BUFFERS_QUEUED);
		for (int i = 0; i < naFila; i++) {
			alSourceUnqueueBuffers(fonte);
		}
		if (decodificador != 0) {
			stb_vorbis_close(decodificador);
			decodificador = 0;
		}
		if (pcm != null) {
			MemoryUtil.memFree(pcm);
			pcm = null;
		}
	}
}
