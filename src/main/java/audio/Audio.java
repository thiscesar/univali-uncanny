package audio;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.ALC10.*;
import static org.lwjgl.stb.STBVorbis.stb_vorbis_decode_filename;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALCCapabilities;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.libc.LibCStdlib;

import util.Caminhos;

/**
 * Todo o som do jogo, com OpenAL (som 3D) e stb_vorbis (leitura de OGG), ambos da LWJGL.
 *
 * - Efeitos curtos são decodificados inteiros e guardados num cache (carregarEfeito).
 * - Para tocar, o jogo usa um conjunto fixo de 32 "fontes" do OpenAL, reaproveitadas.
 *   Se todas estiverem ocupadas, a que começou há mais tempo é interrompida, poupando
 *   os sons em loop (como o sussurro das provas), que sumiriam sem voltar.
 * - tocar() toca um som "na cabeça" do jogador; tocar3D() toca num ponto do mundo, e o
 *   OpenAL calcula de que lado e com que volume ele chega, a partir do ouvinte
 *   (atualizado a cada quadro com a posição e a direção da câmera).
 * - A música toca em streaming (classe Musica).
 *
 * Se não houver placa de som, o jogo continua mudo: todos os métodos viram nada.
 */
public class Audio {

	public static final int NUMERO_FONTES = 32;

	/** Distâncias em metros: até 2 m o som tem volume cheio; depois cai; além de 60 m não cai mais. */
	private static final float DISTANCIA_REFERENCIA = 2f;
	private static final float DISTANCIA_MAXIMA = 60f;

	private long dispositivo = 0;
	private long contexto = 0;
	private boolean disponivel = false;

	private int[] fontes = new int[0];
	private long[] inicioFonte = new long[0];   // quando cada fonte começou a tocar (para achar a mais antiga)
	private long[] geracaoFonte = new long[0];  // muda a cada som novo na fonte
	private boolean[] loopFonte = new boolean[0]; // a fonte está tocando um som em loop
	private long contador = 0;

	private final Map<String, Efeito> efeitos = new HashMap<>();
	private final Set<String> avisadosEstereo = new HashSet<>();
	private int bufferSilencio = 0;
	private Musica musica = new Musica(false); // muda até o OpenAL iniciar

	private float volumeEfeitos = 1;

	/** Abre o dispositivo de som padrão. Se falhar, o jogo fica mudo e avisa no console. */
	public void iniciar() {
		try {
			dispositivo = alcOpenDevice((ByteBuffer) null);
			if (dispositivo == 0) {
				System.out.println("[Audio] nenhum dispositivo de som encontrado: jogo sem som");
				return;
			}
			ALCCapabilities capacidadesAlc = ALC.createCapabilities(dispositivo);
			contexto = alcCreateContext(dispositivo, (IntBuffer) null);
			alcMakeContextCurrent(contexto);
			AL.createCapabilities(capacidadesAlc);
		} catch (RuntimeException | UnsatisfiedLinkError e) {
			System.out.println("[Audio] não foi possível iniciar o OpenAL: " + e.getMessage() + " (jogo sem som)");
			return;
		}

		alDistanceModel(AL_INVERSE_DISTANCE_CLAMPED);

		fontes = new int[NUMERO_FONTES];
		inicioFonte = new long[NUMERO_FONTES];
		geracaoFonte = new long[NUMERO_FONTES];
		loopFonte = new boolean[NUMERO_FONTES];
		for (int i = 0; i < NUMERO_FONTES; i++) {
			fontes[i] = alGenSources();
			alSourcef(fontes[i], AL_REFERENCE_DISTANCE, DISTANCIA_REFERENCIA);
			alSourcef(fontes[i], AL_MAX_DISTANCE, DISTANCIA_MAXIMA);
			alSourcef(fontes[i], AL_ROLLOFF_FACTOR, 1f);
		}

		// silêncio de 50 ms, usado no lugar de arquivos que faltam
		bufferSilencio = alGenBuffers();
		alBufferData(bufferSilencio, AL_FORMAT_MONO16, BufferUtils.createShortBuffer(2205), 44100);

		musica = new Musica(true);
		disponivel = true;
		System.out.println("[Audio] OpenAL iniciado: " + alGetString(AL_RENDERER));
	}

	public boolean disponivel() {
		return disponivel;
	}

	// ---------- volumes ----------

	/** Volumes das opções, de 0 a 1. O geral multiplica todos (é o volume do ouvinte). */
	public void definirVolumes(float geral, float musica, float efeitos) {
		if (!disponivel) {
			return;
		}
		alListenerf(AL_GAIN, geral);
		this.musica.definirVolume(musica);
		this.volumeEfeitos = efeitos;
	}

	// ---------- efeitos ----------

	/**
	 * Decodifica um OGG inteiro (uma vez só) e devolve o efeito. Arquivo ausente vira um
	 * efeito silencioso, para o jogo funcionar sem os sons.
	 */
	public Efeito carregarEfeito(String relativo) {
		Efeito e = efeitos.get(relativo);
		if (e != null) {
			return e;
		}
		if (!disponivel) {
			e = new Efeito(0, 1, relativo);
		} else if (!Caminhos.arquivo(relativo).exists()) {
			e = new Efeito(bufferSilencio, 1, relativo);
		} else {
			e = decodificar(relativo);
		}
		efeitos.put(relativo, e);
		return e;
	}

	private Efeito decodificar(String relativo) {
		try (MemoryStack pilha = MemoryStack.stackPush()) {
			IntBuffer canais = pilha.mallocInt(1);
			IntBuffer taxa = pilha.mallocInt(1);
			ShortBuffer pcm = stb_vorbis_decode_filename(Caminhos.caminho(relativo), canais, taxa);
			if (pcm == null) {
				System.out.println("[Audio] não foi possível ler " + relativo + " (é um OGG Vorbis?)");
				return new Efeito(bufferSilencio, 1, relativo);
			}
			int buffer = alGenBuffers();
			alBufferData(buffer, canais.get(0) == 1 ? AL_FORMAT_MONO16 : AL_FORMAT_STEREO16, pcm, taxa.get(0));
			LibCStdlib.free(pcm); // o stb_vorbis alocou com malloc: liberamos com free
			return new Efeito(buffer, canais.get(0), relativo);
		}
	}

	/**
	 * Cria um efeito a partir de amostras geradas por código (PCM 16 bits mono).
	 * Pedir o mesmo nome de novo devolve o efeito já criado.
	 */
	public Efeito criarEfeito(String nome, short[] amostras, int taxa) {
		Efeito existente = efeitos.get(nome);
		if (existente != null) {
			return existente;
		}
		if (!disponivel) {
			return new Efeito(0, 1, nome);
		}
		ShortBuffer pcm = BufferUtils.createShortBuffer(amostras.length);
		pcm.put(amostras).flip();
		int buffer = alGenBuffers();
		alBufferData(buffer, AL_FORMAT_MONO16, pcm, taxa);
		Efeito e = new Efeito(buffer, 1, nome);
		efeitos.put(nome, e);
		return e;
	}

	/** Som "na cabeça" do jogador (interface, música de susto...). Devolve null se mudo. */
	public SomTocando tocar(Efeito efeito, float volume) {
		return tocar(efeito, volume, false);
	}

	/** O mesmo, podendo repetir sem parar (som ambiente, respiração). */
	public SomTocando tocar(Efeito efeito, float volume, boolean loop) {
		return tocarEm(efeito, volume, true, 0, 0, 0, loop, DISTANCIA_REFERENCIA);
	}

	/** Som num ponto do mundo, em metros. Para sons em loop, guarde o retorno para parar depois. */
	public SomTocando tocar3D(Efeito efeito, float x, float y, float z, float volume, boolean loop) {
		if (efeito.canais() == 2 && avisadosEstereo.add(efeito.nome())) {
			System.out.println("[Audio] " + efeito.nome() + " é estéreo: não será posicionado em 3D (converta para mono)");
		}
		return tocarEm(efeito, volume, false, x, y, z, loop, DISTANCIA_REFERENCIA);
	}

	/**
	 * Som 3D que se ouve de longe: "referencia" é a distância em que ele ainda toca no volume
	 * cheio (o normal é 2 m). Com 25 m, um sino a 150 m ainda soa claro, e vem da direção certa.
	 */
	public SomTocando tocar3D(Efeito efeito, float x, float y, float z, float volume, boolean loop, float referencia) {
		return tocarEm(efeito, volume, false, x, y, z, loop, referencia);
	}

	private SomTocando tocarEm(Efeito efeito, float volume, boolean relativo, float x, float y, float z, boolean loop,
			float referencia) {
		if (!disponivel || efeito.buffer() == 0) {
			return null;
		}
		boolean[] livres = new boolean[NUMERO_FONTES];
		for (int i = 0; i < NUMERO_FONTES; i++) {
			livres[i] = alGetSourcei(fontes[i], AL_SOURCE_STATE) != AL_PLAYING;
		}
		int i = escolherFonte(livres, inicioFonte, loopFonte);
		int f = fontes[i];
		alSourceStop(f);
		alSourcei(f, AL_BUFFER, efeito.buffer());
		alSourcei(f, AL_SOURCE_RELATIVE, relativo ? AL_TRUE : AL_FALSE);
		alSource3f(f, AL_POSITION, x, y, z);
		alSourcef(f, AL_REFERENCE_DISTANCE, referencia); // a fonte pode ter sido usada por um som "de longe"
		alSourcef(f, AL_GAIN, volume * volumeEfeitos);
		alSourcei(f, AL_LOOPING, loop ? AL_TRUE : AL_FALSE);
		alSourcePlay(f);
		inicioFonte[i] = ++contador;
		geracaoFonte[i]++;
		loopFonte[i] = loop;
		return new SomTocando(i, geracaoFonte[i]);
	}

	/**
	 * Escolhe a fonte para um som novo: a primeira livre; se nenhuma estiver livre, a que
	 * começou a tocar há mais tempo entre as que não estão em loop (e, só se todas
	 * estiverem em loop, a mais antiga de todas). Função pura, para poder testar.
	 */
	static int escolherFonte(boolean[] livres, long[] inicio, boolean[] emLoop) {
		int maisAntiga = -1, maisAntigaLoop = -1;
		for (int i = 0; i < livres.length; i++) {
			if (livres[i]) {
				return i;
			}
			if (emLoop[i]) {
				if (maisAntigaLoop < 0 || inicio[i] < inicio[maisAntigaLoop]) maisAntigaLoop = i;
			} else if (maisAntiga < 0 || inicio[i] < inicio[maisAntiga]) {
				maisAntiga = i;
			}
		}
		return maisAntiga >= 0 ? maisAntiga : maisAntigaLoop;
	}

	/** Para um som (ignora identificadores de sons que já foram substituídos). */
	public void parar(SomTocando som) {
		if (valido(som)) {
			alSourceStop(fontes[som.indiceFonte]);
		}
	}

	/** Move um som 3D que está tocando. */
	public void mover(SomTocando som, float x, float y, float z) {
		if (valido(som)) {
			alSource3f(fontes[som.indiceFonte], AL_POSITION, x, y, z);
		}
	}

	public boolean estaTocando(SomTocando som) {
		return valido(som) && alGetSourcei(fontes[som.indiceFonte], AL_SOURCE_STATE) == AL_PLAYING;
	}

	private boolean valido(SomTocando som) {
		return disponivel && som != null && geracaoFonte[som.indiceFonte] == som.geracao;
	}

	// ---------- ouvinte e música ----------

	/**
	 * Posição e orientação de quem ouve (a câmera), chamado a cada quadro durante a partida.
	 * frente e cima são vetores unitários: é por eles que o OpenAL sabe o que é esquerda e direita.
	 */
	public void atualizarOuvinte(float x, float y, float z, float frenteX, float frenteY, float frenteZ,
			float cimaX, float cimaY, float cimaZ) {
		if (!disponivel) {
			return;
		}
		alListener3f(AL_POSITION, x, y, z);
		alListenerfv(AL_ORIENTATION, new float[] { frenteX, frenteY, frenteZ, cimaX, cimaY, cimaZ });
	}

	/** A música do jogo. Mesmo sem som, devolve um objeto que não faz nada. */
	public Musica musica() {
		return musica;
	}

	/** Para tudo e libera o OpenAL. Chamado ao fechar o jogo. */
	public void encerrar() {
		if (!disponivel) {
			return;
		}
		musica.encerrar();
		for (int f : fontes) {
			alSourceStop(f);
			alDeleteSources(f);
		}
		for (Efeito e : efeitos.values()) {
			if (e.buffer() != 0 && e.buffer() != bufferSilencio) {
				alDeleteBuffers(e.buffer());
			}
		}
		alDeleteBuffers(bufferSilencio);
		alcMakeContextCurrent(0);
		alcDestroyContext(contexto);
		alcCloseDevice(dispositivo);
		disponivel = false;
	}
}
