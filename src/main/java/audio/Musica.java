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
			float fator, vol;
			synchronized (this) {
				abrir = arquivoPedido;
				arquivoPedido = null;
				loopNovo = loopPedido;
				inicio = inicioPedido;
				parar = pararPedido;
				pararPedido = false;
				fator = fatorFade(fadeInicio, fadeAlvo, fadeComeco, fadeDuracao, System.currentTimeMillis());
				vol = volume;
			}
			if (parar) {
				fechar();
			}
			if (abrir != null) {
				abrir(abrir, loopNovo, inicio);
			}
			if (decodificador != 0) {
				alSourcef(fonte, AL_GAIN, vol * fator);
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
