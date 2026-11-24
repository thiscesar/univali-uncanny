package telas;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL20.glGetUniformLocation;
import static org.lwjgl.opengl.GL20.glUniform1f;
import static org.lwjgl.opengl.GL20.glUniform1i;
import static org.lwjgl.opengl.GL20.glUniformMatrix4fv;
import static org.lwjgl.opengl.GL20.glDisableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttrib3f;

import java.awt.Color;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.Random;

import org.lwjgl.BufferUtils;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;

import audio.Audio;
import audio.Efeito;
import audio.Sintetizador;
import audio.SomAmbiente;
import audio.SomTocando;
import dados.Opcoes;
import dados.Recursos;
import entidades.CaixaMunicao;
import entidades.DragonKnight;
import entidades.Escopeta;
import entidades.Prova;
import entidades.Tiro;
import entidades.Jogador;
import entrada.Entrada;
import jogo.Fantasma;
import jogo.MaterialChao;
import jogo.Objetivos;
import jogo.Partida;
import jogo.Regras;
import jogo.SustosFalsos;
import jogo.Tutorial;
import md2.Animador;
import mundo.ConsultaAltura;
import mundo.Mundo;
import mundo.DistribuidorArvores;
import mundo.TexturasFloresta;
import render.Ambiente;
import render.Camera;
import render.CameraLivre;
import render.DesenhoEscopeta;
import render.DesenhoFantasma;
import render.DesenhoMonstro;
import render.DesenhoPlacas;
import render.DesenhoPredio;
import render.DesenhoProvas;
import render.DesenhoSilhueta;
import render.FiltroPs1;
import render.Lanterna;
import render.Overlay2D;
import render.Particulas;
import render.SombraLanterna;
import render.Texto;
import render.TextoTextura;
import shaders.ShaderArvore;
import shaders.ShaderArvore3D;
import shaders.ShaderObjeto;
import shaders.ShaderTerrenoFloresta;
import util.Caminhos;
import util.Utils3D;
import Model.VboCube;

/**
 * A partida: o jogador andando pela floresta à noite, em primeira pessoa, com a lanterna.
 *
 * F liga e desliga a lanterna. ESC abre a pausa por cima. F3 mostra o painel de depuração; F4 troca para a câmera livre
 * (voa e atravessa o chão, para inspecionar o mapa). V e X forçam vitória e derrota, e T liga
 * um som 3D de teste: são atalhos provisórios até existirem as regras do jogo. Com o F3 ligado,
 * G faz o Dragon Knight surgir e K o mata, P leva até a próxima prova, I salva a imagem do mapa da fase; as setas e Page Up/Down ajustam a posição da arma.
 *
 * A partida começa dentro da Univali, à noite: o térreo é um corredor com salas dos dois lados. A sala do
 * professor está trancada (é preciso achar o crachá); ele nega sem as provas e abre a porta da
 * frente; o jogador sai para a floresta, acha as 3 provas e volta para entregá-las na mesa dele.
 * O Dragon Knight só começa a contar quando o jogador sai do prédio, e não surge perto dele.
 *
 * As provas M1, M2 e M3: folhas brilhando e sussurrando pela floresta; E pega. Cada uma deixa o
 * monstro mais frequente; a terceira faz tocar o sinal de aula, vindo da direção da Univali.
 *
 * A escopeta: clique esquerdo atira, R recarrega, E pega uma caixa de cartuchos. A lógica fica
 * em entidades.Escopeta (e os bagos em entidades.Tiro), o desenho em render.DesenhoEscopeta.
 *
 * O Dragon Knight: a lógica fica em entidades.DragonKnight e o desenho em render.DesenhoMonstro.
 * Aqui ficam o que ele causa no jogador: o grito 3D, a contagem na tela, o batimento, a vinheta
 * vermelha, a lanterna falhando, o tremor da câmera, o susto e a derrota.
 */
public class TelaJogo implements Tela {

	static final String TITULO = "Jogo";
	/** Onde estão os arquivos do Dragon Knight e a altura dele em metros (o cavaleiro montado). */
	static final String PASTA_MONSTRO = "modelos/dragonknight";
	static final float ALTURA_MONSTRO = 2.2f;

	private static final float PERTO = 0.1f;
	// a neblina esconde tudo antes de ~50 m; só os feixes das provas (neblina mais fraca) vão além
	private static final float LONGE = 120f;

	private static final Color COR_PAINEL = new Color(0, 0, 0, 160);
	private static final Color COR_DEPURACAO = new Color(200, 195, 185);
	private static final Color COR_DICA = new Color(120, 115, 110, 200);
	private static final Color COR_CONTAGEM = new Color(215, 25, 18);

	private final GerenciadorTelas gerenciador;
	/** O mundo pronto (terreno, chão e colisão), entregue pela tela de carregamento. */
	private final Mundo mundo;
	private final Jogador jogador;

	/** Luz e neblina da cena, iguais para o terreno e para os objetos (ver comum.glsl). */
	private final Ambiente ambiente = new Ambiente();
	private final Lanterna lanterna = new Lanterna();

	/** Modo PS1: a cena é desenhada pequena e ampliada sem suavização (liga e desliga nas Opções). */
	private final FiltroPs1 filtroPs1 = new FiltroPs1();
	private boolean ps1;
	/** Campo de visão vertical, em graus (das Opções). */
	private float campoVisao;
	/** A sombra da lanterna e o lado do mapa dela (0 = desligada, das Opções). */
	private final SombraLanterna sombra = new SombraLanterna();
	private int ladoSombra;
	/** De 0 a 1: o quanto o monstro está em cena (separa as cores e ondula a imagem). */
	private float presencaMonstro = 0;

	/** Câmera livre de depuração (F4), começando onde o jogador estiver. */
	private CameraLivre cameraLivre = null;
	private boolean mostrarDepuracao = false;

	private final FloatBuffer bufferProjecao = BufferUtils.createFloatBuffer(16);
	private final FloatBuffer bufferVisao = BufferUtils.createFloatBuffer(16);
	private int[] texturasTerreno;

	// sons
	private final Random random = new Random();
	/** Variações de passo para cada material do chão, andando e (onde houver gravação) correndo. */
	private final java.util.Map<MaterialChao, Efeito[]> passos = new java.util.EnumMap<>(MaterialChao.class);
	private final java.util.Map<MaterialChao, Efeito[]> passosCorrendo = new java.util.EnumMap<>(MaterialChao.class);
	private Efeito respiracao;
	private Efeito cliqueLanterna;
	private SomAmbiente somAmbiente;
	private SomTocando somRespiracao = null;
	private SomTocando somTeste = null;

	// as dicas do começo e os sustos falsos
	private final Tutorial tutorial = new Tutorial();
	private float andado = 0, ultimoX, ultimoZ;
	private SustosFalsos sustos;
	private DesenhoSilhueta desenhoSilhueta;
	/** A silhueta em cena: {x, y, z, guinada, segundos na tela}; null = nenhuma. */
	private float[] silhueta = null;
	private Efeito somGalho;
	/** O fantasma de passagem (null = nenhum) e quanto tempo um fantasma da Univali ainda espera o jogador no corredor. */
	private Fantasma fantasma = null;
	private float esperaFantasmaPredio = 0;
	private DesenhoFantasma desenhoFantasma;
	/** A fala do professor cuja voz já tocou (para tocar uma vez por fala) e a última fala da entrega com voz. */
	private String falaComVoz = null;
	/** Quanto tempo a fala atual fica na tela: pelo menos 3,2 s, ou até a voz acabar. */
	private static final float DURACAO_MINIMA_FALA = 3.2f;
	private float duracaoFala = DURACAO_MINIMA_FALA;
	/** audio/risada_professor.ogg: ele ri quando o jogador sai da sala depois da negativa (null = sem o arquivo). */
	private Efeito somRisadaProfessor;
	private boolean risadaPendente = false, naSalaDoProfessor = false;
	private int ultimaFalaEntrega = -1;
	/** audio/risada.ogg (null = sem o arquivo: a risada vira galho). */
	private Efeito somRisada;

	// o Dragon Knight
	private final DragonKnight monstro;
	private DesenhoMonstro desenhoMonstro;
	private Efeito somGrito, somMorte, somBatida, somSusto;
	/**
	 * A música do monstro (metal): entra quando ele surge e some quando ele morre. Cada aparição
	 * continua a música de onde a anterior parou (segundosMetal), para não repetir sempre o começo.
	 */
	private static final String MUSICA_MONSTRO = "audio/musica_monstro.ogg";
	private static final long ENTRADA_METAL_MS = 250, SAIDA_METAL_MS = 2500;
	private float segundosMetal = 0;
	/** Segundos que a música ainda toca (enquanto ele está em cena, infinito; depois, o fade). */
	private float metalRestante = 0;
	/** Com a música do monstro, o batimento da contagem sai (os dois ritmos brigavam). */
	private boolean temMusicaMonstro;

	// a escopeta, os efeitos dela e as caixas de munição
	private final Escopeta escopeta = new Escopeta();
	private DesenhoEscopeta desenhoEscopeta;
	private final Particulas particulas = new Particulas();
	private java.util.List<CaixaMunicao> caixas;
	private VboCube cuboCaixa;
	private Efeito somTiro, somTelha, somCartucho, somVazia;
	/** Segundos de fumaça que ainda saem do cano. */
	private float fumacaRestante = 0;
	// as provas
	private final Partida partida;
	private DesenhoProvas desenhoProvas;
	private DesenhoPlacas desenhoPlacas;
	private DesenhoPredio desenhoPredio;
	/** A cena da entrega (segundos desde que começou); -1 = ainda não. */
	private float tempoEntrega = -1;
	/** Segunda linha da mensagem (a fala do professor), com o mesmo tempo da primeira. */
	private String mensagem2 = null;
	/** A sequência da partida (crachá, professor, provas) e as falas esperando a vez de aparecer. */
	private final Objetivos objetivos = new Objetivos();
	private final java.util.ArrayDeque<String> falas = new java.util.ArrayDeque<>();
	private float tempoFala = 0;
	/**
	 * O chamado das provas: não é mais um sussurro sem fim (incomodava, e se ouvia de longe o tempo
	 * todo). Cada prova sussurra uma frase de vez em quando, e só com o jogador a menos de 30 m.
	 */
	private static final float ALCANCE_SUSSURRO = 30f, VOLUME_SUSSURRO = 0.5f;
	private static final float SUSSURRO_MINIMO = 7f, SUSSURRO_MAXIMO = 13f;
	private final java.util.Map<Prova, SomTocando> sussurros = new java.util.HashMap<>();
	/** Segundos até a próxima frase de cada prova (pode ficar negativo esperando o jogador chegar perto). */
	private final java.util.Map<Prova, Float> esperaSussurro = new java.util.HashMap<>();
	private Efeito somSussurro, somPegarProva, somSinal;
	private SomTocando somZumbido;
	/** Segundos desde que cada prova foi pega (para o pulso no HUD); negativo = ainda não. */
	private final java.util.Map<Prova, Float> pulsoProva = new java.util.HashMap<>();

	/** Mensagem curta no meio da tela ("+6 cartuchos", "Sem munição!") e por quanto tempo. */
	private String mensagem = null;
	private float tempoMensagem = 0;
	private float proximaBatida = 0;
	/** Tremor da câmera, de 0 a 1 (diminui sozinho). */
	private float tremor = 0;
	/** Segundos desde o começo do susto; -1 = sem susto. */
	private float tempoSusto = -1;
	/** A partida acabou: para onde ir (null = ainda jogando) e a foto do último quadro, tirada no desenhar. */
	private enum Fim { VITORIA, DERROTA }
	private Fim fim = null;
	private Recursos.Textura fotoFim = null;

	/** A cena da entrega: o escurecimento e as falas (início e fim de cada uma, em segundos). */
	private static final float FIM_ENTREGA = 8.2f;
	private static final String[] FALAS = { "Professor: \"Atrasado de novo?\"", "...", "Professor: \"Tá bom. Vou corrigir.\"" };
	private static final float[][] TEMPO_FALAS = { { 1.6f, 3.6f }, { 3.8f, 5.0f }, { 5.2f, 7.6f } };

	/** sprites/susto.png, se existir (0 = sem a imagem: o susto é o próprio modelo bem de perto). */
	private int texturaSusto = 0;

	/** Tempo da partida em segundos; para quando a partida está pausada (anima a água). */
	private float tempo = 0;
	// FPS medido em janelas de meio segundo, para o painel
	private int quadrosContados = 0;
	private float tempoContado = 0, fps = 0;

	public TelaJogo(GerenciadorTelas gerenciador, Mundo mundo) {
		this.gerenciador = gerenciador;
		this.mundo = mundo;
		this.jogador = new Jogador(mundo.inicio[0], mundo.inicio[1], mundo.chao, mundo.colisao);
		// começa olhando para a saída da sala de início (a única passagem dela)
		float[] saidaDaSala = mundo.predio.andar.inicio().porta();
		float[] alvo = mundo.predio.noMundo(saidaDaSala[0], 0, saidaDaSala[1]);
		// a câmera olha para (sen g, -cos g): para olhar na direção (dx, dz), g = atan2(dx, -dz)
		float guinada = (float) Math.atan2(alvo[0] - mundo.inicio[0], -(alvo[2] - mundo.inicio[1]));
		jogador.girar(guinada - jogador.camera().guinada, 0);
		// as animações e o tamanho vêm do MD2; sem ele, o monstro usa tempos e a caixa padrão
		Animador animador = mundo.monstro != null ? new Animador(mundo.monstro.animacoes()) : null;
		float[] caixa = mundo.monstro != null ? mundo.monstro.malhaCorpo().limites(0) : null;
		this.monstro = new DragonKnight(mundo.chao, mundo.colisao, animador, caixa, System.nanoTime());
		java.util.List<Prova> provas = new java.util.ArrayList<>();
		for (int i = 0; i < mundo.provas.size(); i++) {
			float[] lugar = mundo.provas.get(i);
			provas.add(new Prova("M" + (i + 1), lugar[0], lugar[1], mundo.chao));
		}
		this.partida = new Partida(mundo.seed, provas);
	}

	@Override
	public void entrar() {
		gerenciador.definirTitulo(TITULO);
		gerenciador.entrada().prenderCursor(true);
		texturasTerreno = TexturasFloresta.carregar(); // já carregadas na tela de carregamento: aqui só pega
		ps1 = Opcoes.atual().modoPs1;
		Recursos.aplicarModoPs1(ps1);

		Audio audio = gerenciador.audio();
		campoVisao = Opcoes.atual().campoVisao;
		ladoSombra = Opcoes.LADO_SOMBRA[Opcoes.atual().sombras];
		for (MaterialChao m : MaterialChao.values()) {
			String nome = "audio/passo_" + m.name().toLowerCase();
			Efeito[] gravados = carregarVariacoes(nome + "_");
			if (gravados.length == 0) { // sem gravação: quatro passos sintetizados
				gravados = new Efeito[4];
				for (int i = 0; i < gravados.length; i++) {
					long semente = m.ordinal() * 10L + i;
					gravados[i] = audio.criarEfeito(nome + "_" + (i + 1) + " (sintetizado)", sintetizarPasso(m, semente), 44100); // nomes diferentes: o cache é por nome
				}
			}
			passos.put(m, gravados);
			Efeito[] correndo = carregarVariacoes(nome + "_correndo_");
			if (correndo.length > 0) passosCorrendo.put(m, correndo);
		}
		somGalho = efeitoOuSintetizado("audio/galho_quebrando.ogg", () -> Sintetizador.galho(44100, 3));
		somRisada = Caminhos.existe("audio/risada.ogg") ? audio.carregarEfeito("audio/risada.ogg") : null;
		somRisadaProfessor = Caminhos.existe("audio/risada_professor.ogg") ? audio.carregarEfeito("audio/risada_professor.ogg")
				: null;
		sustos = new SustosFalsos(mundo.seed + 5);
		desenhoSilhueta = new DesenhoSilhueta();
		desenhoFantasma = new DesenhoFantasma();
		ultimoX = jogador.x;
		ultimoZ = jogador.z;
		respiracao = audio.carregarEfeito("audio/respiracao.ogg");
		cliqueLanterna = audio.carregarEfeito("audio/lanterna_clique.ogg");
		somAmbiente = new SomAmbiente(audio);
		somAmbiente.iniciar();

		desenhoMonstro = new DesenhoMonstro(mundo.monstro);
		somGrito = efeitoOuSintetizado("audio/monstro_grito.ogg", () -> Sintetizador.rugido(44100, 1400, 120, 55, 0.55, 3));
		somMorte = efeitoOuSintetizado("audio/monstro_morte.ogg", () -> Sintetizador.rugido(44100, 1800, 85, 28, 0.45, 5));
		somBatida = efeitoOuSintetizado("audio/batimento.ogg", () -> Sintetizador.batimento(44100));
		somSusto = efeitoOuSintetizado("audio/susto.ogg", () -> Sintetizador.rugido(44100, 900, 240, 90, 0.8, 7));
		if (Caminhos.existe("sprites/susto.png")) {
			texturaSusto = Recursos.textura("sprites/susto.png");
		}
		desenhoEscopeta = new DesenhoEscopeta();
		somTiro = efeitoOuSintetizado("audio/escopeta_tiro.ogg", () -> Sintetizador.tiro(44100));
		somTelha = efeitoOuSintetizado("audio/escopeta_telha.ogg",
				() -> Sintetizador.estalos(44100, new int[] { 0, 150 }, new double[] { 0.8, 1.0 }));
		somCartucho = efeitoOuSintetizado("audio/escopeta_cartucho.ogg",
				() -> Sintetizador.estalos(44100, new int[] { 0, 60 }, new double[] { 0.5, 0.8 }));
		somVazia = efeitoOuSintetizado("audio/escopeta_vazia.ogg",
				() -> Sintetizador.estalos(44100, new int[] { 0 }, new double[] { 0.6 }));
		escopeta.definirEventos(new Escopeta.Eventos() {
			@Override
			public void engatilhou() {
				gerenciador.audio().tocar(somTelha, 0.8f);
			}

			@Override
			public void cartuchoInserido() {
				gerenciador.audio().tocar(somCartucho, 0.8f);
			}

			@Override
			public void cliqueSeco() {
				gerenciador.audio().tocar(somVazia, 0.9f);
				mostrarMensagem(escopeta.reserva() == 0 ? "Sem munição!" : "Vazia! R para recarregar");
			}
		});
		desenhoProvas = new DesenhoProvas();
		desenhoPlacas = new DesenhoPlacas();
		desenhoPredio = new DesenhoPredio(mundo.predio);
		monstro.zonaSegura = mundo.predio.retangulo(); // perto da Univali, ele não surge
		monstro.ativo = false;                         // e só começa quando o jogador sair dela
		// as duas portas começam trancadas: um retângulo no vão de cada uma
		mundo.colisao.adicionarRetangulo(mundo.predio.portaEntrada());
		mundo.colisao.adicionarRetangulo(mundo.predio.portaProfessor());
		mostrarMensagem("Encontre a sala do professor.", 4f);
		somSussurro = efeitoOuSintetizado("audio/sussurro.ogg", () -> Sintetizador.sussurro(44100, 13));
		somPegarProva = efeitoOuSintetizado("audio/pegar_prova.ogg", () -> Sintetizador.ping(44100, 660, 600, 900));
		somSinal = efeitoOuSintetizado("audio/sinal_aula.ogg", () -> Sintetizador.sinal(44100));
		// o zumbido das lâmpadas no saguão, em loop
		float[] saguao = mundo.predio.noMundo(0, 3, 8);
		somZumbido = gerenciador.audio().tocar3D(gerenciador.audio().criarEfeito("zumbido das lampadas",
				Sintetizador.zumbido(44100), 44100), saguao[0], saguao[1], saguao[2], 0.22f, true, 2.5f);
		temMusicaMonstro = Caminhos.existe(MUSICA_MONSTRO);

		caixas = new java.util.ArrayList<>();
		for (float[] c : mundo.caixas) { // onde o AG da fase pôs as caixas
			caixas.add(new CaixaMunicao(c[0], mundo.chao.alturaEm(c[0], c[1]), c[1], random.nextFloat() * (float) Math.PI));
		}
		cuboCaixa = Recursos.cubo(); // um só para o jogo inteiro: o VboCube do projeto original não tem como ser apagado

		monstro.definirEventos(new DragonKnight.Eventos() {
			@Override
			public void surgiu(DragonKnight m) {
				tremor = 1;
				gerenciador.audio().musica().tocar(MUSICA_MONSTRO, true, segundosMetal);
				gerenciador.audio().musica().fadeIn(ENTRADA_METAL_MS);
				metalRestante = Float.MAX_VALUE;
				// o grito sai de onde ele está: com fone, dá para saber de que lado ele surgiu
				gerenciador.audio().tocar3D(somGrito, m.x, m.y + 1.6f, m.z, 1f, false);
			}

			@Override
			public void morreu(DragonKnight m) {
				gerenciador.audio().tocar3D(somMorte, m.x, m.y + 1.2f, m.z, 1f, false);
				gerenciador.audio().musica().fadeOut(SAIDA_METAL_MS);
				metalRestante = SAIDA_METAL_MS / 1000f;
			}

			@Override
			public void atacou(DragonKnight m) {
				tremor = 0.7f;
				gerenciador.audio().tocar(somGrito, 1f);
			}
		});
	}

	/** Os arquivos prefixo1.ogg, prefixo2.ogg... que existirem (até 8), em ordem. */
	private Efeito[] carregarVariacoes(String prefixo) {
		List<Efeito> lista = new java.util.ArrayList<>();
		for (int i = 1; i <= 8 && Caminhos.existe(prefixo + i + ".ogg"); i++) {
			lista.add(gerenciador.audio().carregarEfeito(prefixo + i + ".ogg"));
		}
		return lista.toArray(new Efeito[0]);
	}

	private static short[] sintetizarPasso(MaterialChao m, long semente) {
		return switch (m) {
		case GRAMA -> Sintetizador.passoFolhas(44100, semente);
		case TERRA -> Sintetizador.passoTerra(44100, semente);
		case AGUA -> Sintetizador.passoAgua(44100, semente);
		case PISO -> Sintetizador.passoPiso(44100, semente);
		};
	}

	/** O som do arquivo; sem o arquivo, um substituto gerado por código (para o monstro não ficar mudo). */
	private Efeito efeitoOuSintetizado(String arquivo, java.util.function.Supplier<short[]> substituto) {
		Audio audio = gerenciador.audio();
		if (Caminhos.existe(arquivo)) {
			return audio.carregarEfeito(arquivo);
		}
		return audio.criarEfeito(arquivo + " (sintetizado)", substituto.get(), 44100);
	}

	@Override
	public void sair() {
		gerenciador.entrada().prenderCursor(false);
		Audio audio = gerenciador.audio();
		audio.parar(somTeste);
		audio.parar(somRespiracao);
		somTeste = null;
		somRespiracao = null;
		somAmbiente.parar();
		if (metalRestante > 0) {
			audio.musica().fadeOut(800);
		}
		filtroPs1.liberar();
		sombra.liberar();
		desenhoMonstro.liberar();
		desenhoEscopeta.liberar();
		desenhoProvas.liberar();
		desenhoPlacas.liberar();
		desenhoPredio.liberar();
		desenhoSilhueta.liberar();
		desenhoFantasma.liberar();
		for (SomTocando s : sussurros.values()) {
			audio.parar(s);
		}
		sussurros.clear();
		audio.parar(somZumbido);
		particulas.liberar();
	}

	// ---------- atualização ----------

	@Override
	public void atualizar(long diftime) {
		Entrada entrada = gerenciador.entrada();
		if (fim != null) {
			// o desenhar do quadro anterior já fotografou a tela: agora troca
			if (fotoFim != null || fim == Fim.VITORIA) {
				Partida.Estatisticas estatisticas = partida.estatisticas(monstro.aparicoesSobrevividas());
				gerenciador.trocarPara(fim == Fim.VITORIA ? new TelaVitoria(gerenciador, estatisticas)
						: new TelaDerrota(gerenciador, estatisticas, fotoFim));
			}
			return;
		}
		if (tempoEntrega >= 0) {
			// a cena final: nada responde; a tela escurece, o professor fala e vem a vitória
			tempoEntrega += diftime / 1000f;
			tempo += diftime / 1000f;
			for (int i = ultimaFalaEntrega + 1; i < FALAS.length && tempoEntrega >= TEMPO_FALAS[i][0]; i++) {
				falarComVoz(FALAS[i]);
				ultimaFalaEntrega = i;
			}
			if (tempoEntrega >= FIM_ENTREGA) {
				fim = Fim.VITORIA;
			}
			return;
		}
		if (tempoSusto >= 0) {
			// o susto: nada responde; quando acaba, a derrota
			tempoSusto += diftime / 1000f;
			tremor = 1;
			if (tempoSusto >= Regras.DURACAO_SUSTO) {
				fim = Fim.DERROTA;
			}
			return;
		}
		if (entrada.foiPressionada(GLFW_KEY_ESCAPE)) {
			gerenciador.sobrepor(new TelaPausa(gerenciador, mundo.seed));
			return;
		}
		if (entrada.foiPressionada(GLFW_KEY_V)) {
			fim = Fim.VITORIA;
			return;
		}
		if (entrada.foiPressionada(GLFW_KEY_X)) {
			fim = Fim.DERROTA;
			return;
		}
		if (entrada.foiPressionada(GLFW_KEY_F3)) {
			mostrarDepuracao = !mostrarDepuracao;
		}
		if (entrada.foiPressionada(GLFW_KEY_F4)) {
			alternarCameraLivre();
		}
		if (entrada.foiPressionada(GLFW_KEY_T)) {
			alternarSomTeste();
		}
		if (entrada.foiPressionada(GLFW_KEY_F)) {
			tutorial.registrar(Tutorial.Acao.LANTERNA);
			lanterna.alternar();
			gerenciador.audio().tocar(cliqueLanterna, 0.8f);
		}
		if (mostrarDepuracao && entrada.foiPressionada(GLFW_KEY_G)) {
			monstro.forcarAparicao();
		}
		if (mostrarDepuracao && entrada.foiPressionada(GLFW_KEY_K)) {
			monstro.matar();
		}
		if (mostrarDepuracao && entrada.foiPressionada(GLFW_KEY_H)) {
			sustos.forcar(SustosFalsos.Tipo.SILHUETA);
			mostrarMensagem("Susto falso: silhueta (só na floresta, sem o monstro)");
		}
		if (mostrarDepuracao && entrada.foiPressionada(GLFW_KEY_J)) {
			sustos.forcar(SustosFalsos.Tipo.GALHO);
			mostrarMensagem("Susto falso: galho (só na floresta, sem o monstro)");
		}
		if (mostrarDepuracao && entrada.foiPressionada(GLFW_KEY_N)) {
			sustos.forcar(SustosFalsos.Tipo.FANTASMA);
			mostrarMensagem("Susto falso: fantasma (na Univali, só no corredor olhando ao longo dele)");
		}
		if (mostrarDepuracao && entrada.foiPressionada(GLFW_KEY_L)) {
			sustos.forcar(SustosFalsos.Tipo.RISADA);
			mostrarMensagem("Susto falso: risada (só na floresta, sem o monstro)");
		}
		if (mostrarDepuracao) {
			ajustarPoseDaArma(entrada);
		}
		if (mostrarDepuracao && entrada.foiPressionada(GLFW_KEY_P)) {
			irParaOProximoObjetivo();
		}
		if (mostrarDepuracao && entrada.foiPressionada(GLFW_KEY_I)) {
			String arquivo = "fase_" + mundo.seed + ".png";
			fase.ImagemFase.salvar(mundo, arquivo);
			mostrarMensagem("Mapa salvo em " + arquivo);
		}

		if (cameraLivre != null) {
			cameraLivre.atualizar(entrada, diftime);
		} else {
			if (monstro.estado() == DragonKnight.Estado.ATACANDO) {
				jogador.comandar(0, 0, false); // paralisado de medo
			} else {
				comandarJogador(entrada);
			}
			jogador.SimulaSe(diftime);
			tocarSonsDoJogador();
			if (monstro.estado() != DragonKnight.Estado.ATACANDO) {
				comandarArma(entrada);
			}
		}
		atualizarMonstro(diftime);
		atualizarArma(entrada, diftime / 1000f);

		tempo += diftime / 1000.0f;
		partida.avancar(diftime / 1000.0f); // o tempo que vale para a nota (a pausa não conta)
		contarQuadros(diftime);

		Camera c = cameraAtual();
		lanterna.atualizar(c, diftime / 1000f);
		// a lanterna está presa embaixo do cano: a luz sai de lá e acompanha o balanço e o recuo
		lanterna.montarNaArma(desenhoEscopeta.lente(), desenhoEscopeta.direcaoDoCano());
		somAmbiente.atualizar(diftime / 1000f, c.x, c.y, c.z);

		// o ouvinte acompanha a câmera: é assim que o OpenAL sabe de que lado vem cada som
		gerenciador.audio().atualizarOuvinte(c.x, c.y, c.z,
				c.frente.x, c.frente.y, c.frente.z, c.cima.x, c.cima.y, c.cima.z);
	}

	/** Clique esquerdo atira, R recarrega, E pega a caixa de munição que estiver perto. */
	private void comandarArma(Entrada entrada) {
		if (entrada.botaoFoiPressionado(GLFW_MOUSE_BUTTON_LEFT) && escopeta.gatilho()) {
			atirar();
		}
		if (entrada.foiPressionada(GLFW_KEY_R)) {
			escopeta.recarregar();
		}
		if (entrada.foiPressionada(GLFW_KEY_E)) {
			tutorial.registrar(Tutorial.Acao.USOU);
			Prova prova = partida.provaAlcancavel(jogador.x, jogador.z);
			CaixaMunicao perto = caixaAlcancavel();
			if (prova != null) {
				coletar(prova);
			} else if (perto != null) {
				escopeta.adicionarReserva(perto.pegar());
				gerenciador.audio().tocar(somCartucho, 1f);
				mostrarMensagem("+" + Regras.CARTUCHOS_POR_CAIXA + " cartuchos");
			} else {
				usarNoPredio();
			}
		}
	}

	/** O que dá para usar com E dentro da Univali, do mais perto ao mais longe. */
	private enum Uso { CRACHA, PORTA_PROFESSOR, PROFESSOR, PORTA_ENTRADA, PORTA_TRANCADA }

	private Uso usoPerto() {
		var p = mundo.predio;
		if (!objetivos.temCracha() && p.perto(jogador.x, jogador.z, p.chaveNoModelo())) return Uso.CRACHA;
		float[] prof = p.professorNoModelo();
		if (objetivos.portaProfessorAberta() && p.perto(jogador.x, jogador.z, new float[] { prof[0], prof[1] })) return Uso.PROFESSOR;
		if (!objetivos.portaProfessorAberta() && p.perto(jogador.x, jogador.z, p.centroPortaProfessor())) return Uso.PORTA_PROFESSOR;
		if (!objetivos.portaEntradaAberta() && p.dentro(jogador.x, jogador.z)
				&& p.perto(jogador.x, jogador.z, p.centroPortaEntrada())) return Uso.PORTA_ENTRADA;
		if (p.salaTrancadaPerto(jogador.x, jogador.z) != null) return Uso.PORTA_TRANCADA;
		return null;
	}

	/** O crachá, as portas e o professor. */
	private void usarNoPredio() {
		Uso uso = usoPerto();
		if (uso == null) return;
		switch (uso) {
		case CRACHA -> {
			objetivos.pegarCracha();
			desenhoPredio.crachaPego = true;
			gerenciador.audio().tocar(somPegarProva, 1f);
			mostrarMensagem("Crachá do professor", 2.5f);
		}
		case PORTA_PROFESSOR -> {
			if (objetivos.abrirPortaProfessor()) {
				mundo.colisao.removerRetangulo(mundo.predio.portaProfessor());
				desenhoPredio.portaProfessorAberta = true;
				gerenciador.audio().tocar(somTelha, 1f);
				mostrarMensagem("A porta abriu.", 2f);
			} else {
				gerenciador.audio().tocar(somVazia, 1f);
				mostrarMensagem("Trancada. Deve haver um crachá em alguma sala.", 3f);
			}
		}
		case PROFESSOR -> falarComProfessor();
		case PORTA_ENTRADA -> {
			gerenciador.audio().tocar(somVazia, 1f);
			mostrarMensagem("A porta da frente está trancada.", 2.5f);
		}
		case PORTA_TRANCADA -> {
			gerenciador.audio().tocar(somVazia, 1f);
			mostrarMensagem(mundo.predio.salaTrancadaPerto(jogador.x, jogador.z).nome() + ": trancada.", 2.5f);
		}
		}
	}

	/**
	 * A voz do professor numa fala: o texto entre aspas vira um sussurro "sem palavras" (sílabas
	 * sintetizadas com o timbre das vogais do texto), saindo da cabeça dele, em 3D. Cada frase é
	 * gerada uma vez (o Audio guarda pelo nome). Falas sem letras ("...") ficam em silêncio.
	 * Devolve a duração da voz, em segundos (0 = sem voz).
	 */
	private float falarComVoz(String fala) {
		int a = fala.indexOf('"'), b = fala.lastIndexOf('"');
		String texto = a >= 0 && b > a ? fala.substring(a + 1, b) : fala;
		if (texto.chars().noneMatch(Character::isLetter)) return 0;
		Audio audio = gerenciador.audio();
		short[] amostras = Sintetizador.voz(44100, texto, 0.6, 7);
		Efeito voz = audio.criarEfeito("voz do professor: " + texto, amostras, 44100);
		float[] prof = mundo.predio.professorNoModelo();
		float[] cabeca = mundo.predio.noMundo(prof[0], 1.3f, prof[1]);
		audio.tocar3D(voz, cabeca[0], cabeca[1], cabeca[2], 1f, false, 3f);
		return amostras.length / 44100f;
	}

	/** Uma frase sussurrada de cada prova não pega, a cada 7–13 s, só se o jogador estiver a menos de 30 m. */
	private void atualizarSussurros(float dt) {
		if (tempoEntrega >= 0) return;
		Camera c = jogador.camera();
		for (Prova p : partida.provas()) {
			if (p.coletada()) continue;
			float espera = esperaSussurro.getOrDefault(p, random.nextFloat() * 3) - dt;
			if (espera <= 0 && Math.hypot(p.x - c.x, p.z - c.z) < ALCANCE_SUSSURRO) {
				sussurros.put(p, gerenciador.audio().tocar3D(somSussurro, p.x, p.y + 0.3f, p.z, VOLUME_SUSSURRO, false, 2.5f));
				espera = SUSSURRO_MINIMO + random.nextFloat() * (SUSSURRO_MAXIMO - SUSSURRO_MINIMO);
			}
			esperaSussurro.put(p, Math.max(espera, 0));
		}
	}

	/** Sem as 3 provas, ele nega (e a porta da frente abre na primeira vez); com as 3, a cena final. */
	private void falarComProfessor() {
		if (partida.todasColetadas()) {
			tempoEntrega = 0;
			lanterna.definirPanico(0);
			for (SomTocando s : sussurros.values()) gerenciador.audio().parar(s);
			return;
		}
		boolean primeira = !objetivos.falouComProfessor();
		risadaPendente = true; // negou: quando o jogador sair da sala, ele ri
		falas.clear();
		falaComVoz = null;
		falas.addAll(objetivos.falarSemAsProvas(partida.faltando()));
		tempoFala = 0;
		if (primeira) {
			mundo.colisao.removerRetangulo(mundo.predio.portaEntrada());
			desenhoPredio.portaEntradaAberta = true;
		}
	}

	/**
	 * Pegou uma prova: o sussurro dela para, o HUD acende, e o monstro reage (fica mais
	 * frequente e vem em no máximo 15 s). Na terceira, o sinal de aula toca na Univali.
	 */
	private void coletar(Prova prova) {
		if (!prova.coletar()) return;
		Audio audio = gerenciador.audio();
		audio.parar(sussurros.remove(prova));
		audio.tocar(somPegarProva, 1f);
		pulsoProva.put(prova, 0f);
		monstro.provasColetadas = partida.provasColetadas();
		monstro.apressarProximaAparicao(Regras.APARICAO_DEPOIS_DA_PROVA);
		if (partida.todasColetadas()) {
			float[] u = mundo.univali;
			// de longe (referência de 25 m), para o jogador saber para que lado ir
			audio.tocar3D(somSinal, u[0], mundo.chao.alturaEm(u[0], u[1]) + 6, u[1], 1f, false, 25f);
			mostrarMensagem(prova.nome + " encontrada. Leve as provas ao professor", 5f);
		} else {
			mostrarMensagem(prova.nome + " encontrada", 2.5f);
		}
	}

	/** Depuração: põe o jogador a 3 m da próxima prova que falta. */
	private void irParaAProximaProva() {
		for (Prova p : partida.provas()) {
			if (!p.coletada()) {
				jogador.x = p.x - 3;
				jogador.z = p.z;
				return;
			}
		}
		// todas pegas: vai para a frente da mesa do professor
		irPara(mundo.predio.professorNoModelo(), 1.9f);
	}

	/** Depuração (F3 + P): o crachá, depois a porta da sala, o professor, as provas e o professor de novo. */
	private void irParaOProximoObjetivo() {
		var p = mundo.predio;
		if (!objetivos.temCracha()) {
			irPara(p.chaveNoModelo(), 1.2f);
		} else if (!objetivos.portaProfessorAberta()) {
			irPara(fase.AndarUnivali.noCorredor(p.andar.professores()), 0);
		} else if (!objetivos.falouComProfessor()) {
			irPara(p.professorNoModelo(), 1.9f);
		} else {
			irParaAProximaProva();
		}
	}

	/** Põe o jogador a "frente" metros de um ponto do modelo do prédio, na direção da porta da sala dele. */
	private void irPara(float[] noModelo, float frente) {
		float[] prof = mundo.predio.professorNoModelo();
		float dx = (float) Math.sin(prof[2]), dz = (float) Math.cos(prof[2]);
		float[] w = mundo.predio.noMundo(noModelo[0] + dx * frente, 0, noModelo[1] + dz * frente);
		jogador.x = w[0];
		jogador.z = w[2];
	}

	private CaixaMunicao caixaAlcancavel() {
		for (CaixaMunicao c : caixas) {
			if (c.alcancavel(jogador.x, jogador.z)) return c;
		}
		return null;
	}

	/**
	 * O tiro saiu: os 8 bagos partem do centro da câmera (para a mira ser fiel), e cada um
	 * deixa o seu efeito onde parou. A chama, o clarão, o tremor e a fumaça saem da arma.
	 */
	private void atirar() {
		tutorial.registrar(Tutorial.Acao.ATIROU);
		Camera c = jogador.camera();
		float[] origem = { c.x, c.y, c.z };
		float[] frente = { c.frente.x, c.frente.y, c.frente.z };
		float[] direita = { c.direita.x, c.direita.y, c.direita.z };
		float[] cima = { c.cima.x, c.cima.y, c.cima.z };
		// só vale contar os bagos se ele ainda pode levar dano (não conta o corpo caído)
		boolean atingivel = monstro.estado() == DragonKnight.Estado.SURGINDO
				|| monstro.estado() == DragonKnight.Estado.ENCARANDO;
		Tiro.Resultado r = Tiro.disparar(origem, frente, direita, cima, monstro, mundo.colisao, mundo.chao, random);
		partida.registrarTiro(Regras.BAGOS, r.acertos());
		if (atingivel) {
			System.out.println("[Tiro] " + r.acertos() + " de " + Regras.BAGOS + " bagos acertaram o monstro");
		}
		for (Tiro.Impacto i : r.impactos()) {
			switch (i.alvo()) {
			case MONSTRO -> particulas.sangue(i.x(), i.y(), i.z(), 5);
			case TRONCO -> {
				particulas.faiscas(i.x(), i.y(), i.z(), 3);
				particulas.poeira(i.x(), i.y(), i.z(), 3);
			}
			case CHAO -> {
				particulas.poeira(i.x(), i.y(), i.z(), 4);
				particulas.faiscas(i.x(), i.y(), i.z(), 1);
			}
			}
		}
		gerenciador.audio().tocar(somTiro, 1f);
		desenhoEscopeta.disparar();
		tremor = Math.max(tremor, 0.45f);
		fumacaRestante = 1f;
	}

	/** A pose da arma, a chama, a fumaça subindo do cano, o clarão e as partículas. */
	private void atualizarArma(Entrada entrada, float dt) {
		boolean podeMexer = cameraLivre == null;
		escopeta.atualizar(dt, podeMexer ? entrada.deltaMouse().x : 0, podeMexer ? entrada.deltaMouse().y : 0,
				jogador.fasePasso(), jogador.velocidade(), jogador.correndo());
		desenhoEscopeta.atualizarPose(escopeta);
		desenhoEscopeta.atualizar(dt);
		particulas.atualizar(dt);
		mundo.predio.SimulaSe(Math.round(dt * 1000));
		desenhoPredio.atualizar(dt, jogador.x, jogador.z);
		// depois da negativa, quando o jogador sai da sala do professor, ele ri (lá da mesa)
		fase.AndarUnivali.Sala sala = mundo.predio.andar.professores();
		float[] noModelo = mundo.predio.paraModelo(jogador.x, jogador.z);
		boolean naSala = noModelo[0] > sala.xMin() && noModelo[0] < sala.xMax() && noModelo[1] > sala.zMin()
				&& noModelo[1] < sala.zMax();
		if (naSalaDoProfessor && !naSala && risadaPendente && somRisadaProfessor != null) {
			float[] prof = mundo.predio.professorNoModelo();
			float[] cabeca = mundo.predio.noMundo(prof[0], 1.3f, prof[1]);
			gerenciador.audio().tocar3D(somRisadaProfessor, cabeca[0], cabeca[1], cabeca[2], 1f, false, 4f);
			risadaPendente = false;
		}
		naSalaDoProfessor = naSala;
		// as falas do professor, uma de cada vez, cada uma com a voz dele
		if (!falas.isEmpty() && falas.peek() != falaComVoz) {
			falaComVoz = falas.peek();
			duracaoFala = Math.max(DURACAO_MINIMA_FALA, falarComVoz(falaComVoz) + 0.6f); // a fala fica até a voz acabar
		}
		if (!falas.isEmpty()) {
			tempoFala += dt;
			if (tempoFala >= duracaoFala) {
				falas.poll();
				tempoFala = 0;
			}
		}
		// o Dragon Knight acorda na primeira vez que o jogador põe os pés fora do prédio
		if (!monstro.ativo && !mundo.predio.dentro(jogador.x, jogador.z)) {
			monstro.ativo = true;
			tutorial.registrar(Tutorial.Acao.SAIU);
			gerenciador.audio().parar(somZumbido); // lá fora o zumbido do saguão não faz falta
			somZumbido = null;
		}
		andado += (float) Math.hypot(jogador.x - ultimoX, jogador.z - ultimoZ);
		ultimoX = jogador.x;
		ultimoZ = jogador.z;
		if (andado > 2) tutorial.registrar(Tutorial.Acao.ANDOU);
		tutorial.atualizar(dt);
		tempoMensagem = Math.max(0, tempoMensagem - dt);
		for (Prova p : partida.provas()) {
			p.SimulaSe(Math.round(dt * 1000));
		}
		atualizarSussurros(dt);
		pulsoProva.replaceAll((p, t) -> t + dt);

		// o clarão: uma luz forte na boca do cano por ~80 ms, que ilumina a arma e as árvores
		float t = escopeta.tempoDesdeOTiro();
		ambiente.intensidadeClarao = t < 0.08f ? 4f * (1 - t / 0.08f) : 0;
		float[] boca = desenhoEscopeta.boca();
		System.arraycopy(boca, 0, ambiente.posClarao, 0, 3);

		// a fumaça nasce na boca do cano, levada ao mundo pela base da câmera, e fica para trás
		if (fumacaRestante > 0) {
			fumacaRestante -= dt;
			Camera c = jogador.camera();
			float x = c.x + c.direita.x * boca[0] + c.cima.x * boca[1] - c.frente.x * boca[2];
			float y = c.y + c.direita.y * boca[0] + c.cima.y * boca[1] - c.frente.y * boca[2];
			float z = c.z + c.direita.z * boca[0] + c.cima.z * boca[1] - c.frente.z * boca[2];
			if (random.nextFloat() < 0.5f) particulas.fumaca(x, y, z, 1);
		}
	}

	/** Com o F3: setas movem a arma em x e y, Page Up/Down em z; o console mostra os valores. */
	private void ajustarPoseDaArma(Entrada entrada) {
		float passo = 0.005f;
		float[] d = desenhoEscopeta.descanso;
		boolean mudou = true;
		if (entrada.foiPressionadaOuRepetida(GLFW_KEY_LEFT)) d[0] -= passo;
		else if (entrada.foiPressionadaOuRepetida(GLFW_KEY_RIGHT)) d[0] += passo;
		else if (entrada.foiPressionadaOuRepetida(GLFW_KEY_UP)) d[1] += passo;
		else if (entrada.foiPressionadaOuRepetida(GLFW_KEY_DOWN)) d[1] -= passo;
		else if (entrada.foiPressionadaOuRepetida(GLFW_KEY_PAGE_UP)) d[2] -= passo;
		else if (entrada.foiPressionadaOuRepetida(GLFW_KEY_PAGE_DOWN)) d[2] += passo;
		else mudou = false;
		if (mudou) {
			System.out.println("[Arma] " + desenhoEscopeta.poseComoTexto());
		}
	}

	private void mostrarMensagem(String texto) {
		mostrarMensagem(texto, 2f);
	}

	private void mostrarMensagem(String texto, float segundos) {
		mensagem = texto;
		mensagem2 = null;
		tempoMensagem = segundos;
	}

	/** O monstro, o batimento, a lanterna em pânico e, no fim do ataque, o susto. */
	/**
	 * Os sustos falsos, com o monstro fora de cena. Na floresta: o fantasma passando entre as
	 * árvores, a silhueta (na borda do facho, some se a luz a pegar, se o jogador chegar perto, se
	 * o monstro aparecer ou depois de um tempo), a risada longe e o galho atrás. Na Univali: o
	 * fantasma atravessando o corredor mais à frente.
	 */
	private void atualizarSustosFalsos(float dt) {
		boolean dentro = mundo.predio.dentro(jogador.x, jogador.z);
		SustosFalsos.Lugar lugar = tempoEntrega >= 0 ? null
				: dentro ? SustosFalsos.Lugar.PREDIO : monstro.ativo ? SustosFalsos.Lugar.FLORESTA : null;
		boolean monstroEmCena = monstro.estado() != DragonKnight.Estado.AUSENTE;
		Camera c = jogador.camera();
		if (fantasma != null) {
			if (monstroEmCena) fantasma.sumir();
			fantasma.atualizar(dt, c.x, c.z);
			if (fantasma.acabou()) fantasma = null;
			return;
		}
		if (silhueta != null) {
			silhueta[4] += dt;
			float d = (float) Math.hypot(silhueta[0] - c.x, silhueta[2] - c.z);
			if (monstroEmCena || lugar != SustosFalsos.Lugar.FLORESTA || d < SustosFalsos.SILHUETA_SOME_PERTO
					|| silhueta[4] > SustosFalsos.SILHUETA_DURACAO
					|| SustosFalsos.iluminado(lanterna.ligada(), c.x, c.z, c.frente.x, c.frente.z, silhueta[0], silhueta[2])) {
				silhueta = null;
			}
			return;
		}
		// um fantasma da Univali que ainda espera o jogador estar no corredor, olhando ao longo dele
		if (esperaFantasmaPredio > 0) {
			esperaFantasmaPredio -= dt;
			if (dentro && !monstroEmCena) fantasma = fantasmaNoCorredor(c);
			if (fantasma != null || !dentro) esperaFantasmaPredio = 0;
			return;
		}
		SustosFalsos.Tipo tipo = sustos.atualizar(dt, lugar, monstroEmCena);
		if (tipo == null) return;
		Random r = sustos.random();
		float olhando = (float) Math.atan2(c.frente.x, c.frente.z);
		if (tipo == SustosFalsos.Tipo.FANTASMA && dentro) {
			esperaFantasmaPredio = 25; // tenta por até 25 s
		} else if (tipo == SustosFalsos.Tipo.FANTASMA) {
			fantasma = fantasmaNaFloresta(c, olhando, r);
		} else if (tipo == SustosFalsos.Tipo.SILHUETA) {
			for (int tentativa = 0; tentativa < 20 && silhueta == null; tentativa++) {
				// na borda do facho da lanterna, para um dos lados: vista de relance, meio iluminada
				float desvio = SustosFalsos.BORDA_MINIMA + r.nextFloat() * (SustosFalsos.BORDA_MAXIMA - SustosFalsos.BORDA_MINIMA);
				float angulo = olhando + (r.nextBoolean() ? 1 : -1) * (float) Math.toRadians(desvio);
				float d = SustosFalsos.SILHUETA_PERTO + r.nextFloat() * (SustosFalsos.SILHUETA_LONGE - SustosFalsos.SILHUETA_PERTO);
				float x = c.x + (float) Math.sin(angulo) * d, z = c.z + (float) Math.cos(angulo) * d;
				if (!lugarLivreNaFloresta(c, x, z) || mundo.colisao.colide(x, z, 0.5f)) continue;
				silhueta = new float[] { x, mundo.chao.alturaEm(x, z), z, (float) Math.atan2(c.x - x, c.z - z), 0 };
			}
		} else if (tipo == SustosFalsos.Tipo.RISADA && somRisada != null) {
			// uma risada longe, de um lado qualquer, entre as árvores
			float angulo = r.nextFloat() * (float) (2 * Math.PI);
			float d = 25 + r.nextFloat() * 10;
			float x = c.x + (float) Math.sin(angulo) * d, z = c.z + (float) Math.cos(angulo) * d;
			gerenciador.audio().tocar3D(somRisada, x, mundo.chao.alturaEm(x, z) + 1.6f, z, 1f, false, 8f);
		} else if (tipo != SustosFalsos.Tipo.SILHUETA) { // o galho (ou a risada sem o arquivo)
			float angulo = olhando + (float) Math.PI + (r.nextFloat() - 0.5f) * 1.0f; // atrás, até 30 graus para o lado
			float d = 4 + r.nextFloat() * 3;
			float x = c.x + (float) Math.sin(angulo) * d, z = c.z + (float) Math.cos(angulo) * d;
			gerenciador.audio().tocar3D(somGalho, x, mundo.chao.alturaEm(x, z) + 0.3f, z, 1f, false, 3f);
		}
	}

	/**
	 * Um ponto da floresta onde dá para pôr um susto: no mapa, fora da água e do prédio, sem tronco
	 * no caminho e sem morro na frente (a linha dos olhos até 40 cm acima do chão de lá passa por
	 * cima do terreno em todo o caminho: assim aparece o corpo inteiro, e não só a cabeça).
	 */
	private boolean lugarLivreNaFloresta(Camera c, float x, float z) {
		if (!mundo.chao.dentroDoMapa(x, z, 25) || mundo.chao.alturaEm(x, z) < 0.3f || mundo.predio.dentro(x, z)
				|| !mundo.colisao.linhaLivre(c.x, c.z, x, z)) {
			return false;
		}
		float alvoY = mundo.chao.alturaEm(x, z) + 0.4f;
		for (float t = 0.1f; t < 1; t += 0.05f) {
			float px = c.x + (x - c.x) * t, pz = c.z + (z - c.z) * t, py = c.y + (alvoY - c.y) * t;
			if (mundo.chao.alturaEm(px, pz) > py - 0.1f) return false;
		}
		return true;
	}

	/**
	 * O fantasma da floresta: de 14 a 20 m, num lugar aberto, até 25 graus para o lado de onde o jogador olha (para
	 * ser visto), atravessando a vista de lado, devagar, por 10 a 14 s. Um sussurro sai dele.
	 */
	private Fantasma fantasmaNaFloresta(Camera c, float olhando, Random r) {
		for (int tentativa = 0; tentativa < 20; tentativa++) {
			float angulo = olhando + (r.nextFloat() * 2 - 1) * (float) Math.toRadians(25);
			float d = 14 + r.nextFloat() * 6;
			float x = c.x + (float) Math.sin(angulo) * d, z = c.z + (float) Math.cos(angulo) * d;
			// num lugar aberto: os galhos de baixo dos pinheiros escondiam o fantasma inteiro
			if (!lugarLivreNaFloresta(c, x, z) || mundo.colisao.colide(x, z, 2f) || !mundo.colisao.linhaLivre(c.x, c.z, x, z, 0.8f)) {
				continue;
			}
			float lado = r.nextBoolean() ? 1 : -1; // anda para a esquerda ou para a direita de quem olha
			gerenciador.audio().tocar3D(somSussurro, x, mundo.chao.alturaEm(x, z) + 1.5f, z, 0.35f, false, 3f);
			return new Fantasma(x, z, (float) Math.cos(angulo) * lado, -(float) Math.sin(angulo) * lado, 1.1f,
					10 + r.nextFloat() * 4);
		}
		return null;
	}

	/**
	 * O fantasma da Univali: só com o jogador no corredor olhando ao longo dele. Atravessa o
	 * corredor de 9 a 15 m à frente, de uma parede à outra (sai de uma sala e entra na da frente).
	 */
	private Fantasma fantasmaNoCorredor(Camera c) {
		entidades.PredioUnivali p = mundo.predio;
		float[] m = p.paraModelo(c.x, c.z);
		float[] olhar = p.direcaoParaModelo(c.frente.x, c.frente.z);
		float t = (float) Math.hypot(olhar[0], olhar[1]);
		if (Math.abs(m[1]) > fase.AndarUnivali.MEIO_CORREDOR || t < 1e-4f || Math.abs(olhar[0] / t) < 0.75f) return null;
		Random r = sustos.random();
		float gx = m[0] + Math.signum(olhar[0]) * (9 + r.nextFloat() * 6);
		float limite = -fase.AndarUnivali.X0 - 2;
		if (Math.abs(gx) > limite) return null; // fora do fim do corredor
		float lado = r.nextBoolean() ? 1 : -1;
		float[] de = p.noMundo(gx, 0, 2.6f * lado), para = p.noMundo(gx, 0, -2.6f * lado);
		gerenciador.audio().tocar3D(somSussurro, de[0], p.y + 1.5f, de[2], 0.3f, false, 3f);
		return new Fantasma(de[0], de[2], para[0] - de[0], para[2] - de[2], 0.9f, 5.8f + Fantasma.SAIDA);
	}

	private void atualizarMonstro(long diftime) {
		float dt = diftime / 1000f;
		Camera olhos = jogador.camera(); // ele mira o jogador, mesmo com a câmera livre ligada
		monstro.definirAlvo(olhos.x, olhos.y, olhos.z, olhos.frente.x, olhos.frente.z);
		monstro.lanternaLigada = lanterna.ligada(); // a luz o atrai mais cedo
		monstro.SimulaSe(diftime);
		tremor = Math.max(0, tremor - dt * 2.5f);
		if (metalRestante > 0) { // a música está tocando: anda junto, para a próxima aparição continuar dela
			segundosMetal += dt;
			metalRestante -= dt;
		}

		DragonKnight.Estado estado = monstro.estado();
		boolean ameacando = estado == DragonKnight.Estado.SURGINDO || estado == DragonKnight.Estado.ENCARANDO;
		float progresso = progressoContagem();
		lanterna.definirPanico(ameacando ? 0.3f + 0.7f * progresso : 0);
		float presenca = switch (estado) {
		case SURGINDO -> 0.6f;
		case ENCARANDO -> 0.45f + 0.55f * progresso;
		case ATACANDO -> 1f;
		default -> 0f;
		};
		presencaMonstro += (presenca - presencaMonstro) * Math.min(1, dt * 4);
		atualizarSustosFalsos(dt);
		if (ameacando && !temMusicaMonstro) {
			// batimento acelerando: de 0,8 s entre batidas até 0,3 s no fim da contagem
			proximaBatida -= dt;
			if (proximaBatida <= 0) {
				gerenciador.audio().tocar(somBatida, 0.6f + 0.4f * progresso);
				proximaBatida = 0.8f - 0.5f * progresso;
			}
		} else {
			proximaBatida = 0;
		}
		if (monstro.ataqueTerminou()) {
			comecarSusto();
		}
	}

	/** De 0 (a contagem acabou de começar) a 1 (acabou). Fora da contagem, 0. */
	private float progressoContagem() {
		if (monstro.estado() != DragonKnight.Estado.ENCARANDO) return 0;
		return 1 - monstro.contagem() / Regras.TEMPO_CONTAGEM;
	}

	/**
	 * O susto: som alto, tremor e, sem a imagem sprites/susto.png, a cabeça do dragão parada a
	 * 70 cm dos olhos do jogador, olhando para ele (o "modelo em close").
	 */
	private void comecarSusto() {
		tempoSusto = 0;
		tremor = 1;
		lanterna.definirPanico(0);
		gerenciador.audio().tocar(somSusto, 1f);

		Camera c = jogador.camera();
		float fx = c.frente.x, fz = c.frente.z;
		float t = (float) Math.sqrt(fx * fx + fz * fz);
		if (t < 1e-4f) { fx = 1; fz = 0; t = 1; }
		fx /= t;
		fz /= t;
		monstro.guinada = DragonKnight.guinadaPara(-fx, -fz); // a frente dele aponta para o jogador
		float[] olho = desenhoMonstro.olhoLocal(monstro);
		float cg = (float) Math.cos(monstro.guinada), sg = (float) Math.sin(monstro.guinada);
		// onde o olho deve ficar, menos o olho girado (o mesmo giro do modelo) = onde ficam os pés
		monstro.x = c.x + fx * 0.7f - (cg * olho[0] + sg * olho[2]);
		monstro.z = c.z + fz * 0.7f - (-sg * olho[0] + cg * olho[2]);
		monstro.y = c.y - olho[1];
	}

	/** Lê o teclado e o mouse e passa os comandos para o jogador. */
	private void comandarJogador(Entrada entrada) {
		float frente = 0, lado = 0;
		if (entrada.estaPressionada(GLFW_KEY_W)) frente += 1;
		if (entrada.estaPressionada(GLFW_KEY_S)) frente -= 1;
		if (entrada.estaPressionada(GLFW_KEY_D)) lado += 1;
		if (entrada.estaPressionada(GLFW_KEY_A)) lado -= 1;
		boolean correr = entrada.estaPressionada(GLFW_KEY_LEFT_SHIFT) || entrada.estaPressionada(GLFW_KEY_RIGHT_SHIFT);
		jogador.comandar(frente, lado, correr);

		// mouse: o y da tela cresce para baixo, por isso o sinal invertido na arfagem (a não ser
		// que o jogador peça o eixo invertido nas opções)
		float sensibilidade = CameraLivre.SENSIBILIDADE_MOUSE * Opcoes.atual().sensibilidade;
		float sinalY = Opcoes.atual().inverterY ? 1 : -1;
		jogador.girar(entrada.deltaMouse().x * sensibilidade, sinalY * entrada.deltaMouse().y * sensibilidade);
	}

	/**
	 * Passos a cada passo dado, com o som do chão (folhas, terra, água ou o piso da Univali) e
	 * volume variando um pouco; respiração em loop com estamina baixa.
	 */
	private void tocarSonsDoJogador() {
		Audio audio = gerenciador.audio();
		if (jogador.passosNoQuadro() > 0) {
			MaterialChao material = MaterialChao.em(mundo.chao, jogador.x, jogador.z,
					mundo.predio.dentro(jogador.x, jogador.z), naTrilha(jogador.x, jogador.z));
			Efeito[] variacoes = jogador.correndo() && passosCorrendo.containsKey(material) ? passosCorrendo.get(material)
					: passos.get(material);
			for (int i = 0; i < jogador.passosNoQuadro(); i++) {
				float volume = (jogador.correndo() ? 0.5f : 0.35f) * (0.85f + random.nextFloat() * 0.3f)
						* (material == MaterialChao.AGUA ? 0.8f : 1f);
				audio.tocar(variacoes[proximaVariacao(variacoes.length)], volume);
			}
		}
		if (jogador.ofegante() && !audio.estaTocando(somRespiracao)) {
			somRespiracao = audio.tocar(respiracao, 0.7f);
		}
		if (!jogador.ofegante() && somRespiracao != null && jogador.estamina() > 0.5f) {
			audio.parar(somRespiracao); // só para quando já recuperou bem (não fica liga-desliga)
			somRespiracao = null;
		}
	}

	/** Sorteia a variação do passo, sem repetir a anterior (dois passos iguais seguidos soam de máquina). */
	private int ultimaVariacao = -1;

	private int proximaVariacao(int quantas) {
		if (quantas <= 1) return 0;
		if (ultimaVariacao < 0 || ultimaVariacao >= quantas) { // primeiro passo, ou o chão mudou
			ultimaVariacao = random.nextInt(quantas);
			return ultimaVariacao;
		}
		int v = random.nextInt(quantas - 1); // uma das outras
		if (v >= ultimaVariacao) v++;
		ultimaVariacao = v;
		return v;
	}

	/** O ponto está numa trilha? (Os pontos delas ficam a cada ~1 m: basta estar perto de um.) */
	private boolean naTrilha(float x, float z) {
		float alcance = mundo.fase.trilhas().isEmpty() ? 0 : 1.3f;
		for (List<fase.Ponto> t : mundo.fase.trilhas()) {
			for (fase.Ponto p : t) {
				float dx = p.x() - x, dz = p.z() - z;
				if (dx * dx + dz * dz < alcance * alcance) return true;
			}
		}
		return false;
	}

	private void alternarCameraLivre() {
		if (cameraLivre == null) {
			Camera c = jogador.camera();
			cameraLivre = new CameraLivre(c.x, c.y, c.z, c.guinada, c.arfagem);
		} else {
			cameraLivre = null;
		}
	}

	/** Liga ou desliga um bipe em loop 10 m à direita do jogador (teste do som 3D). */
	private void alternarSomTeste() {
		Audio audio = gerenciador.audio();
		if (audio.estaTocando(somTeste)) {
			audio.parar(somTeste);
			somTeste = null;
			return;
		}
		Camera c = cameraAtual();
		// um "ping" gerado por código, repetido a cada 0,7 s: não depende de arquivo
		Efeito bipe = audio.criarEfeito("bipe de teste", Sintetizador.ping(44100, 880, 120, 700), 44100);
		somTeste = audio.tocar3D(bipe, c.x + c.direita.x * 10, c.y, c.z + c.direita.z * 10, 1f, true);
	}

	private void contarQuadros(long diftime) {
		quadrosContados++;
		tempoContado += diftime / 1000f;
		if (tempoContado >= 0.5f) {
			fps = quadrosContados / tempoContado;
			quadrosContados = 0;
			tempoContado = 0;
		}
	}

	private Camera cameraAtual() {
		return cameraLivre != null ? cameraLivre : jogador.camera();
	}

	// ---------- desenho ----------

	@Override
	public void desenhar() {
		Matrix4f visao = visaoComTremor();
		lanterna.aplicar(ambiente);
		desenharMapaDeSombra(visao); // antes da cena: o que a lanterna enxerga

		// a cena vai para uma imagem à parte: pequena no modo PS1, do tamanho da janela fora dele
		filtroPs1.comecar(gerenciador.largura(), gerenciador.altura(), ps1);
		if (ps1) {
			ambiente.resolucaoPs1[0] = filtroPs1.largura();
			ambiente.resolucaoPs1[1] = filtroPs1.altura();
		}
		ambiente.modoPs1 = ps1 ? 1 : 0;

		// fundo da mesma cor da neblina: o que some na neblina se funde com o céu
		glClearColor(ambiente.corCeu[0], ambiente.corCeu[1], ambiente.corCeu[2], 1.0f);
		glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
		glEnable(GL_DEPTH_TEST);

		guardar(projecao(), bufferProjecao);
		guardar(visao, bufferVisao);
		luzesDosPostes(visao);
		desenharTerreno();
		if (mundo.arvores3d != null) {
			desenharArvores3D();
		} else {
			desenharArvores();
		}
		desenharPedras();
		if (silhueta != null) {
			desenhoSilhueta.desenhar(silhueta[0], silhueta[1], silhueta[2], silhueta[3], bufferProjecao, bufferVisao, ambiente);
		}
		Camera camera = cameraAtual();
		int alturaImagem = filtroPs1.altura();
		desenhoMonstro.desenhar(monstro, bufferProjecao, bufferVisao, ambiente, camera.x, camera.z, alturaImagem);
		desenharCaixas();
		desenhoPlacas.desenhar(mundo.placas, mundo.chao, bufferProjecao, bufferVisao, ambiente);
		desenhoPredio.desenhar(bufferProjecao, bufferVisao, ambiente, alturaImagem);
		desenhoProvas.desenhar(partida.provas(), bufferProjecao, bufferVisao, ambiente, alturaImagem);
		particulas.desenhar(bufferProjecao, bufferVisao, ambiente, alturaImagem);
		if (fantasma != null) { // translúcido: depois das coisas opacas
			float chaoFantasma = mundo.predio.dentro(fantasma.x, fantasma.z) ? mundo.predio.y : mundo.chao.alturaEm(fantasma.x, fantasma.z);
			desenhoFantasma.desenhar(fantasma.x, chaoFantasma, fantasma.z, fantasma.alfa(), fantasma.tempo(), camera.x, camera.z,
					bufferProjecao, bufferVisao, ambiente);
		}
		desenhoProvas.desenharFeixes(partida.provas(), camera.x, camera.z, bufferProjecao, bufferVisao, ambiente);
		if (tempoSusto < 0) { // no susto, só a cara dele
			desenhoEscopeta.desenhar(escopeta, ambiente, gerenciador.proporcao(), alturaImagem);
		}
		// a passagem para a tela: amplia (modo PS1), escurece os cantos, granula e, com o monstro, distorce
		filtroPs1.vinheta = 1;
		filtroPs1.granulacao = 1;
		filtroPs1.aberracao = presencaMonstro;
		filtroPs1.tempo = tempo;
		filtroPs1.terminar(gerenciador.largura(), gerenciador.altura());
		if (fim == Fim.DERROTA && fotoFim == null) {
			fotoFim = fotografar(); // antes do HUD: só a cena
		}

		Overlay2D overlay = Recursos.overlay();
		overlay.iniciar(gerenciador.larguraJanela(), gerenciador.alturaJanela());
		desenharEfeitosMonstro(overlay);
		if (tempoSusto < 0 && fim == null && tempoEntrega < 0) {
			desenharHudArma(overlay);
		}
		if (tempoEntrega >= 0) {
			desenharEntrega(overlay);
		}
		if (mostrarDepuracao) {
			desenharDepuracao(overlay);
		} else if (tempo < 8) {
			Texto.desenhar(overlay, "F3: depuração", 16, gerenciador.alturaJanela() - 30, 14, COR_DICA,
					Texto.Alinhamento.ESQUERDA);
		}
		overlay.finalizar();
	}

	/**
	 * A passada de profundidade da sombra: desenha, do ponto de vista da lanterna, tudo o que faz
	 * sombra (terreno, árvores, pedras, monstro, caixas, placas e o prédio; a escopeta não, a sombra
	 * da própria arma na cena ficaria errada). Usa os mesmos desenhos e shaders da cena, com as
	 * matrizes da luz: a posição de cada vértice sai igual nas duas passadas (até a animação do
	 * monstro e o vento nas árvores), e os fragment shaders só descartam o recorte das folhas e saem.
	 * Só com a lanterna ligada e a opção de sombra ligada.
	 */
	private void desenharMapaDeSombra(Matrix4f visao) {
		ambiente.usaSombra = 0;
		if (ladoSombra == 0 || !lanterna.ligada() || cameraLivre != null) {
			return;
		}
		sombra.preparar(ladoSombra);
		sombra.calcular(visao, lanterna, ambiente);
		bufferProjecao.clear();
		bufferProjecao.put(sombra.projecaoLuz.rewind()).flip();
		bufferVisao.clear();
		bufferVisao.put(sombra.visaoLuz.rewind()).flip();
		sombra.projecaoLuz.rewind();
		sombra.visaoLuz.rewind();
		float modo = ambiente.modoPs1;
		ambiente.modoPs1 = 0; // sem o tremido do PS1: a profundidade precisa da posição exata

		sombra.comecar(ambiente);
		desenharTerreno();
		if (mundo.arvores3d != null) {
			desenharArvores3D();
		} else {
			desenharArvores();
		}
		desenharPedras();
		Camera camera = cameraAtual();
		desenhoMonstro.desenhar(monstro, bufferProjecao, bufferVisao, ambiente, camera.x, camera.z, ladoSombra);
		desenharCaixas();
		desenhoPlacas.desenhar(mundo.placas, mundo.chao, bufferProjecao, bufferVisao, ambiente);
		desenhoPredio.desenhar(bufferProjecao, bufferVisao, ambiente, ladoSombra);
		sombra.terminar(ambiente);

		ambiente.modoPs1 = modo;
	}

	/** As caixas de munição: o cubo do projeto original achatado, com a textura da caixa e um brilho leve. */
	private void desenharCaixas() {
		ShaderObjeto shader = Recursos.shaderObjeto();
		shader.start();
		int p = shader.programID;
		glUniformMatrix4fv(glGetUniformLocation(p, "projection"), false, bufferProjecao);
		glUniformMatrix4fv(glGetUniformLocation(p, "view"), false, bufferVisao);
		glUniform1f(glGetUniformLocation(p, "emissividade"), 0.12f); // para ser achada com a lanterna
		glUniform1i(glGetUniformLocation(p, "tex"), 0);
		glUniform1i(glGetUniformLocation(p, "usaNormalMap"), 0);
		ambiente.enviar(shader);
		glActiveTexture(GL_TEXTURE0);
		glBindTexture(GL_TEXTURE_2D, texturaCaixa());
		FloatBuffer modelo = BufferUtils.createFloatBuffer(16);
		for (CaixaMunicao c : caixas) {
			if (c.pega()) continue;
			Matrix4f m = new Matrix4f();
			m.translate(new Vector3f(c.x, c.y + 0.15f, c.z));
			m.rotate(c.giro, new Vector3f(0, 1, 0));
			m.scale(new Vector3f(0.25f, 0.15f, 0.18f)); // o cubo vai de -1 a 1: caixa de 50 x 30 x 36 cm
			modelo.clear();
			m.storeTranspose(modelo);
			modelo.flip();
			glUniformMatrix4fv(glGetUniformLocation(p, "model"), false, modelo);
			// o cubo não tem normal: com o atributo 2 desligado e zerado, o shader usa a normal da face
			glDisableVertexAttribArray(2);
			glVertexAttrib3f(2, 0, 0, 0);
			cuboCaixa.draw();
		}
		glDisableVertexAttribArray(1);
		shader.stop();
	}

	/** texturas/caixa_municao.png, ou uma caixa verde-oliva com "CARTUCHOS" escrito, gerada por código. */
	private static int texturaCaixa() {
		if (Caminhos.existe("texturas/caixa_municao.png")) {
			return Recursos.textura("texturas/caixa_municao.png");
		}
		return Recursos.texturaGerada("caixa de munição", () -> {
			java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(64, 64,
					java.awt.image.BufferedImage.TYPE_INT_ARGB);
			java.awt.Graphics2D g = img.createGraphics();
			g.setColor(new Color(62, 70, 38));
			g.fillRect(0, 0, 64, 64);
			g.setColor(new Color(40, 46, 24));
			g.drawRect(1, 1, 61, 61);
			g.setColor(new Color(170, 40, 30));
			g.fillRect(6, 22, 52, 20);
			g.setColor(new Color(235, 220, 190));
			g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 9));
			g.drawString("CARTUCHOS", 8, 35);
			g.dispose();
			return img;
		}, false, true).id();
	}

	/** Mira no centro, munição no canto inferior direito, mensagens e o aviso de "E" perto de uma caixa. */
	private void desenharHudArma(Overlay2D o) {
		int w = gerenciador.larguraJanela(), h = gerenciador.alturaJanela();
		float cx = w / 2f, cy = h / 2f;
		Color mira = new Color(230, 225, 215, 150);
		o.retangulo(cx - 9, cy - 1, 6, 2, mira);
		o.retangulo(cx + 3, cy - 1, 6, 2, mira);
		o.retangulo(cx - 1, cy - 9, 2, 6, mira);
		o.retangulo(cx - 1, cy + 3, 2, 6, mira);

		// os cartuchos no cano (vermelho com a base de latão) e a reserva
		float escala = Math.max(1f, h / 800f);
		float largura = 12 * escala, altura = 30 * escala;
		float x = w - 40 * escala - (largura + 6 * escala) * Regras.CAPACIDADE_CANO - 60 * escala;
		float y = h - 30 * escala - altura;
		for (int i = 0; i < Regras.CAPACIDADE_CANO; i++) {
			boolean cheio = i < escopeta.cano();
			float xi = x + i * (largura + 6 * escala);
			o.retangulo(xi, y, largura, altura * 0.72f, cheio ? new Color(150, 22, 18) : new Color(60, 55, 50, 120));
			o.retangulo(xi, y + altura * 0.72f, largura, altura * 0.28f, cheio ? new Color(190, 150, 60) : new Color(60, 55, 50, 120));
		}
		Texto.desenhar(o, "+" + escopeta.reserva(), x + Regras.CAPACIDADE_CANO * (largura + 6 * escala) + 8 * escala,
				y + altura / 2 - 12 * escala, Math.round(22 * escala), new Color(215, 205, 190), Texto.Alinhamento.ESQUERDA);
		if (escopeta.estado() == Escopeta.Estado.RECARREGANDO) {
			Texto.desenhar(o, "recarregando...", w - 40 * escala, y - 26 * escala, Math.round(16 * escala),
					new Color(170, 160, 150), Texto.Alinhamento.DIREITA);
		}

		desenharHudProvas(o, escala);
		Uso uso = usoPerto();
		if (uso != null && partida.provaAlcancavel(jogador.x, jogador.z) == null && caixaAlcancavel() == null) {
			String dica = switch (uso) {
			case CRACHA -> "E: pegar o crachá";
			case PORTA_PROFESSOR -> "E: abrir a porta";
			case PROFESSOR -> partida.todasColetadas() ? "E: entregar as provas" : "E: falar com o professor";
			case PORTA_ENTRADA, PORTA_TRANCADA -> "E: abrir a porta";
			};
			Texto.desenhar(o, dica, cx, h * 0.62f, Math.round(20 * escala), new Color(230, 220, 200), Texto.Alinhamento.CENTRO);
		}
		// o objetivo, discreto, no alto da tela
		Texto.desenhar(o, objetivos.texto(partida.provasColetadas(), partida.provas().size()), cx, 18 * escala,
				Math.round(16 * escala), new Color(190, 180, 165, 200), Texto.Alinhamento.CENTRO);
		if (!falas.isEmpty()) {
			float alfa = Math.min(1, Math.min(tempoFala, duracaoFala - tempoFala) / 0.3f);
			Texto.desenhar(o, falas.peek(), cx, h * 0.76f, Math.round(22 * escala),
					new Color(235, 225, 205, Math.round(Math.max(0, alfa) * 255)), Texto.Alinhamento.CENTRO);
		}
		if (partida.provaAlcancavel(jogador.x, jogador.z) != null) {
			Texto.desenhar(o, "E: pegar a prova", cx, h * 0.62f, Math.round(20 * escala), new Color(230, 220, 200),
					Texto.Alinhamento.CENTRO);
		} else if (caixaAlcancavel() != null) {
			Texto.desenhar(o, "E: pegar cartuchos", cx, h * 0.62f, Math.round(20 * escala), new Color(230, 220, 200),
					Texto.Alinhamento.CENTRO);
		}
		String dica = tutorial.texto();
		if (dica != null) { // as dicas do começo, esmaecidas, embaixo
			Texto.desenhar(o, dica, cx, h * 0.88f, Math.round(18 * escala),
					new Color(215, 205, 185, Math.round(175 * tutorial.alfa())), Texto.Alinhamento.CENTRO);
		}
		if (mensagem != null && tempoMensagem > 0) {
			int alfa = Math.round(255 * Math.min(1, tempoMensagem / 0.5f));
			Texto.desenhar(o, mensagem, cx, h * 0.68f, Math.round(24 * escala), new Color(235, 215, 190, alfa),
					Texto.Alinhamento.CENTRO);
			if (mensagem2 != null) {
				Texto.desenhar(o, mensagem2, cx, h * 0.68f + 34 * escala, Math.round(20 * escala),
						new Color(200, 190, 175, alfa), Texto.Alinhamento.CENTRO);
			}
		}
	}

	/** "M1 M2 M3" no canto superior esquerdo: apagadas até serem pegas; ao pegar, acendem com um pulso. */
	private void desenharHudProvas(Overlay2D o, float escala) {
		float x = 22 * escala, y = 18 * escala;
		for (Prova p : partida.provas()) {
			Color cor = p.coletada() ? new Color(235, 222, 190) : new Color(110, 104, 98);
			TextoTextura.Imagem img = Recursos.textos().obter(p.nome, Recursos.fonteTitulo(), 30, cor, true, true);
			if (img == null) continue;
			Float t = pulsoProva.get(p);
			float pulso = t != null && t < 0.6f ? 1 + 0.6f * (1 - t / 0.6f) : 1;
			float lw = img.largura() * escala * pulso, lh = img.altura() * escala * pulso;
			float base = img.largura() * escala;
			o.imagem(img.textura(), x + base / 2 - lw / 2, y + img.altura() * escala / 2 - lh / 2, lw, lh,
					new Color(1f, 1f, 1f, p.coletada() ? 1f : 0.55f));
			x += base + 14 * escala;
		}
	}

	/**
	 * Copia o que está na tela agora para uma textura nova (glCopyTexImage2D lê a imagem que
	 * acabou de ser desenhada). Vira o fundo da tela de derrota, que apaga a textura ao sair.
	 */
	private Recursos.Textura fotografar() {
		int w = gerenciador.largura(), h = gerenciador.altura();
		int id = glGenTextures();
		glBindTexture(GL_TEXTURE_2D, id);
		glCopyTexImage2D(GL_TEXTURE_2D, 0, GL_RGB8, 0, 0, w, h, 0);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
		glBindTexture(GL_TEXTURE_2D, 0);
		return new Recursos.Textura(id, w, h);
	}

	/**
	 * A matriz de visão da câmera, sacudida por um giro pequeno e aleatório quando há tremor
	 * (o monstro surgindo, o ataque, o susto). Girar depois da visão = girar a cabeça no lugar.
	 */
	private Matrix4f visaoComTremor() {
		Matrix4f visao = cameraAtual().matrizVisao();
		if (tremor > 0) {
			float forca = tremor * tremor * 0.03f; // radianos
			Matrix4f giro = new Matrix4f();
			giro.rotate((random.nextFloat() * 2 - 1) * forca, new Vector3f(1, 0, 0));
			giro.rotate((random.nextFloat() * 2 - 1) * forca, new Vector3f(0, 1, 0));
			Matrix4f.mul(giro, visao, visao);
		}
		return visao;
	}

	/**
	 * O que o monstro faz na tela: escurece no surgimento; na contagem, a vinheta vermelha e o
	 * número grande pulsando; no susto, a imagem (se houver) e um clarão vermelho.
	 */
	private void desenharEfeitosMonstro(Overlay2D o) {
		int w = gerenciador.larguraJanela(), h = gerenciador.alturaJanela();
		if (tempoSusto >= 0) {
			if (texturaSusto != 0) {
				o.imagem(texturaSusto, 0, 0, w, h, Color.WHITE);
			}
			float pulso = 0.12f + 0.18f * (float) Math.abs(Math.sin(tempoSusto * 28));
			o.retangulo(0, 0, w, h, new Color(0.55f, 0f, 0f, pulso));
			return;
		}
		DragonKnight.Estado estado = monstro.estado();
		if (estado == DragonKnight.Estado.SURGINDO) {
			float t = Math.min(1, monstro.tempoNoEstado() / Regras.DURACAO_SURGIMENTO);
			o.retangulo(0, 0, w, h, new Color(0f, 0f, 0f, 0.65f * (float) Math.sin(Math.PI * t)));
		}
		if (estado != DragonKnight.Estado.ENCARANDO) {
			return;
		}
		float progresso = progressoContagem();
		o.imagem(texturaVinheta(), 0, 0, w, h, new Color(1f, 1f, 1f, Math.min(1f, 0.35f + 0.65f * progresso)));

		// "5", "4"... "1": cada número começa grande e encolhe até o próximo segundo (o pulso)
		float restante = monstro.contagem();
		int numero = Math.max(1, (int) Math.ceil(restante));
		float fracao = restante - (numero - 1); // 1 quando o número aparece, 0 quando vai trocar
		TextoTextura.Imagem img = Recursos.textos().obter(String.valueOf(numero), Recursos.fonteTitulo(), 140,
				COR_CONTAGEM, true, true);
		if (img != null) {
			float escala = (h / 800f) * (1 + 0.35f * fracao * fracao);
			float lw = img.largura() * escala, lh = img.altura() * escala;
			o.imagem(img.textura(), w / 2f - lw / 2, h * 0.2f - lh / 2, lw, lh,
					new Color(1f, 1f, 1f, 0.55f + 0.45f * fracao));
		}
	}

	/** Bordas vermelhas, transparentes no meio (gerada uma vez, por código). */
	private static int texturaVinheta() {
		return Recursos.texturaGerada("vinheta vermelha", () -> {
			int n = 256;
			java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(n, n,
					java.awt.image.BufferedImage.TYPE_INT_ARGB);
			for (int y = 0; y < n; y++) {
				for (int x = 0; x < n; x++) {
					float dx = (x + 0.5f) / n * 2 - 1, dy = (y + 0.5f) / n * 2 - 1;
					float r = (float) Math.sqrt(dx * dx + dy * dy) / 1.414f;
					float a = Math.max(0, Math.min(1, (r - 0.3f) / 0.7f));
					a = a * a * (3 - 2 * a);
					img.setRGB(x, y, ((int) (a * 235) << 24) | (150 << 16) | (5 << 8) | 3);
				}
			}
			return img;
		}, false, false).id();
	}

	/**
	 * Projeção em perspectiva que acompanha a proporção da janela: a altura do
	 * frustum vem do campo de visão, e a largura é a altura vezes a proporção.
	 */
	private Matrix4f projecao() {
		float topo = PERTO * (float) Math.tan(Math.toRadians(campoVisao / 2));
		float direita = topo * gerenciador.proporcao();
		return Utils3D.setFrustum(-direita, direita, -topo, topo, PERTO, LONGE);
	}

	/** Mesmo padrão do projeto original: storeTranspose e flip. */
	private static void guardar(Matrix4f m, FloatBuffer buffer) {
		buffer.clear();
		m.storeTranspose(buffer);
		buffer.flip();
	}

	private void desenharTerreno() {
		ShaderTerrenoFloresta shader = Recursos.shaderFloresta();
		shader.start();
		int prog = shader.programID;
		glUniformMatrix4fv(glGetUniformLocation(prog, "projection"), false, bufferProjecao);
		glUniformMatrix4fv(glGetUniformLocation(prog, "view"), false, bufferVisao);
		glUniform1f(glGetUniformLocation(prog, "tempo"), tempo);
		ambiente.enviar(shader);

		// cada textura vai numa "unidade" diferente: 0, 1, 2...
		for (int i = 0; i < texturasTerreno.length; i++) {
			glActiveTexture(GL_TEXTURE0 + i);
			glBindTexture(GL_TEXTURE_2D, texturasTerreno[i]);
			glUniform1i(glGetUniformLocation(prog, TexturasFloresta.UNIFORMS[i]), i);
		}
		// o mapa de detalhes (trilhas e regiões) na unidade seguinte; a coordenada do terreno no
		// shader é a da grade (1 ponto por metro), então o mapa vai de 0 ao tamanho em metros
		int unidade = texturasTerreno.length;
		glActiveTexture(GL_TEXTURE0 + unidade);
		glBindTexture(GL_TEXTURE_2D, mundo.texturaDetalhes);
		glUniform1i(glGetUniformLocation(prog, "texDetalhes"), unidade);
		glUniform1f(glGetUniformLocation(prog, "tamanhoMapa"), mundo.tamanho());

		mundo.terreno.DesenhaSe(shader);

		glActiveTexture(GL_TEXTURE0);
		shader.stop();
	}

	/** Todas as árvores numa chamada, com a luz e a neblina do comum.glsl. */
	private void desenharArvores() {
		ShaderArvore shader = Recursos.shaderArvore();
		shader.start();
		int prog = shader.programID;
		glUniformMatrix4fv(glGetUniformLocation(prog, "projection"), false, bufferProjecao);
		glUniformMatrix4fv(glGetUniformLocation(prog, "view"), false, bufferVisao);
		glUniform1f(glGetUniformLocation(prog, "tempo"), tempo);
		glUniform1f(glGetUniformLocation(prog, "numeroTexturas"), DistribuidorArvores.VARIACOES);
		glUniform1i(glGetUniformLocation(prog, "tex"), 0);
		ambiente.enviar(shader);

		glActiveTexture(GL_TEXTURE0);
		glBindTexture(GL_TEXTURE_2D, Recursos.texturaArvores());
		mundo.arvores.draw();
		shader.stop();
	}

	/**
	 * As árvores 3D: primeiro escolhe as que a câmera pode ver (perto e não atrás dela),
	 * depois desenha todas de uma vez por variação e material.
	 */
	private void desenharArvores3D() {
		Camera c = cameraAtual();
		mundo.arvores3d.escolher(c.x, c.z, c.frente.x, c.frente.z);

		ShaderArvore3D shader = Recursos.shaderArvore3D();
		shader.start();
		int prog = shader.programID;
		glUniformMatrix4fv(glGetUniformLocation(prog, "projection"), false, bufferProjecao);
		glUniformMatrix4fv(glGetUniformLocation(prog, "view"), false, bufferVisao);
		glUniform1f(glGetUniformLocation(prog, "tempo"), tempo);
		glUniform1i(glGetUniformLocation(prog, "tex"), 0);
		ambiente.enviar(shader);

		glActiveTexture(GL_TEXTURE0);
		mundo.arvores3d.draw(); // cada material liga a sua textura
		shader.stop();
	}

	/** A linha das provas no painel F3: a distância até cada uma e até a Univali. */
	private String textoProvas() {
		StringBuilder sb = new StringBuilder("provas");
		Camera c = jogador.camera();
		for (Prova p : partida.provas()) {
			sb.append("   ").append(p.nome).append(p.coletada() ? " pega"
					: String.format(" %.0f m", Math.hypot(p.x - c.x, p.z - c.z)));
		}
		float[] u = mundo.predio.porta();
		return sb.append(String.format("   Univali %.0f m   fitness da fase %.3f", Math.hypot(u[0] - c.x, u[2] - c.z),
				mundo.fase.fitness())).toString();
	}

	/** A linha do monstro no painel F3. */
	private String textoMonstro() {
		String base = "monstro " + monstro.estado().name().toLowerCase();
		String detalhe = switch (monstro.estado()) {
		case AUSENTE -> String.format(", surge em %.0f s", monstro.cronometro());
		case ENCARANDO -> String.format(", contagem %.1f s, vida %.1f%s", monstro.contagem(), monstro.life,
				monstro.ferido() ? ", ferido" : "");
		default -> "";
		};
		String animacao = monstro.animador() != null ? "   " + monstro.animador().nomeAtual() : "   (substituto)";
		return base + detalhe + animacao + String.format("   sobrevividas %d   susto falso em %.0f s%s",
				monstro.aparicoesSobrevividas(), sustos.cronometro(), silhueta != null ? " (silhueta em cena)" : "");
	}

	/**
	 * As luzes pontuais (o shader aceita 4): das candidatas (os dois postes, a luminária do professor e as
	 * lâmpadas do teto que funcionam), as 4 mais perto da câmera. A posição de cada uma vai para o
	 * espaço da câmera (a matriz de visão vezes o ponto), porque é nesse espaço que o comum.glsl calcula a luz.
	 */
	private void luzesDosPostes(Matrix4f visao) {
		// as candidatas: os postes, a luminária do professor e as lâmpadas do teto que funcionam
		List<float[]> luzes = new java.util.ArrayList<>(); // {x, y, z, r, g, b, alcance}
		for (float[] poste : mundo.predio.postes()) {
			luzes.add(new float[] { poste[0], mundo.predio.y + 5.0f, poste[1], 1.3f, 1.0f, 0.65f, 16f });
		}
		float[] l = desenhoPredio.luminariaNoMundo();
		luzes.add(new float[] { l[0], l[1], l[2], 1.1f, 0.95f, 0.6f, 6f });
		boolean falhaAcesa = desenhoPredio.lampadaPiscandoAcesa();
		for (fase.AndarUnivali.Lampada lamp : mundo.predio.andar.lampadas()) {
			if (lamp.estado() == fase.AndarUnivali.Lampada.Estado.QUEIMADA) continue;
			if (lamp.estado() == fase.AndarUnivali.Lampada.Estado.PISCANDO && !falhaAcesa) continue;
			float[] w = mundo.predio.noMundo(lamp.x(), entidades.PredioUnivali.PE_DIREITO - 0.2f, lamp.z());
			luzes.add(new float[] { w[0], w[1], w[2], 0.75f, 0.82f, 0.9f, 7f });
		}
		// as 4 mais perto da câmera (o shader aceita 4)
		Camera c = cameraAtual();
		luzes.sort((a, b) -> Float.compare(dist2(a, c), dist2(b, c)));
		ambiente.numLuzes = Math.min(Ambiente.MAX_LUZES, luzes.size());
		for (int i = 0; i < ambiente.numLuzes; i++) {
			float[] luz = luzes.get(i);
			org.lwjgl.util.vector.Vector4f p = Matrix4f.transform(visao, new org.lwjgl.util.vector.Vector4f(luz[0], luz[1], luz[2], 1), null);
			ambiente.posLuz[i * 3] = p.x;
			ambiente.posLuz[i * 3 + 1] = p.y;
			ambiente.posLuz[i * 3 + 2] = p.z;
			ambiente.corLuz[i * 3] = luz[3];
			ambiente.corLuz[i * 3 + 1] = luz[4];
			ambiente.corLuz[i * 3 + 2] = luz[5];
			ambiente.alcanceLuz[i] = luz[6];
		}
	}

	private static float dist2(float[] luz, Camera c) {
		float dx = luz[0] - c.x, dy = luz[1] - c.y, dz = luz[2] - c.z;
		return dx * dx + dy * dy + dz * dz;
	}

	/** A cena final: a tela escurece em 1,5 s e as falas aparecem uma de cada vez. */
	private void desenharEntrega(Overlay2D o) {
		int w = gerenciador.larguraJanela(), h = gerenciador.alturaJanela();
		float escuro = Math.min(1, tempoEntrega / 1.5f);
		o.retangulo(0, 0, w, h, new Color(0, 0, 0, Math.round(escuro * 255)));
		for (int i = 0; i < FALAS.length; i++) {
			float ini = TEMPO_FALAS[i][0], fimFala = TEMPO_FALAS[i][1];
			if (tempoEntrega < ini || tempoEntrega > fimFala) continue;
			float alfa = Math.min(1, Math.min(tempoEntrega - ini, fimFala - tempoEntrega) / 0.3f);
			Texto.desenhar(o, FALAS[i], w / 2f, h * 0.47f, Math.max(20, Math.round(h * 0.035f)),
					new Color(225, 215, 195, Math.round(alfa * 255)), Texto.Alinhamento.CENTRO);
		}
	}

	/** As pedras: o shader das árvores 3D, sem o vento. */
	private void desenharPedras() {
		if (mundo.pedras == null) return;
		ShaderArvore3D shader = Recursos.shaderArvore3D();
		shader.start();
		int prog = shader.programID;
		glUniformMatrix4fv(glGetUniformLocation(prog, "projection"), false, bufferProjecao);
		glUniformMatrix4fv(glGetUniformLocation(prog, "view"), false, bufferVisao);
		glUniform1f(glGetUniformLocation(prog, "tempo"), tempo);
		glUniform1f(glGetUniformLocation(prog, "semVento"), 1f);
		glUniform1f(glGetUniformLocation(prog, "duplaFace"), 0f);
		glUniform1i(glGetUniformLocation(prog, "tex"), 0);
		ambiente.enviar(shader);
		glActiveTexture(GL_TEXTURE0);
		mundo.pedras.draw();
		glUniform1f(glGetUniformLocation(prog, "semVento"), 0f);
		shader.stop();
	}

	/** Painel F3: números para conferir o que o jogo está calculando. */
	private void desenharDepuracao(Overlay2D overlay) {
		ConsultaAltura chao = mundo.chao;
		Camera c = cameraAtual();
		String[] linhas = {
				String.format("FPS %.0f    seed %d    árvores %d%s", fps, mundo.seed, mundo.quantidadeArvores(),
						mundo.arvores3d != null ? String.format(" (3D, %d desenhadas)", mundo.arvores3d.desenhadas())
								: " (planas)")
						+ String.format("   pedras %d   placas %d", mundo.pedras != null ? mundo.pedras.quantidade() : 0,
								mundo.placas.size()),
				String.format("posição  x %.1f  z %.1f   olhos %.2f m   sombra %s", c.x, c.z, c.y,
						ambiente.usaSombra > 0 ? String.format("%d², %.2f ms na placa", sombra.lado(), sombra.milissegundos())
								: ladoSombra == 0 ? "desligada" : "lanterna apagada"),
				String.format("chão %.2f m   inclinação %.0f°   %s", chao.alturaEm(c.x, c.z), chao.anguloEm(c.x, c.z),
						chao.ehAgua(c.x, c.z) ? String.format("água, %.2f m de fundo", chao.profundidadeAgua(c.x, c.z))
								: "terra firme"),
				String.format("velocidade %.1f m/s   %s", jogador.velocidade(),
						jogador.correndo() ? "correndo" : jogador.velocidade() > 0.1f ? "andando" : "parado"),
				String.format("estamina %.0f%%%s   lanterna %s", jogador.estamina() * 100,
						jogador.ofegante() ? "   ofegante" : "", lanterna.ligada() ? "ligada" : "desligada"),
				(cameraLivre != null ? "câmera livre (F4 volta ao jogador)" : "F4: câmera livre")
						+ (ps1 ? String.format("   modo PS1 %dx%d", filtroPs1.largura(), filtroPs1.altura()) : ""),
				textoMonstro(),
				textoProvas(),
				String.format("arma %s   cano %d   reserva %d   partículas %d   caixas %d", escopeta.estado().name().toLowerCase(),
						escopeta.cano(), escopeta.reserva(), particulas.quantidade(),
						caixas.stream().filter(cx -> !cx.pega()).count()),
				"F lanterna   ESC pausa   T som de teste   V vitória   X derrota   G monstro surge   K mata   N fantasma   H silhueta   J galho   L risada",
				"clique atira   R recarrega   E pega   P vai até a próxima prova   I salva o mapa da fase   setas e PgUp/PgDn: arma",
		};
		int tamanho = 16;
		float linha = tamanho * 1.45f;
		overlay.retangulo(8, 8, 640, linhas.length * linha + 16, COR_PAINEL);
		for (int i = 0; i < linhas.length; i++) {
			Texto.desenhar(overlay, linhas[i], 18, 16 + i * linha, tamanho, COR_DEPURACAO, Texto.Alinhamento.ESQUERDA);
		}
	}
}
