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
import entidades.Jubilado;
import entidades.Escopeta;
import entidades.Prova;
import entidades.Tiro;
import entidades.Jogador;
import entrada.Entrada;
import jogo.MaterialChao;
import jogo.Objetivos;
import jogo.Partida;
import jogo.Regras;
import jogo.SustosFalsos;
import jogo.Tutorial;
import md2.Animador;
import mundo.ConsultaAltura;
import mundo.GradeColisao;
import mundo.Mundo;
import mundo.DistribuidorArvores;
import mundo.TexturasFloresta;
import render.Ambiente;
import render.Camera;
import render.CameraLivre;
import render.DesenhoEscopeta;
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
 * F liga e desliga a lanterna. ESC abre a pausa por cima. F3 mostra o painel de depuração. Com o F3 ligado,
 * F4 troca para a câmera livre (voa e atravessa o chão, para inspecionar o mapa), T liga um som 3D de teste,
 * V e X forçam vitória e derrota, G faz o Dragon Knight surgir e K o mata, P leva até a próxima prova, I salva a imagem do mapa da fase; as setas e Page Up/Down ajustam a posição da arma.
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
	static final String PASTA_JUBILADO = "modelos/jubilado";
	static final float ALTURA_JUBILADO = 2.0f;

	private static final float PERTO = 0.1f;
	// a neblina esconde tudo antes de ~50 m; só os feixes das provas (neblina mais fraca) vão além
	private static final float LONGE = 120f;
	/** Folga para a copa das árvores na escolha do mapa de sombra, em metros. */
	private static final float RAIO_COPA = 6f;

	private static final Color COR_PAINEL = new Color(0, 0, 0, 160);
	private static final Color COR_DEPURACAO = new Color(200, 195, 185);
	private static final Color COR_DICA = new Color(120, 115, 110, 200);

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
	/** Os primeiros segundos: acordar com a cabeça na carteira (ver jogo.Despertar). */
	private final jogo.Despertar despertar = new jogo.Despertar();
	private float andado = 0, ultimoX, ultimoZ;
	private SustosFalsos sustos;
	private DesenhoSilhueta desenhoSilhueta;
	/** A silhueta em cena: {x, y, z, guinada, segundos na tela}; null = nenhuma. */
	private float[] silhueta = null;
	private Efeito somGalho;
	/** A fala do professor cuja voz já tocou (para tocar uma vez por fala) e a última fala da entrega com voz. */
	private String falaComVoz = null;
	/** Quanto tempo a fala atual fica na tela: pelo menos 3,2 s, ou até a voz acabar. */
	private static final float DURACAO_MINIMA_FALA = 3.2f;
	private float duracaoFala = DURACAO_MINIMA_FALA;
	/** audio/risada_professor.ogg: ele ri quando o jogador sai da sala depois da negativa (null = sem o arquivo). */
	private Efeito somRisadaProfessor;
	private boolean risadaPendente = false, naSalaDoProfessor = false;
	/** As lâmpadas da Univali falhando (cada uma no seu tempo, e às vezes todas juntas) e o som disso. */
	private jogo.FalhasDeLuz falhasDeLuz;
	private Efeito somLampada;
	/** As portas de sala que abrem sozinhas (a primeira é a do início), a colisão de cada uma enquanto fechada e o rangido. */
	private jogo.PortasSozinhas portasSozinhas;
	private final List<mundo.GradeColisao.Retangulo> colisaoPortas = new java.util.ArrayList<>();
	private Efeito somPorta;
	/** Com as 3 provas, a Univali deixa de ser segura: ele vem logo na primeira entrada. */
	private boolean voltouComAsProvas = false;
	/** O jogador já pôs os pés fora da Univali: os Jubilados e os sustos da floresta começam. */
	private boolean saiuDaUnivali = false;
	private int ultimaFalaEntrega = -1;
	/** audio/risada.ogg (null = sem o arquivo: a risada vira galho). */
	private Efeito somRisada;

	// o Dragon Knight
	private final DragonKnight monstro;
	private DesenhoMonstro desenhoMonstro;
	private Efeito somGrito, somMorte, somBatida, somSusto, somGalope;
	/** Segundos que ele ainda sangra depois de um golpe. */
	private float sangrando = 0;
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
	/** Quem quer a música agora: o Dragon Knight em cena e/ou o Jubilado caçando. Ela só sai quando os dois param. */
	private boolean metalDragon, metalJubilado;

	// o Jubilado: o monstro que anda pela floresta (lógica em entidades.Jubilado)
	private final List<Jubilado> jubilados = new java.util.ArrayList<>();
	/** O ritmo da partida: calmaria, a vez de um monstro, calmaria... (um monstro de cada vez). */
	private final jogo.DiretorDeSustos diretor;
	/** O Jubilado que pegou o jogador (o do susto final). */
	private Jubilado jubiladoDoSusto;
	private DesenhoMonstro desenhoJubilado;
	private Efeito somPisadaJubilado, somGrunhido, somGritoJubilado, somQuedaJubilado;
	/** O que ele fala quando vê o jogador ou levanta (a voz gravada, com o efeito de voz de doido). */
	private final jogo.FalasJubilado falasJubilado = new jogo.FalasJubilado();
	/** O susto de quando ele vê o jogador (audio/jubilado_glitch.ogg) e os sussurros de quando vem farejando. */
	private Efeito somGlitchJubilado;
	private Efeito[] sussurrosJubilado;
	/** A fala que vem logo depois do glitch: de quem, qual e daqui a quantos segundos. */
	private Jubilado quemVaiFalar;
	private jogo.FalasJubilado.Fala falaPendente;
	private float atrasoFala;
	/** Depois do glitch, quanto a fala espera (segundos). */
	private static final float ATRASO_FALA = 0.9f;

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
	/** A voz de rádio da TV do saguão, em loop (audio/tv_univali.ogg). */
	private SomTocando somTv;
	/** Segundos desde que as provas apareceram no canto (depois da missão do professor); -1 = ainda não. */
	private float tempoMissao = -1;
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
	private static final float FIM_ENTREGA = 8.6f;
	private static final String[] FALAS = { "Professor: \"Atrasado de novo?\"", "...", "Professor: \"Tá bom. Vou corrigir.\"" };
	private static final float[][] TEMPO_FALAS = { { 1.6f, 4.0f }, { 4.1f, 5.1f }, { 5.3f, 8.0f } };

	/** sprites/susto.png, se existir (0 = sem a imagem: o susto é o próprio modelo bem de perto). */
	private int texturaSusto = 0;

	/** Tempo da partida em segundos; para quando a partida está pausada (anima a água). */
	private float tempo = 0;
	/** Os textos do painel F3 e quando foram montados (refeitos 4 vezes por segundo). */
	private String[] linhasDepuracao;
	private float tempoDepuracao;
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
		this.monstro = new DragonKnight(animador, caixa, System.nanoTime()); // dorme até a volta com as provas
		// os Jubilados: espalhados pelo mapa, longe do jogador e uns dos outros; cada um com a sua animação
		float[] caixaJubilado = mundo.jubilado != null ? mundo.jubilado.malhaCorpo().limites(0) : null;
		List<float[]> ocupados = new java.util.ArrayList<>();
		for (int i = 0; i < Regras.JUBILADOS; i++) {
			Animador animadorJubilado = mundo.jubilado != null ? new Animador(mundo.jubilado.animacoes()) : null;
			Jubilado j = new Jubilado(mundo.chao, mundo.colisao, mundo.caminhada, animadorJubilado, caixaJubilado,
					System.nanoTime() + 1 + i);
			j.univali = mundo.predio.retangulo();
			j.posicionarLongeDe(mundo.inicio[0], mundo.inicio[1], ocupados);
			ocupados.add(new float[] { j.x, j.z });
			j.ativo = false; // como o Dragon Knight, só começam quando o jogador sair da Univali
			jubilados.add(j);
		}
		this.diretor = new jogo.DiretorDeSustos(jubilados, new java.util.Random(System.nanoTime()));
		java.util.List<Prova> provas = new java.util.ArrayList<>();
		for (int i = 0; i < mundo.provas.size(); i++) {
			float[] lugar = mundo.provas.get(i);
			provas.add(new Prova("M" + (i + 1), lugar[0], lugar[1], mundo.chao));
		}
		this.partida = new Partida(mundo.seed, provas);
		avisarProvasAoJubilado();
	}

	@Override
	public void entrar() {
		gerenciador.definirTitulo(TITULO);
		if (lanterna.ligada() && !objetivos.temEscopeta()) lanterna.alternar(); // a lanterna vem presa na escopeta
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
		somLampada = efeitoOuSintetizado("audio/lampada_falhando.ogg", () -> Sintetizador.zumbido(44100));
		List<fase.AndarUnivali.Lampada> lampadas = mundo.predio.andar.lampadas();
		boolean[] funciona = new boolean[lampadas.size()], pisca = new boolean[lampadas.size()];
		for (int i = 0; i < lampadas.size(); i++) {
			funciona[i] = lampadas.get(i).estado() != fase.AndarUnivali.Lampada.Estado.QUEIMADA;
			pisca[i] = lampadas.get(i).estado() == fase.AndarUnivali.Lampada.Estado.PISCANDO;
		}
		falhasDeLuz = new jogo.FalhasDeLuz(funciona, pisca, mundo.seed + 11);
		somPorta = efeitoOuSintetizado("audio/porta_abrindo.ogg", () -> Sintetizador.estalos(44100, new int[] { 0, 400, 900 },
				new double[] { 0.6, 0.4, 0.8 }));
		List<float[]> centros = new java.util.ArrayList<>();
		for (fase.AndarUnivali.Sala s : mundo.predio.andar.portasQueAbremSozinhas()) {
			centros.add(s.porta());
			mundo.GradeColisao.Retangulo r = mundo.predio.noMundo(mundo.predio.andar.porta(s));
			colisaoPortas.add(r);
			mundo.colisao.adicionarRetangulo(r); // fechada: ninguém passa
		}
		portasSozinhas = new jogo.PortasSozinhas(centros);
		sustos = new SustosFalsos(mundo.seed + 5);
		desenhoSilhueta = new DesenhoSilhueta();
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
		prepararJubilado();
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
		// as duas portas começam trancadas: um retângulo no vão de cada uma
		mundo.colisao.adicionarRetangulo(mundo.predio.portaEntrada());
		mundo.colisao.adicionarRetangulo(mundo.predio.portaProfessor());
		// o despertar: o zumbido no ouvido, a respiração (a mensagem do objetivo vem quando ele acordar)
		gerenciador.audio().tocar(gerenciador.audio().criarEfeito("despertar", Sintetizador.despertar(44100), 44100), 0.8f);
		somSussurro = efeitoOuSintetizado("audio/sussurro.ogg", () -> Sintetizador.sussurro(44100, 13));
		somPegarProva = efeitoOuSintetizado("audio/pegar_prova.ogg", () -> Sintetizador.ping(44100, 660, 600, 900));
		somSinal = efeitoOuSintetizado("audio/sinal_aula.ogg", () -> Sintetizador.sinal(44100));
		// o zumbido das lâmpadas no saguão, em loop
		float[] saguao = mundo.predio.noMundo(0, 3, 8);
		somZumbido = gerenciador.audio().tocar3D(gerenciador.audio().criarEfeito("zumbido das lampadas",
				Sintetizador.zumbido(44100), 44100), saguao[0], saguao[1], saguao[2], 0.22f, true, 2.5f);
		temMusicaMonstro = Caminhos.existe(MUSICA_MONSTRO);
		if (Caminhos.existe("audio/tv_univali.ogg")) { // a voz de rádio da TV, saindo dela (3D)
			float[] tv = mundo.predio.noMundo(DesenhoPredio.TV[0], DesenhoPredio.TV[1], DesenhoPredio.TV[2]);
			somTv = gerenciador.audio().tocar3D(gerenciador.audio().carregarEfeito("audio/tv_univali.ogg"), tv[0], tv[1], tv[2],
					0.85f, true, 2.5f);
		}

		caixas = new java.util.ArrayList<>();
		for (float[] c : mundo.caixas) { // onde o AG da fase pôs as caixas
			caixas.add(new CaixaMunicao(c[0], mundo.chao.alturaEm(c[0], c[1]), c[1], random.nextFloat() * (float) Math.PI));
		}
		// uma caixinha ao lado de cada prova (o aluno da carta deixou), a uns 2 m, num lugar seco e sem tronco
		for (float[] prova : mundo.provas) {
			float[] lugar = ladoDaProva(prova);
			caixas.add(new CaixaMunicao(lugar[0], mundo.chao.alturaEm(lugar[0], lugar[1]), lugar[1],
					random.nextFloat() * (float) Math.PI, Regras.CARTUCHOS_PERTO_DA_PROVA));
		}
		cuboCaixa = Recursos.cubo(); // um só para o jogo inteiro: o VboCube do projeto original não tem como ser apagado

		somGalope = gerenciador.audio().criarEfeito("galope", Sintetizador.pisadaPesada(44100, 77), 44100);
		monstro.definirEventos(new DragonKnight.Eventos() {
			@Override
			public void surgiu(DragonKnight m) {
				tremor = 1;
				falhasDeLuz.forcarFalhaGeral(); // dentro da Univali, as luzes falham
				pedirMetal(true, true);
				// o grito sai de onde ele está: com fone, dá para saber de que lado ele surgiu
				gerenciador.audio().tocar3D(somGrito, m.x, m.y + 1.6f, m.z, 1f, false, 8f);
			}

			@Override
			public void golpeado(DragonKnight m, int golpes) {
				// o tiro entrou: um jorro de sangue do peito, saindo pelas costas, e ele continua sangrando
				particulas.jorroDeSangue(m.x, m.y + 1.6f, m.z, m.x - jogador.x, m.z - jogador.z, 90);
				sangrando = 0.9f;
				tremor = Math.max(tremor, 0.4f);
				if (golpes < Regras.GOLPES_PARA_MATAR) {
					gerenciador.audio().tocar3D(somMorte, m.x, m.y + 1.4f, m.z, 0.8f, false, 8f);
				}
			}

			@Override
			public void galope(DragonKnight m) {
				gerenciador.audio().tocar3D(somGalope, m.x, m.y + 0.1f, m.z, 0.9f, false, 5f);
			}

			@Override
			public void morreu(DragonKnight m) {
				gerenciador.audio().tocar3D(somMorte, m.x, m.y + 1.2f, m.z, 1f, false, 10f);
				pedirMetal(true, false);
			}

			@Override
			public void atacou(DragonKnight m) {
				tremor = 0.7f;
				gerenciador.audio().tocar(somGrito, 1f);
			}
		});
	}

	/**
	 * A volta com as provas: o Dragon Knight acorda dentro da Univali. A planta (por onde ele anda)
	 * é montada agora, com as portas como estão: ele cabe no corredor e no saguão, mas não nas
	 * salas. Surge no fundo do saguão, de frente para quem entra, e depois pelas pontas do corredor.
	 */
	private void acordarDragonKnight() {
		if (monstro.lutando()) return;
		var p = mundo.predio;
		List<float[]> pontas = new java.util.ArrayList<>();
		for (float[] m : new float[][] { { 0, -0.5f }, { -21, 0 }, { 21, 0 } }) {
			float[] w = p.noMundo(m[0], 0, m[1]);
			pontas.add(new float[] { w[0], w[2] });
		}
		float piso = p.noMundo(0, 0, 0)[1];
		grafos.PlantaInterna planta = new grafos.PlantaInterna(mundo.colisao, p.retangulo(), 0.75f, 0.5f, pontas, piso + 1.6f);
		monstro.despertar(planta, piso);
	}

	/** Um lugar a ~2 m da prova, em terra firme e sem tronco (tenta 12 direções; sem nenhuma boa, a 2 m em x). */
	private float[] ladoDaProva(float[] prova) {
		for (int i = 0; i < 12; i++) {
			double angulo = i * Math.PI / 6;
			float x = prova[0] + 2.2f * (float) Math.cos(angulo), z = prova[1] + 2.2f * (float) Math.sin(angulo);
			if (!mundo.chao.ehAgua(x, z) && !mundo.colisao.colide(x, z, 0.5f)) return new float[] { x, z };
		}
		return new float[] { prova[0] + 2.2f, prova[1] };
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
		desenhoJubilado.liberar();
		desenhoEscopeta.liberar();
		desenhoProvas.liberar();
		desenhoPlacas.liberar();
		desenhoPredio.liberar();
		desenhoSilhueta.liberar();
		for (SomTocando s : sussurros.values()) {
			audio.parar(s);
		}
		sussurros.clear();
		audio.parar(somZumbido);
		audio.parar(somTv);
		particulas.liberar();
	}

	// ---------- atualização ----------

	@Override
	public void atualizar(long diftime) {
		Entrada entrada = gerenciador.entrada();
		if (fim != null) {
			// o desenhar do quadro anterior já fotografou a tela: agora troca
			if (fotoFim != null || fim == Fim.VITORIA) {
				Partida.Estatisticas estatisticas = partida.estatisticas(diretor.sobrevividas());
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
			gerenciador.sobrepor(new TelaPausa(gerenciador, mundo.seed, objetivos.temEscopeta()));
			return;
		}
		if (entrada.foiPressionada(GLFW_KEY_F3)) {
			mostrarDepuracao = !mostrarDepuracao;
		}
		// atalhos de teste: só com a depuração (F3) ligada
		if (mostrarDepuracao && entrada.foiPressionada(GLFW_KEY_V)) {
			fim = Fim.VITORIA;
			return;
		}
		if (mostrarDepuracao && entrada.foiPressionada(GLFW_KEY_X)) {
			fim = Fim.DERROTA;
			return;
		}
		// câmera livre e bipe de teste: ligam só com o F3; desligam sempre, para não ficar preso neles
		if ((mostrarDepuracao || cameraLivre != null) && entrada.foiPressionada(GLFW_KEY_F4)) {
			alternarCameraLivre();
		}
		if ((mostrarDepuracao || somTeste != null) && entrada.foiPressionada(GLFW_KEY_T)) {
			alternarSomTeste();
		}
		if (entrada.foiPressionada(GLFW_KEY_F) && objetivos.temEscopeta()) { // a lanterna fica presa na escopeta
			tutorial.registrar(Tutorial.Acao.LANTERNA);
			lanterna.alternar();
			gerenciador.audio().tocar(cliqueLanterna, 0.8f);
		}
		if (mostrarDepuracao && entrada.foiPressionada(GLFW_KEY_G)) {
			acordarDragonKnight();
		}
		if (mostrarDepuracao && entrada.foiPressionada(GLFW_KEY_K)) {
			monstro.matar();
		}
		if (mostrarDepuracao && entrada.foiPressionada(GLFW_KEY_N)) {
			Camera c = jogador.camera();
			for (Jubilado j : jubilados) j.ativo = true;
			diretor.forcar(partida.provasColetadas(), jogador.x, jogador.z);
			List<Jubilado> cacadores = diretor.cacadores();
			if (!cacadores.isEmpty()) cacadores.get(0).forcarPerto(c.frente.x, c.frente.z);
			mostrarMensagem("Caçada: um Jubilado a 20 m, atrás de você, vindo ver o barulho");
		}
		if (mostrarDepuracao && entrada.foiPressionada(GLFW_KEY_H)) {
			sustos.forcar(SustosFalsos.Tipo.SILHUETA);
			mostrarMensagem("Susto falso: silhueta (só na floresta, sem o monstro)");
		}
		if (mostrarDepuracao && entrada.foiPressionada(GLFW_KEY_J)) {
			sustos.forcar(SustosFalsos.Tipo.GALHO);
			mostrarMensagem("Susto falso: galho (só na floresta, sem o monstro)");
		}
		if (mostrarDepuracao && entrada.foiPressionada(GLFW_KEY_B)) {
			falhasDeLuz.forcarFalhaGeral();
			mostrarMensagem("Falha geral das luzes (só dentro da Univali)");
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

		if (!despertar.terminou()) { // acordando: Enter ou espaço pulam
			despertar.atualizar(diftime / 1000f);
			if (entrada.foiPressionada(GLFW_KEY_ENTER) || entrada.foiPressionada(GLFW_KEY_SPACE)) despertar.pular();
			if (despertar.terminou()) mostrarMensagem("Encontre a sala do professor.", 4f);
		}
		if (cameraLivre != null) {
			cameraLivre.atualizar(entrada, diftime);
		} else {
			if (monstro.estado() == DragonKnight.Estado.ATACANDO || algumJubilado(Jubilado.Estado.ATACANDO)) {
				jogador.comandar(0, 0, false); // paralisado de medo
			} else {
				comandarJogador(entrada);
			}
			jogador.SimulaSe(diftime);
			tocarSonsDoJogador();
			if (monstro.estado() != DragonKnight.Estado.ATACANDO && !algumJubilado(Jubilado.Estado.ATACANDO)
					&& despertar.terminou()) {
				if (objetivos.temEscopeta()) comandarArma(entrada); // atirar e recarregar, só com a escopeta
				usar(entrada);
			}
		}
		atualizarMonstro(diftime);
		atualizarJubilados(diftime);
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

	/** Clique esquerdo atira e R recarrega (só depois de pegar a escopeta). */
	private void comandarArma(Entrada entrada) {
		if (entrada.botaoFoiPressionado(GLFW_MOUSE_BUTTON_LEFT) && escopeta.gatilho()) {
			atirar();
		}
		if (entrada.foiPressionada(GLFW_KEY_R)) {
			escopeta.recarregar();
		}
	}

	/** E pega a prova ou a caixa de munição que estiver perto, ou usa o que houver no prédio. */
	private void usar(Entrada entrada) {
		if (entrada.foiPressionada(GLFW_KEY_E)) {
			tutorial.registrar(Tutorial.Acao.USOU);
			Prova prova = partida.provaAlcancavel(jogador.x, jogador.z);
			CaixaMunicao perto = caixaAlcancavel();
			if (prova != null) {
				coletar(prova);
			} else if (perto != null) {
				int cartuchos = perto.pegar();
				escopeta.adicionarReserva(cartuchos);
				gerenciador.audio().tocar(somCartucho, 1f);
				mostrarMensagem("+" + cartuchos + " cartuchos");
			} else {
				usarNoPredio();
			}
		}
	}

	/** O que dá para usar com E dentro da Univali, do mais perto ao mais longe. */
	private enum Uso { ESCOPETA, CRACHA, PORTA_PROFESSOR, PROFESSOR, PORTA_ENTRADA, PORTA_TRANCADA }

	private Uso usoPerto() {
		var p = mundo.predio;
		if (!objetivos.temEscopeta() && p.perto(jogador.x, jogador.z, p.escopetaNoModelo())) return Uso.ESCOPETA;
		if (objetivos.podePegarCracha() && p.perto(jogador.x, jogador.z, p.chaveNoModelo())) return Uso.CRACHA;
		float[] prof = p.professorNoModelo();
		if (objetivos.portaProfessorAberta() && p.perto(jogador.x, jogador.z, new float[] { prof[0], prof[1] })) return Uso.PROFESSOR;
		if (!objetivos.portaProfessorAberta() && p.perto(jogador.x, jogador.z, p.centroPortaProfessor())) return Uso.PORTA_PROFESSOR;
		if (!objetivos.portaEntradaAberta() && p.dentro(jogador.x, jogador.z)
				&& p.perto(jogador.x, jogador.z, p.centroPortaEntrada())) return Uso.PORTA_ENTRADA;
		if (p.salaTrancadaPerto(jogador.x, jogador.z) != null) return Uso.PORTA_TRANCADA;
		return null;
	}

	/**
	 * Pega a escopeta e a carta no balcão: a carta abre na tela (como os arquivos de Resident Evil),
	 * a lanterna presa na arma acende, as dicas da arma aparecem e, se o professor já mandou buscar
	 * as provas, a porta da frente abre.
	 */
	private void pegarEscopeta() {
		objetivos.pegarEscopeta();
		desenhoPredio.escopetaPega = true;
		gerenciador.audio().tocar(somCartucho, 1f);
		if (!lanterna.ligada()) {
			lanterna.alternar();
			gerenciador.audio().tocar(cliqueLanterna, 0.8f);
		}
		tutorial.registrar(Tutorial.Acao.ARMOU);
		abrirPortaEntradaSePuder();
		gerenciador.sobrepor(new TelaCarta(gerenciador, null));
	}

	/** A porta da frente abre quando o jogador já falou com o professor e pegou a escopeta. */
	private void abrirPortaEntradaSePuder() {
		if (objetivos.portaEntradaAberta() && !desenhoPredio.portaEntradaAberta) {
			mundo.colisao.removerRetangulo(mundo.predio.portaEntrada());
			desenhoPredio.portaEntradaAberta = true;
		}
	}

	/** A escopeta deitada no balcão do saguão, até ser pega (o cano apontando para a TV). */
	private void desenharEscopetaNoBalcao() {
		if (objetivos.temEscopeta()) return;
		var p = mundo.predio;
		float[] e = entidades.PredioUnivali.ESCOPETA;
		float[] pos = p.noMundo(e[0], e[1], e[2]);
		// o giro do prédio: para onde vai o eixo x do modelo
		float[] origem = p.noMundo(0, 0, 0), eixoX = p.noMundo(1, 0, 0);
		float giroPredio = DragonKnight.guinadaPara(eixoX[0] - origem[0], eixoX[2] - origem[2]);
		org.lwjgl.util.vector.Matrix4f m = new org.lwjgl.util.vector.Matrix4f();
		m.translate(new org.lwjgl.util.vector.Vector3f(pos[0], pos[1], pos[2]));
		m.rotate(giroPredio - (float) Math.PI / 2, new org.lwjgl.util.vector.Vector3f(0, 1, 0)); // o cano (-z) para +x do modelo
		m.rotate((float) Math.PI / 2, new org.lwjgl.util.vector.Vector3f(0, 0, 1)); // deitada de lado
		desenhoEscopeta.desenharNoMundo(bufferProjecao, bufferVisao, ambiente, m);
	}

	/** O crachá, as portas e o professor. */
	private void usarNoPredio() {
		Uso uso = usoPerto();
		if (uso == null) return;
		switch (uso) {
		case ESCOPETA -> pegarEscopeta();
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
				List<String> resposta = objetivos.respostaDaPorta();
				if (!resposta.isEmpty()) { // a primeira vez: ele responde lá de dentro, e o crachá passa a existir
					falas.clear();
					falas.addAll(resposta);
					falaComVoz = null;
					tempoFala = 0;
					desenhoPredio.crachaVisivel = true;
					mostrarMensagem("Trancada.", 2f);
				} else {
					mostrarMensagem("Trancada. Procure o crachá dele nas salas.", 3f);
				}
			}
		}
		case PROFESSOR -> falarComProfessor();
		case PORTA_ENTRADA -> {
			gerenciador.audio().tocar(somVazia, 1f);
			if (objetivos.falouComProfessor() && !objetivos.temEscopeta()) {
				mostrarMensagem("Lá fora tem alguma coisa. Melhor não sair desarmado.", 3f);
			} else {
				mostrarMensagem("A porta da frente está trancada.", 2.5f);
			}
		}
		case PORTA_TRANCADA -> {
			gerenciador.audio().tocar(somVazia, 1f);
			mostrarMensagem(mundo.predio.salaTrancadaPerto(jogador.x, jogador.z).nome() + ": trancada.", 2.5f);
		}
		}
	}

	/**
	 * A voz do professor numa fala: a gravação dela (audio/voz_professor/, ver Objetivos.arquivoDeVoz)
	 * ou, sem o arquivo, o texto entre aspas vira um sussurro "sem palavras" (sílabas
	 * sintetizadas com o timbre das vogais do texto), saindo da cabeça dele, em 3D; com o jogador fora da
	 * sala, a voz sai da porta (ele responde lá de dentro) e se ouve de mais longe. Cada frase é
	 * gerada uma vez (o Audio guarda pelo nome). Falas sem letras ("...") ficam em silêncio.
	 * Devolve a duração da voz, em segundos (0 = sem voz).
	 */
	private float falarComVoz(String fala) {
		int a = fala.indexOf('"'), b = fala.lastIndexOf('"');
		String texto = a >= 0 && b > a ? fala.substring(a + 1, b) : fala;
		if (texto.chars().noneMatch(Character::isLetter)) return 0;
		Audio audio = gerenciador.audio();
		float[] prof = mundo.predio.professorNoModelo();
		float[] cabeca = mundo.predio.noMundo(prof[0], 1.3f, prof[1]);
		float referencia = 3f;
		if (!naSalaDoProfessor) {
			GradeColisao.Retangulo porta = mundo.predio.portaProfessor();
			cabeca[0] = (porta.xMin() + porta.xMax()) / 2;
			cabeca[2] = (porta.zMin() + porta.zMax()) / 2;
			referencia = 6f;
		}
		String gravacao = jogo.Objetivos.arquivoDeVoz(texto);
		if (gravacao != null && Caminhos.existe(gravacao)) { // a voz gravada (já com o efeito)
			Efeito voz = audio.carregarEfeito(gravacao);
			audio.tocar3D(voz, cabeca[0], cabeca[1], cabeca[2], 1f, false, referencia);
			return audio.duracao(voz);
		}
		short[] amostras = Sintetizador.voz(44100, texto, 0.6, 7);
		Efeito voz = audio.criarEfeito("voz do professor: " + texto, amostras, 44100);
		audio.tocar3D(voz, cabeca[0], cabeca[1], cabeca[2], 1f, false, referencia);
		return amostras.length / 44100f;
	}

	/**
	 * As lâmpadas falhando: o brilho de cada uma vai para o desenho; quando uma rajada começa perto
	 * do jogador, o estalo da lâmpada sai dela (3D). Na falha geral, o som sai da lâmpada mais perto,
	 * mais forte.
	 */
	private void atualizarLuzes(float dt) {
		boolean dentro = mundo.predio.dentro(jogador.x, jogador.z);
		List<fase.AndarUnivali.Lampada> lampadas = mundo.predio.andar.lampadas();
		for (jogo.FalhasDeLuz.Evento e : falhasDeLuz.atualizar(dt, dentro)) {
			int i = e.geral() ? lampadaMaisPerto() : e.lampada();
			if (i < 0) continue;
			float[] w = mundo.predio.noMundo(lampadas.get(i).x(), entidades.PredioUnivali.PE_DIREITO - 0.1f, lampadas.get(i).z());
			if (e.geral() || Math.hypot(w[0] - jogador.x, w[2] - jogador.z) < 18) {
				gerenciador.audio().tocar3D(somLampada, w[0], w[1], w[2], e.geral() ? 1f : 0.55f, false, e.geral() ? 5f : 2.5f);
			}
		}
		for (int i = 0; i < falhasDeLuz.quantidade(); i++) desenhoPredio.brilhoLampadas[i] = falhasDeLuz.brilho(i);
	}

	/** As portas que abrem sozinhas: quando uma começa a abrir, a colisão sai e o rangido sai dela. */
	private void atualizarPortas(float dt) {
		float[] m = mundo.predio.paraModelo(jogador.x, jogador.z);
		List<fase.AndarUnivali.Sala> salas = mundo.predio.andar.portasQueAbremSozinhas();
		for (int i : portasSozinhas.atualizar(dt, tempo, m[0], m[1])) {
			mundo.colisao.removerRetangulo(colisaoPortas.get(i));
			float[] c = salas.get(i).porta();
			float[] w = mundo.predio.noMundo(c[0], 1.2f, c[1]);
			gerenciador.audio().tocar3D(somPorta, w[0], w[1], w[2], 1f, false, 3f);
		}
		for (int i = 0; i < portasSozinhas.quantidade(); i++) desenhoPredio.anguloPortas[i] = portasSozinhas.angulo(i);
	}

	/** A lâmpada acesa mais perto do jogador (-1 se nenhuma). */
	private int lampadaMaisPerto() {
		List<fase.AndarUnivali.Lampada> lampadas = mundo.predio.andar.lampadas();
		int melhor = -1;
		double menor = Double.MAX_VALUE;
		for (int i = 0; i < lampadas.size(); i++) {
			if (lampadas.get(i).estado() == fase.AndarUnivali.Lampada.Estado.QUEIMADA) continue;
			float[] w = mundo.predio.noMundo(lampadas.get(i).x(), 0, lampadas.get(i).z());
			double d = Math.hypot(w[0] - jogador.x, w[2] - jogador.z);
			if (d < menor) {
				menor = d;
				melhor = i;
			}
		}
		return melhor;
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
		if (partida.todasColetadas() && monstro.lutando()) {
			mostrarMensagem("Agora não! Ele está aqui.", 2f); // primeiro, sobreviver
			return;
		}
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
		if (primeira) abrirPortaEntradaSePuder();
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
		avisarProvasAoJubilado();
		diretor.provaColetada(); // a floresta reage: a próxima ameaça vem logo
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

	/** Depuração (F3 + P): a porta da sala, o crachá, a porta de novo, o professor, as provas e o professor de novo. */
	private void irParaOProximoObjetivo() {
		var p = mundo.predio;
		if (objetivos.podePegarCracha()) {
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
		boolean atingivel = monstro.atingivel();
		List<entidades.Atingivel> alvos = new java.util.ArrayList<>();
		alvos.add(monstro);
		for (Jubilado j : jubilados) {
			atingivel |= j.atingivel();
			alvos.add(j);
		}
		Tiro.Resultado r = Tiro.disparar(origem, frente, direita, cima, alvos, mundo.colisao, mundo.chao, random);
		for (Jubilado j : jubilados) j.ouvirTiro(c.x, c.z); // o estouro se ouve de longe (só o da vez liga)
		partida.registrarTiro(Regras.BAGOS, r.acertos());
		if (atingivel) {
			System.out.println("[Tiro] " + r.acertos() + " de " + Regras.BAGOS + " chumbos acertaram o monstro");
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
		atualizarLuzes(dt);
		atualizarPortas(dt);
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
		// as provas no canto só aparecem quando o professor termina de passar a missão
		if (tempoMissao < 0 && objetivos.falouComProfessor() && falas.isEmpty()) tempoMissao = 0;
		if (tempoMissao >= 0) tempoMissao += dt;
		// com as 3 provas, a volta para a Univali: os Jubilados ficam para trás e o Dragon Knight acorda lá dentro
		if (partida.todasColetadas() && !voltouComAsProvas && mundo.predio.dentro(jogador.x, jogador.z)) {
			voltouComAsProvas = true;
			diretor.encerrar();
			acordarDragonKnight(); // e ele vem logo na entrada
			// uma caixa de munição no saguão, na frente do balcão, para a luta final
			float[] c = mundo.predio.noMundo(-1.6f, 0, 7.7f);
			caixas.add(new CaixaMunicao(c[0], c[1], c[2], 0.4f, Regras.CARTUCHOS_NA_VOLTA));
		}
		// os Jubilados acordam na primeira vez que o jogador põe os pés fora do prédio
		if (!saiuDaUnivali && !mundo.predio.dentro(jogador.x, jogador.z)) {
			saiuDaUnivali = true;
			for (Jubilado j : jubilados) j.ativo = true;
			tutorial.registrar(Tutorial.Acao.SAIU);
			gerenciador.audio().parar(somZumbido); // lá fora o zumbido do saguão não faz falta
			somZumbido = null;
		}
		andado += (float) Math.hypot(jogador.x - ultimoX, jogador.z - ultimoZ);
		ultimoX = jogador.x;
		ultimoZ = jogador.z;
		if (andado > 2) tutorial.registrar(Tutorial.Acao.ANDOU);
		if (despertar.terminou()) tutorial.atualizar(dt); // as dicas, só depois de acordar
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

	/**
	 * Os sustos falsos, só na floresta e com o monstro fora de cena: a silhueta (na borda do facho,
	 * some se a luz a pegar, se o jogador chegar perto, se o monstro aparecer ou depois de um
	 * tempo), a risada longe e o galho atrás.
	 */
	private void atualizarSustosFalsos(float dt) {
		boolean naFloresta = tempoEntrega < 0 && saiuDaUnivali && !mundo.predio.dentro(jogador.x, jogador.z);
		boolean monstroEmCena = monstro.visivel();
		Camera c = jogador.camera();
		if (silhueta != null) {
			silhueta[4] += dt;
			float d = (float) Math.hypot(silhueta[0] - c.x, silhueta[2] - c.z);
			if (monstroEmCena || !naFloresta || d < SustosFalsos.SILHUETA_SOME_PERTO
					|| silhueta[4] > SustosFalsos.SILHUETA_DURACAO
					|| SustosFalsos.iluminado(lanterna.ligada(), c.x, c.z, c.frente.x, c.frente.z, silhueta[0], silhueta[2])) {
				silhueta = null;
			}
			return;
		}
		SustosFalsos.Tipo tipo = sustos.atualizar(dt, naFloresta, monstroEmCena);
		if (tipo == null) return;
		Random r = sustos.random();
		float olhando = (float) Math.atan2(c.frente.x, c.frente.z);
		if (tipo == SustosFalsos.Tipo.SILHUETA) {
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

	/** O monstro, o batimento, a lanterna em pânico e, no fim do ataque, o susto. */
	private void atualizarMonstro(long diftime) {
		float dt = diftime / 1000f;
		Camera olhos = jogador.camera(); // ele mira o jogador, mesmo com a câmera livre ligada
		monstro.definirAlvo(olhos.x, olhos.y, olhos.z, olhos.frente.x, olhos.frente.z);
		monstro.SimulaSe(diftime);
		tremor = Math.max(0, tremor - dt * 2.5f);
		if (metalRestante > 0) { // a música está tocando: anda junto, para a próxima vez continuar dela
			segundosMetal += dt;
			metalRestante -= dt;
		}
		if (sangrando > 0) { // depois do golpe, ele continua sangrando
			sangrando -= dt;
			particulas.jorroDeSangue(monstro.x, monstro.y + 1.5f, monstro.z, monstro.x - jogador.x, monstro.z - jogador.z, 2);
		}

		// quanto mais perto ele vem, mais a lanterna treme, a imagem pesa e o coração bate
		DragonKnight.Estado estado = monstro.estado();
		boolean ameacando = monstro.lutando() && monstro.visivel();
		float perto = ameacando ? Math.max(0, 1 - (float) Math.hypot(monstro.x - jogador.x, monstro.z - jogador.z) / 20f) : 0;
		lanterna.definirPanico(ameacando ? 0.2f + 0.8f * perto : 0);
		float presenca = switch (estado) {
		case SURGINDO -> 0.6f;
		case AVANCANDO, DESVIANDO -> 0.25f + 0.6f * perto;
		case ATACANDO -> 1f;
		default -> 0f;
		};
		presenca = Math.max(presenca, presencaJubilado());
		presencaMonstro += (presenca - presencaMonstro) * Math.min(1, dt * 4);
		atualizarSustosFalsos(dt);
		if (ameacando && !temMusicaMonstro) {
			// batimento acelerando: de 0,8 s entre batidas (longe) até 0,3 s (encostado)
			proximaBatida -= dt;
			if (proximaBatida <= 0) {
				gerenciador.audio().tocar(somBatida, 0.6f + 0.4f * perto);
				proximaBatida = 0.8f - 0.5f * perto;
			}
		} else {
			proximaBatida = 0;
		}
		if (monstro.ataqueTerminou()) {
			comecarSusto();
		}
	}

	/** O desenho, os sons e os eventos do Jubilado. */
	private void prepararJubilado() {
		desenhoJubilado = new DesenhoMonstro(mundo.jubilado, DesenhoMonstro.JUBILADO);
		somPisadaJubilado = efeitoOuSintetizado("audio/jubilado_passo.ogg", () -> Sintetizador.pisadaPesada(44100, 21));
		somGrunhido = efeitoOuSintetizado("audio/jubilado_grunhido.ogg", () -> Sintetizador.rugido(44100, 1500, 75, 52, 0.7, 31));
		somGritoJubilado = efeitoOuSintetizado("audio/jubilado_grito.ogg",
				() -> Sintetizador.rugido(44100, 1200, 165, 70, 0.75, 33));
		somQuedaJubilado = efeitoOuSintetizado("audio/jubilado_queda.ogg", () -> Sintetizador.rugido(44100, 1300, 95, 35, 0.5, 35));
		somGlitchJubilado = Caminhos.existe("audio/jubilado_glitch.ogg") ? gerenciador.audio().carregarEfeito("audio/jubilado_glitch.ogg")
				: null;
		sussurrosJubilado = carregarVariacoes("audio/jubilado_sussurro_");
		if (sussurrosJubilado.length == 0) { // sem os arquivos, um sussurro sintetizado
			sussurrosJubilado = new Efeito[] { gerenciador.audio().criarEfeito("sussurro do jubilado",
					Sintetizador.sussurro(44100, 41), 44100) };
		}
		Audio audio = gerenciador.audio();
		// todos os sons saem de onde ele está: com fone, dá para saber de que lado ele vem
		Jubilado.Eventos eventosJubilado = new Jubilado.Eventos() {
			@Override
			public void passo(Jubilado j) {
				// caçando, as pisadas ficam mais fortes
				float volume = j.estado() == Jubilado.Estado.CACANDO ? 1f : 0.7f;
				audio.tocar3D(somPisadaJubilado, j.x, j.y + 0.1f, j.z, volume, false, 4f);
			}

			@Override
			public void grunhiu(Jubilado j) {
				audio.tocar3D(somGrunhido, j.x, j.y + 1.7f, j.z, 0.8f, false, 6f);
			}

			@Override
			public void sussurrou(Jubilado j) {
				Efeito s = sussurrosJubilado[random.nextInt(sussurrosJubilado.length)];
				audio.tocar3D(s, j.x, j.y + 1.7f, j.z, 0.9f, false, 8f);
			}

			@Override
			public void viu(Jubilado j) {
				// o susto: o glitch na hora (o som e a imagem falhando) e, logo depois, a fala
				if (somGlitchJubilado != null) {
					audio.tocar(somGlitchJubilado, 0.9f);
					audio.musica().abafar(ABAFAR_NA_FALA, Math.round(ATRASO_FALA * 1000));
				}
				presencaMonstro = 1;
				tremor = Math.max(tremor, 0.6f);
				jogo.FalasJubilado.Fala fala = falasJubilado.aoVer(tempo, partida.provasColetadas());
				if (fala != null) {
					quemVaiFalar = j;
					falaPendente = fala;
					atrasoFala = ATRASO_FALA;
				} else if (somGlitchJubilado == null) {
					gritarOuFalar(j, null); // sem o glitch, ao menos o grito
				}
			}

			@Override
			public void investiu(Jubilado j) {
				// o bote: um grunhido forte, de perto, e a tela treme um pouco
				audio.tocar3D(somGrunhido, j.x, j.y + 1.7f, j.z, 1f, false, 8f);
				tremor = Math.max(tremor, 0.3f);
			}

			@Override
			public void caiu(Jubilado j) {
				audio.tocar3D(somQuedaJubilado, j.x, j.y + 1.2f, j.z, 1f, false, 8f);
			}

			@Override
			public void levantou(Jubilado j) {
				gritarOuFalar(j, falasJubilado.aoLevantar(tempo, partida.provasColetadas()));
			}

			@Override
			public void atacou(Jubilado j) {
				tremor = 0.7f;
				audio.tocar(somGritoJubilado, 1f);
			}
		};
		for (Jubilado j : jubilados) j.definirEventos(eventosJubilado);
	}

	/** Quanto sobra da música enquanto o Jubilado fala (0 a 1). */
	private static final float ABAFAR_NA_FALA = 0.25f;

	/** A fala do Jubilado (a voz saindo da cabeça dele, com a legenda); sem fala ou sem o arquivo, o grito. */
	private void gritarOuFalar(Jubilado j, jogo.FalasJubilado.Fala fala) {
		Audio audio = gerenciador.audio();
		if (fala != null && Caminhos.existe(fala.arquivo())) {
			Efeito voz = audio.carregarEfeito(fala.arquivo());
			audio.tocar3D(voz, j.x, j.y + 1.7f, j.z, 1f, false, 20f);
			float duracao = audio.duracao(voz);
			mostrarMensagem(fala.legenda(), duracao + 0.8f);
			// enquanto ele fala, a música baixa (ducking) e depois volta: a fala por cima do metal
			audio.musica().abafar(ABAFAR_NA_FALA, Math.round(duracao * 1000));
		} else {
			audio.tocar3D(somGritoJubilado, j.x, j.y + 1.7f, j.z, 1f, false, 10f);
		}
	}

	/**
	 * A música de metal toca enquanto algum monstro está atrás do jogador (o Dragon Knight em cena,
	 * o Jubilado caçando). Ela entra quando o primeiro pede e só sai quando os dois param.
	 */
	private void pedirMetal(boolean dragonKnight, boolean ligar) {
		boolean antes = metalDragon || metalJubilado;
		if (dragonKnight) metalDragon = ligar;
		else metalJubilado = ligar;
		boolean depois = metalDragon || metalJubilado;
		if (!antes && depois) {
			gerenciador.audio().musica().tocar(MUSICA_MONSTRO, true, segundosMetal);
			gerenciador.audio().musica().fadeIn(ENTRADA_METAL_MS);
			metalRestante = Float.MAX_VALUE;
		} else if (antes && !depois) {
			gerenciador.audio().musica().fadeOut(SAIDA_METAL_MS);
			metalRestante = SAIDA_METAL_MS / 1000f;
		}
	}

	/**
	 * O diretor de sustos decide de quem é a vez (o Dragon Knight ou um Jubilado); os Jubilados
	 * ficam sabendo onde o jogador está só se o virem ou ouvirem. Dentro da Univali o jogador está
	 * escondido; com o Dragon Knight em cena, eles param e esperam.
	 */
	private void atualizarJubilados(long diftime) {
		diretor.atualizar(diftime / 1000f, partida.provasColetadas(), jogador.x, jogador.z,
				saiuDaUnivali && !voltouComAsProvas);
		boolean escondido = mundo.predio.dentro(jogador.x, jogador.z) || tempoEntrega >= 0;
		boolean andando = jogador.velocidade() > 0.5f;
		boolean dragonEmCena = monstro.lutando() && monstro.visivel();
		Camera olhos = jogador.camera();
		// a música de metal toca enquanto algum caçador está atrás do jogador
		boolean cacando = algumJubilado(Jubilado.Estado.CACANDO);
		if (cacando != metalJubilado) pedirMetal(false, cacando);
		// a fala que vem depois do glitch
		if (falaPendente != null) {
			atrasoFala -= diftime / 1000f;
			if (atrasoFala <= 0) {
				gritarOuFalar(quemVaiFalar, falaPendente);
				falaPendente = null;
			}
		}
		for (Jubilado j : jubilados) {
			j.definirAlvo(jogador.x, olhos.y, jogador.z, jogador.correndo(), andando, lanterna.ligada(), escondido);
			j.definirOlhar(olhos.frente.x, olhos.frente.z);
			j.pausado = dragonEmCena;
			j.SimulaSe(diftime);
			if (j.ataqueTerminou() && jubiladoDoSusto == null) {
				jubiladoDoSusto = j;
				comecarSusto();
			}
		}
	}

	/** Algum Jubilado está nesse estado? */
	private boolean algumJubilado(Jubilado.Estado estado) {
		for (Jubilado j : jubilados) {
			if (j.estado() == estado) return true;
		}
		return false;
	}

	/** O Jubilado ronda as provas que o jogador ainda não pegou. */
	private void avisarProvasAoJubilado() {
		List<float[]> faltam = partida.provas().stream().filter(p -> !p.coletada()).map(p -> new float[] { p.x, p.z })
				.toList();
		for (Jubilado j : jubilados) j.provasQueFaltam = faltam;
	}

	/** De 0 (caçando de longe) a 1 (encostado): o quanto o Jubilado caçando "pesa" na imagem. */
	private float presencaJubilado() {
		if (algumJubilado(Jubilado.Estado.ATACANDO)) return 1;
		float presenca = 0;
		for (Jubilado cacador : diretor.cacadores()) {
			if (cacador.estado() != Jubilado.Estado.CACANDO) continue;
			float d = (float) Math.hypot(cacador.x - jogador.x, cacador.z - jogador.z);
			presenca = Math.max(presenca, 0.45f * Math.max(0, 1 - d / 25f));
		}
		return presenca;
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
		float guinada = DragonKnight.guinadaPara(-fx, -fz); // a frente dele aponta para o jogador
		float[] olho = jubiladoDoSusto != null ? desenhoJubilado.olhoLocal(jubiladoDoSusto.animador())
				: desenhoMonstro.olhoLocal(monstro.animador());
		float cg = (float) Math.cos(guinada), sg = (float) Math.sin(guinada);
		// onde o olho deve ficar, menos o olho girado (o mesmo giro do modelo) = onde ficam os pés
		float px = c.x + fx * 0.7f - (cg * olho[0] + sg * olho[2]);
		float pz = c.z + fz * 0.7f - (-sg * olho[0] + cg * olho[2]);
		float py = c.y - olho[1];
		if (jubiladoDoSusto != null) {
			jubiladoDoSusto.guinada = guinada;
			jubiladoDoSusto.x = px;
			jubiladoDoSusto.y = py;
			jubiladoDoSusto.z = pz;
		} else {
			monstro.guinada = guinada;
			monstro.x = px;
			monstro.y = py;
			monstro.z = pz;
		}
	}

	/** Lê o teclado e o mouse e passa os comandos para o jogador. */
	private void comandarJogador(Entrada entrada) {
		float frente = 0, lado = 0;
		if (!despertar.podeAndar()) { // ainda acordando: parado (e, antes de levantar a cabeça, sem olhar em volta)
			jogador.comandar(0, 0, false);
			if (!despertar.podeOlhar()) return;
			float s = CameraLivre.SENSIBILIDADE_MOUSE * Opcoes.atual().sensibilidade;
			jogador.girar(entrada.deltaMouse().x * s, (Opcoes.atual().inverterY ? 1 : -1) * entrada.deltaMouse().y * s);
			return;
		}
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
		ambiente.intensidadeLanterna *= 1 - despertar.deitado(); // a lanterna só acende quando ele levanta a cabeça
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
		// O prédio vem primeiro: dentro dele, o piso e as paredes gravam a profundidade antes e a placa
		// descarta o terreno escondido sem calcular a cor (o shader do terreno é o mais caro do jogo).
		// Culling: as faces viradas para longe da câmera nem viram pixels. As caixas do prédio estão em
		// sentido anti-horário vistas de fora (o padrão: some a de trás); os triângulos do terreno estão
		// em sentido horário vistos de cima, por isso nele some a "da frente" (a de baixo do chão).
		// As árvores, as pedras e os monstros ficam sem culling (as folhas são vistas dos dois lados).
		glEnable(GL_CULL_FACE);
		glCullFace(GL_BACK);
		desenhoPredio.desenharSolido(bufferProjecao, bufferVisao, ambiente, filtroPs1.altura());
		glCullFace(GL_FRONT);
		desenharTerreno();
		glCullFace(GL_BACK);
		glDisable(GL_CULL_FACE);
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
		for (Jubilado j : jubilados) {
			desenhoJubilado.desenhar(j, bufferProjecao, bufferVisao, ambiente, camera.x, camera.z, alturaImagem);
		}
		desenharCaixas();
		desenhoPlacas.desenhar(mundo.placas, mundo.chao, bufferProjecao, bufferVisao, ambiente);
		desenhoPredio.desenharHalos(bufferProjecao, bufferVisao, ambiente, alturaImagem); // transparentes: depois do sólido
		desenharEscopetaNoBalcao();
		desenhoProvas.desenhar(partida.provas(), bufferProjecao, bufferVisao, ambiente, alturaImagem);
		particulas.desenhar(bufferProjecao, bufferVisao, ambiente, alturaImagem);
		desenhoProvas.desenharFeixes(partida.provas(), camera.x, camera.z, bufferProjecao, bufferVisao, ambiente);
		// no susto, só a cara dele (e acordando, ou antes de pegar a escopeta no balcão, sem arma)
		if (tempoSusto < 0 && despertar.deitado() <= 0 && objetivos.temEscopeta()) {
			desenhoEscopeta.desenhar(escopeta, ambiente, gerenciador.proporcao(), alturaImagem);
		}
		// a passagem para a tela: amplia (modo PS1), escurece os cantos, granula e, com o monstro, distorce
		filtroPs1.vinheta = 1;
		filtroPs1.granulacao = 1;
		filtroPs1.aberracao = presencaMonstro;
		filtroPs1.desfoque = despertar.desfoque();
		filtroPs1.tempo = tempo;
		filtroPs1.terminar(gerenciador.largura(), gerenciador.altura());
		if (fim == Fim.DERROTA && fotoFim == null) {
			fotoFim = fotografar(); // antes do HUD: só a cena
		}

		Overlay2D overlay = Recursos.overlay();
		overlay.iniciar(gerenciador.larguraJanela(), gerenciador.alturaJanela());
		desenharEfeitosMonstro(overlay);
		if (tempoSusto < 0 && fim == null && tempoEntrega < 0 && despertar.terminou()) {
			if (objetivos.temEscopeta()) desenharHudArma(overlay); // a mira e os cartuchos só com a arma
			desenharHudTextos(overlay); // as dicas, o objetivo e as falas valem desde o começo
		}
		if (!despertar.terminou()) desenharPalpebras(overlay);
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
		for (Jubilado j : jubilados) {
			desenhoJubilado.desenhar(j, bufferProjecao, bufferVisao, ambiente, camera.x, camera.z, ladoSombra);
		}
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

	}

	/**
	 * Os textos da partida, com ou sem a escopeta: as provas no canto, a dica do E (pegar, abrir,
	 * falar), o objetivo no alto, as falas, as dicas do começo e as mensagens.
	 */
	private void desenharHudTextos(Overlay2D o) {
		int w = gerenciador.larguraJanela(), h = gerenciador.alturaJanela();
		float cx = w / 2f;
		float escala = Math.max(1f, h / 800f);
		desenharHudProvas(o, escala);
		Uso uso = usoPerto();
		if (uso != null && partida.provaAlcancavel(jogador.x, jogador.z) == null && caixaAlcancavel() == null) {
			String dica = switch (uso) {
			case CRACHA -> "E: pegar o crachá";
			case ESCOPETA -> "E: pegar a escopeta e a carta";
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

	/** "M1 M2 M3" no canto superior esquerdo, depois da missão do professor: apagadas até serem pegas; ao pegar, acendem com um pulso. */
	private void desenharHudProvas(Overlay2D o, float escala) {
		if (tempoMissao < 0) return; // antes da missão, o jogador nem sabe das provas
		float aparece = Math.min(1, tempoMissao / 0.8f);
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
					new Color(1f, 1f, 1f, (p.coletada() ? 1f : 0.55f) * aparece));
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
		float deitado = despertar.deitado();
		if (deitado > 0 && cameraLivre == null) {
			// a cabeça na carteira: os olhos mais baixos, olhando para a mesa e tombados para o lado.
			// Aplicado depois da visão (no espaço da câmera): baixar os olhos = subir o mundo
			Matrix4f cabeca = new Matrix4f();
			cabeca.rotate(jogo.Despertar.ROLAGEM_DEITADO * deitado, new Vector3f(0, 0, 1));
			cabeca.rotate(-jogo.Despertar.ARFAGEM_DEITADO * deitado, new Vector3f(1, 0, 0));
			cabeca.translate(new Vector3f(0, jogo.Despertar.ALTURA_DEITADO * deitado, 0));
			Matrix4f.mul(cabeca, visao, visao);
		}
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
	 * As pálpebras do despertar: duas faixas pretas (de cima e de baixo) que se abrem e fecham,
	 * com a borda esmaecida; enquanto a visão está borrada, tudo também um pouco mais escuro.
	 */
	private void desenharPalpebras(Overlay2D o) {
		int w = gerenciador.larguraJanela(), h = gerenciador.alturaJanela();
		float aberto = despertar.palpebras();
		o.retangulo(0, 0, w, h, new Color(0f, 0f, 0f, 0.45f * despertar.desfoque()));
		float fechado = h / 2f * (1 - aberto) + 1, borda = h * 0.08f;
		o.retangulo(0, 0, w, fechado, Color.BLACK);
		o.retangulo(0, h - fechado, w, fechado, Color.BLACK);
		for (int i = 0; i < 10; i++) { // a borda esmaecida da pálpebra
			float a = 1 - (i + 1) / 11f, y = fechado + i * borda / 10;
			o.retangulo(0, y, w, borda / 10 + 1, new Color(0f, 0f, 0f, a));
			o.retangulo(0, h - y - borda / 10, w, borda / 10 + 1, new Color(0f, 0f, 0f, a));
		}
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
		if (monstro.lutando() && monstro.visivel()) { // as bordas ficam vermelhas conforme ele chega perto
			float perto = Math.max(0, 1 - (float) Math.hypot(monstro.x - jogador.x, monstro.z - jogador.z) / 15f);
			if (perto > 0) o.imagem(texturaVinheta(), 0, 0, w, h, new Color(1f, 1f, 1f, Math.min(1f, perto)));
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

		if (mundo.terreno.model instanceof obj.ObjHTGsrtmIndexado blocos) {
			// os blocos do terreno que não aparecem ficam de fora: na cena, o que a câmera alcança;
			// no mapa de sombra, só o que a lanterna alcança
			Camera c = cameraAtual();
			blocos.definirVista(c.x, c.z, c.frente.x, c.frente.z,
					ambiente.passadaSombra > 0.5f ? ambiente.alcanceLanterna + 2 : LONGE);
		}
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
		if (ambiente.passadaSombra > 0.5f) {
			// no mapa de sombra, só as árvores que a lanterna alcança (o alcance dela mais a copa)
			mundo.arvores3d.escolher(c.x, c.z, c.frente.x, c.frente.z, ambiente.alcanceLanterna + RAIO_COPA, RAIO_COPA);
		} else {
			mundo.arvores3d.escolher(c.x, c.z, c.frente.x, c.frente.z);
		}

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

	/**
	 * A linha do diretor e dos Jubilados no painel F3: de quem é a vez e quanto falta; depois,
	 * cada Jubilado (o da vez marcado com *), o estado e a distância.
	 */
	private String textoJubilado() {
		StringBuilder b = new StringBuilder(String.format("diretor: %s (%.0f s)   ",
				diretor.vez().name().toLowerCase().replace('_', ' '), Math.max(0, diretor.restante())));
		for (Jubilado j : jubilados) {
			float d = (float) Math.hypot(j.x - jogador.x, j.z - jogador.z);
			b.append(String.format("%sjubilado %s a %.0f m   ", diretor.cacadores().contains(j) ? "*" : "",
					j.estado().name().toLowerCase(), d));
		}
		if (!saiuDaUnivali) b.append("(inativos até sair da Univali)");
		return b.toString();
	}

	/** A linha do monstro no painel F3. */
	private String textoMonstro() {
		String base = "dragon knight " + monstro.estado().name().toLowerCase();
		String detalhe = monstro.lutando() ? String.format(", golpes %d de %d, a %.0f m", monstro.golpes(),
				Regras.GOLPES_PARA_MATAR, Math.hypot(monstro.x - jogador.x, monstro.z - jogador.z)) : "";
		String animacao = monstro.animador() != null ? "   " + monstro.animador().nomeAtual() : "   (substituto)";
		return base + detalhe + animacao + String.format("   susto falso em %.0f s%s", sustos.cronometro(),
				silhueta != null ? " (silhueta em cena)" : "");
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
		// o brilho azulado da TV do saguão, tremulando com o chuvisco
		float[] tv = mundo.predio.noMundo(DesenhoPredio.TV[0], DesenhoPredio.TV[1], DesenhoPredio.TV[2] - 0.4f);
		float tremula = 0.7f + 0.3f * random.nextFloat();
		luzes.add(new float[] { tv[0], tv[1], tv[2], 0.32f * tremula, 0.38f * tremula, 0.5f * tremula, 4.5f });
		List<fase.AndarUnivali.Lampada> lampadas = mundo.predio.andar.lampadas();
		for (int i = 0; i < lampadas.size(); i++) {
			float brilho = falhasDeLuz.brilho(i); // a mesma conta do desenho: a luz pisca junto com a lâmpada
			if (brilho < 0.05f) continue;
			fase.AndarUnivali.Lampada lamp = lampadas.get(i);
			float[] w = mundo.predio.noMundo(lamp.x(), entidades.PredioUnivali.PE_DIREITO - 0.2f, lamp.z());
			luzes.add(new float[] { w[0], w[1], w[2], 0.75f * brilho, 0.82f * brilho, 0.9f * brilho, 7f });
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
	/**
	 * O painel do F3. Cada texto diferente vira uma textura nova (Java2D + envio à placa), e várias
	 * linhas mudam a cada quadro: refeito a cada quadro, o painel custava ~3 ms e derrubava o FPS que
	 * ele mesmo mostra. Agora os textos são refeitos só 4 vezes por segundo.
	 */
	private void desenharDepuracao(Overlay2D overlay) {
		if (linhasDepuracao == null || tempo - tempoDepuracao >= 0.25f || tempo < tempoDepuracao) {
			linhasDepuracao = linhasDepuracao();
			tempoDepuracao = tempo;
		}
		String[] linhas = linhasDepuracao;
		int tamanho = 16;
		float linha = tamanho * 1.45f;
		overlay.retangulo(8, 8, 640, linhas.length * linha + 16, COR_PAINEL);
		for (int i = 0; i < linhas.length; i++) {
			Texto.desenhar(overlay, linhas[i], 18, 16 + i * linha, tamanho, COR_DEPURACAO, Texto.Alinhamento.ESQUERDA);
		}
	}

	private String[] linhasDepuracao() {
		ConsultaAltura chao = mundo.chao;
		Camera c = cameraAtual();
		return new String[] {
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
				textoJubilado(),
				textoProvas(),
				String.format("arma %s   cano %d   reserva %d   partículas %d   caixas %d", escopeta.estado().name().toLowerCase(),
						escopeta.cano(), escopeta.reserva(), particulas.quantidade(),
						caixas.stream().filter(cx -> !cx.pega()).count()),
				"F lanterna   ESC pausa   T som de teste   V vitória   X derrota   G monstro surge   K mata   N Jubilado perto   B luzes   H silhueta   J galho   L risada",
				"clique atira   R recarrega   E pega   P vai até a próxima prova   I salva o mapa da fase   setas e PgUp/PgDn: arma",
		};
	}
}
