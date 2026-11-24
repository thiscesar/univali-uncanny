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
import jogo.Partida;
import jogo.Regras;
import md2.Animador;
import mundo.ConsultaAltura;
import mundo.Mundo;
import mundo.DistribuidorArvores;
import mundo.TexturasFloresta;
import render.Ambiente;
import render.Camera;
import render.CameraLivre;
import render.DesenhoEscopeta;
import render.DesenhoMonstro;
import render.DesenhoPlacas;
import render.DesenhoProvas;
import render.FiltroPs1;
import render.Lanterna;
import render.Overlay2D;
import render.Particulas;
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
 * G faz o Dragon Knight surgir e K o mata, P leva até a próxima prova; as setas e Page Up/Down ajustam a posição da arma.
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

	private static final float CAMPO_DE_VISAO = 70f; // graus, na vertical
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

	/** Câmera livre de depuração (F4), começando onde o jogador estiver. */
	private CameraLivre cameraLivre = null;
	private boolean mostrarDepuracao = false;

	private final FloatBuffer bufferProjecao = BufferUtils.createFloatBuffer(16);
	private final FloatBuffer bufferVisao = BufferUtils.createFloatBuffer(16);
	private int[] texturasTerreno;

	// sons
	private final Random random = new Random();
	private Efeito[] passos;
	private Efeito respiracao;
	private Efeito cliqueLanterna;
	private SomAmbiente somAmbiente;
	private SomTocando somRespiracao = null;
	private SomTocando somTeste = null;

	// o Dragon Knight
	private final DragonKnight monstro;
	private DesenhoMonstro desenhoMonstro;
	private Efeito somGrito, somMorte, somBatida, somSusto;

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
	private final java.util.Map<Prova, SomTocando> sussurros = new java.util.HashMap<>();
	private Efeito somSussurro, somPegarProva, somSinal;
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
		passos = new Efeito[4];
		for (int i = 0; i < passos.length; i++) {
			passos[i] = audio.carregarEfeito("audio/passo_" + (i + 1) + ".ogg");
		}
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
		somSussurro = efeitoOuSintetizado("audio/sussurro.ogg", () -> Sintetizador.sussurro(44100, 13));
		somPegarProva = efeitoOuSintetizado("audio/pegar_prova.ogg", () -> Sintetizador.ping(44100, 660, 600, 900));
		somSinal = efeitoOuSintetizado("audio/sinal_aula.ogg", () -> Sintetizador.sinal(44100));
		for (Prova p : partida.provas()) {
			// referência de 3 m: o sussurro ainda se ouve a uns 20 m, vindo da direção da folha
			sussurros.put(p, gerenciador.audio().tocar3D(somSussurro, p.x, p.y, p.z, 0.9f, true, 3f));
		}

		caixas = CaixaMunicao.sortear(mundo.chao, mundo.colisao, mundo.inicio, Regras.CAIXAS_MUNICAO, random);
		cuboCaixa = new VboCube();
		cuboCaixa.load();

		monstro.definirEventos(new DragonKnight.Eventos() {
			@Override
			public void surgiu(DragonKnight m) {
				tremor = 1;
				// o grito sai de onde ele está: com fone, dá para saber de que lado ele surgiu
				gerenciador.audio().tocar3D(somGrito, m.x, m.y + 1.6f, m.z, 1f, false);
			}

			@Override
			public void morreu(DragonKnight m) {
				gerenciador.audio().tocar3D(somMorte, m.x, m.y + 1.2f, m.z, 1f, false);
			}

			@Override
			public void atacou(DragonKnight m) {
				tremor = 0.7f;
				gerenciador.audio().tocar(somGrito, 1f);
			}
		});
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
		filtroPs1.liberar();
		desenhoMonstro.liberar();
		desenhoEscopeta.liberar();
		desenhoProvas.liberar();
		desenhoPlacas.liberar();
		for (SomTocando s : sussurros.values()) {
			audio.parar(s);
		}
		sussurros.clear();
		particulas.liberar();
	}

	// ---------- atualização ----------

	@Override
	public void atualizar(long diftime) {
		Entrada entrada = gerenciador.entrada();
		if (fim != null) {
			// o desenhar do quadro anterior já fotografou a tela: agora troca
			if (fotoFim != null || fim == Fim.VITORIA) {
				TelaFim.Resumo resumo = new TelaFim.Resumo(mundo.seed, tempo, monstro.aparicoesSobrevividas());
				gerenciador.trocarPara(fim == Fim.VITORIA ? new TelaVitoria(gerenciador, resumo)
						: new TelaDerrota(gerenciador, resumo, fotoFim));
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
			gerenciador.sobrepor(new TelaPausa(gerenciador));
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
			lanterna.alternar();
			gerenciador.audio().tocar(cliqueLanterna, 0.8f);
		}
		if (mostrarDepuracao && entrada.foiPressionada(GLFW_KEY_G)) {
			monstro.forcarAparicao();
		}
		if (mostrarDepuracao && entrada.foiPressionada(GLFW_KEY_K)) {
			monstro.matar();
		}
		if (mostrarDepuracao) {
			ajustarPoseDaArma(entrada);
		}
		if (mostrarDepuracao && entrada.foiPressionada(GLFW_KEY_P)) {
			irParaAProximaProva();
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
			Prova prova = partida.provaAlcancavel(jogador.x, jogador.z);
			CaixaMunicao perto = caixaAlcancavel();
			if (prova != null) {
				coletar(prova);
			} else if (perto != null) {
				escopeta.adicionarReserva(perto.pegar());
				gerenciador.audio().tocar(somCartucho, 1f);
				mostrarMensagem("+" + Regras.CARTUCHOS_POR_CAIXA + " cartuchos");
			}
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
			mostrarMensagem(prova.nome + " encontrada. Leve as provas à Univali", 5f);
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
		Camera c = jogador.camera();
		float[] origem = { c.x, c.y, c.z };
		float[] frente = { c.frente.x, c.frente.y, c.frente.z };
		float[] direita = { c.direita.x, c.direita.y, c.direita.z };
		float[] cima = { c.cima.x, c.cima.y, c.cima.z };
		// só vale contar os bagos se ele ainda pode levar dano (não conta o corpo caído)
		boolean atingivel = monstro.estado() == DragonKnight.Estado.SURGINDO
				|| monstro.estado() == DragonKnight.Estado.ENCARANDO;
		Tiro.Resultado r = Tiro.disparar(origem, frente, direita, cima, monstro, mundo.colisao, mundo.chao, random);
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
		tempoMensagem = Math.max(0, tempoMensagem - dt);
		for (Prova p : partida.provas()) {
			p.SimulaSe(Math.round(dt * 1000));
		}
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
		tempoMensagem = segundos;
	}

	/** O monstro, o batimento, a lanterna em pânico e, no fim do ataque, o susto. */
	private void atualizarMonstro(long diftime) {
		float dt = diftime / 1000f;
		Camera olhos = jogador.camera(); // ele mira o jogador, mesmo com a câmera livre ligada
		monstro.definirAlvo(olhos.x, olhos.y, olhos.z, olhos.frente.x, olhos.frente.z);
		monstro.SimulaSe(diftime);
		tremor = Math.max(0, tremor - dt * 2.5f);

		DragonKnight.Estado estado = monstro.estado();
		boolean ameacando = estado == DragonKnight.Estado.SURGINDO || estado == DragonKnight.Estado.ENCARANDO;
		float progresso = progressoContagem();
		lanterna.definirPanico(ameacando ? 0.3f + 0.7f * progresso : 0);
		if (ameacando) {
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

		// mouse: o y da tela cresce para baixo, por isso o sinal invertido na arfagem
		float sensibilidade = CameraLivre.SENSIBILIDADE_MOUSE * Opcoes.atual().sensibilidade;
		jogador.girar(entrada.deltaMouse().x * sensibilidade, -entrada.deltaMouse().y * sensibilidade);
	}

	/** Passos a cada passo dado, com volume variando um pouco; respiração em loop com estamina baixa. */
	private void tocarSonsDoJogador() {
		Audio audio = gerenciador.audio();
		for (int i = 0; i < jogador.passosNoQuadro(); i++) {
			Efeito passo = passos[random.nextInt(passos.length)];
			float volume = (jogador.correndo() ? 0.75f : 0.5f) * (0.85f + random.nextFloat() * 0.3f);
			audio.tocar(passo, volume);
		}
		if (jogador.ofegante() && !audio.estaTocando(somRespiracao)) {
			somRespiracao = audio.tocar(respiracao, 0.7f);
		}
		if (!jogador.ofegante() && somRespiracao != null && jogador.estamina() > 0.5f) {
			audio.parar(somRespiracao); // só para quando já recuperou bem (não fica liga-desliga)
			somRespiracao = null;
		}
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
		if (ps1) {
			filtroPs1.comecar(gerenciador.largura(), gerenciador.altura());
			ambiente.resolucaoPs1[0] = filtroPs1.largura();
			ambiente.resolucaoPs1[1] = filtroPs1.altura();
		}
		ambiente.modoPs1 = ps1 ? 1 : 0;

		// fundo da mesma cor da neblina: o que some na neblina se funde com o céu
		glClearColor(ambiente.corCeu[0], ambiente.corCeu[1], ambiente.corCeu[2], 1.0f);
		glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
		glEnable(GL_DEPTH_TEST);

		guardar(projecao(), bufferProjecao);
		guardar(visaoComTremor(), bufferVisao);
		lanterna.aplicar(ambiente);
		desenharTerreno();
		if (mundo.arvores3d != null) {
			desenharArvores3D();
		} else {
			desenharArvores();
		}
		desenharPedras();
		Camera camera = cameraAtual();
		int alturaImagem = ps1 ? filtroPs1.altura() : gerenciador.altura();
		desenhoMonstro.desenhar(monstro, bufferProjecao, bufferVisao, ambiente, camera.x, camera.z, alturaImagem);
		desenharCaixas();
		desenhoPlacas.desenhar(mundo.placas, mundo.chao, bufferProjecao, bufferVisao, ambiente);
		desenhoProvas.desenhar(partida.provas(), bufferProjecao, bufferVisao, ambiente, alturaImagem);
		particulas.desenhar(bufferProjecao, bufferVisao, ambiente, alturaImagem);
		desenhoProvas.desenharFeixes(partida.provas(), camera.x, camera.z, bufferProjecao, bufferVisao, ambiente);
		if (tempoSusto < 0) { // no susto, só a cara dele
			desenhoEscopeta.desenhar(escopeta, ambiente, gerenciador.proporcao(), alturaImagem);
		}
		if (ps1) {
			filtroPs1.terminar(gerenciador.largura(), gerenciador.altura()); // amplia sem suavização
		}
		if (fim == Fim.DERROTA && fotoFim == null) {
			fotoFim = fotografar(); // antes do HUD: só a cena
		}

		Overlay2D overlay = Recursos.overlay();
		overlay.iniciar(gerenciador.larguraJanela(), gerenciador.alturaJanela());
		desenharEfeitosMonstro(overlay);
		if (tempoSusto < 0 && fim == null) {
			desenharHudArma(overlay);
		}
		if (mostrarDepuracao) {
			desenharDepuracao(overlay);
		} else if (tempo < 8) {
			Texto.desenhar(overlay, "F3: depuração", 16, gerenciador.alturaJanela() - 30, 14, COR_DICA,
					Texto.Alinhamento.ESQUERDA);
		}
		overlay.finalizar();
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
		if (partida.provaAlcancavel(jogador.x, jogador.z) != null) {
			Texto.desenhar(o, "E: pegar a prova", cx, h * 0.62f, Math.round(20 * escala), new Color(230, 220, 200),
					Texto.Alinhamento.CENTRO);
		} else if (caixaAlcancavel() != null) {
			Texto.desenhar(o, "E: pegar cartuchos", cx, h * 0.62f, Math.round(20 * escala), new Color(230, 220, 200),
					Texto.Alinhamento.CENTRO);
		}
		if (mensagem != null && tempoMensagem > 0) {
			int alfa = Math.round(255 * Math.min(1, tempoMensagem / 0.5f));
			Texto.desenhar(o, mensagem, cx, h * 0.68f, Math.round(24 * escala), new Color(235, 215, 190, alfa),
					Texto.Alinhamento.CENTRO);
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
		float topo = PERTO * (float) Math.tan(Math.toRadians(CAMPO_DE_VISAO / 2));
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
		float[] u = mundo.univali;
		return sb.append(String.format("   Univali %.0f m", Math.hypot(u[0] - c.x, u[1] - c.z))).toString();
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
		return base + detalhe + animacao + String.format("   sobrevividas %d", monstro.aparicoesSobrevividas());
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
				String.format("posição  x %.1f  z %.1f   olhos %.2f m", c.x, c.z, c.y),
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
				"F lanterna   ESC pausa   T som de teste   V vitória   X derrota   G monstro surge   K mata",
				"clique atira   R recarrega   E pega   P vai até a próxima prova   setas e PgUp/PgDn: posição da arma",
		};
		int tamanho = 16;
		float linha = tamanho * 1.45f;
		overlay.retangulo(8, 8, 640, linhas.length * linha + 16, COR_PAINEL);
		for (int i = 0; i < linhas.length; i++) {
			Texto.desenhar(overlay, linhas[i], 18, 16 + i * linha, tamanho, COR_DEPURACAO, Texto.Alinhamento.ESQUERDA);
		}
	}
}
