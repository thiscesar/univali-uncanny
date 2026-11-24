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
import entidades.DragonKnight;
import entidades.Jogador;
import entrada.Entrada;
import jogo.Regras;
import md2.Animador;
import mundo.ConsultaAltura;
import mundo.Mundo;
import mundo.DistribuidorArvores;
import mundo.TexturasFloresta;
import render.Ambiente;
import render.Camera;
import render.CameraLivre;
import render.DesenhoMonstro;
import render.FiltroPs1;
import render.Lanterna;
import render.Overlay2D;
import render.Texto;
import render.TextoTextura;
import shaders.ShaderArvore;
import shaders.ShaderArvore3D;
import shaders.ShaderTerrenoFloresta;
import util.Caminhos;
import util.Utils3D;

/**
 * A partida: o jogador andando pela floresta à noite, em primeira pessoa, com a lanterna.
 *
 * F liga e desliga a lanterna. ESC abre a pausa por cima. F3 mostra o painel de depuração; F4 troca para a câmera livre
 * (voa e atravessa o chão, para inspecionar o mapa). V e X forçam vitória e derrota, e T liga
 * um som 3D de teste: são atalhos provisórios até existirem as regras do jogo. Com o F3 ligado,
 * G faz o Dragon Knight surgir e K o mata.
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
	private static final float LONGE = 60f;          // além disso a neblina já escondeu tudo

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
		}
		atualizarMonstro(diftime);

		tempo += diftime / 1000.0f;
		contarQuadros(diftime);

		Camera c = cameraAtual();
		lanterna.atualizar(c, diftime / 1000f);
		somAmbiente.atualizar(diftime / 1000f, c.x, c.y, c.z);

		// o ouvinte acompanha a câmera: é assim que o OpenAL sabe de que lado vem cada som
		gerenciador.audio().atualizarOuvinte(c.x, c.y, c.z,
				c.frente.x, c.frente.y, c.frente.z, c.cima.x, c.cima.y, c.cima.z);
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
		Camera camera = cameraAtual();
		desenhoMonstro.desenhar(monstro, bufferProjecao, bufferVisao, ambiente, camera.x, camera.z,
				ps1 ? filtroPs1.altura() : gerenciador.altura());
		if (ps1) {
			filtroPs1.terminar(gerenciador.largura(), gerenciador.altura()); // amplia sem suavização
		}
		if (fim == Fim.DERROTA && fotoFim == null) {
			fotoFim = fotografar(); // antes do HUD: só a cena
		}

		Overlay2D overlay = Recursos.overlay();
		overlay.iniciar(gerenciador.larguraJanela(), gerenciador.alturaJanela());
		desenharEfeitosMonstro(overlay);
		if (mostrarDepuracao) {
			desenharDepuracao(overlay);
		} else if (tempo < 8) {
			Texto.desenhar(overlay, "F3: depuração", 16, gerenciador.alturaJanela() - 30, 14, COR_DICA,
					Texto.Alinhamento.ESQUERDA);
		}
		overlay.finalizar();
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

		// "3", "2", "1": cada número começa grande e encolhe até o próximo segundo (o pulso)
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

	/** Painel F3: números para conferir o que o jogo está calculando. */
	private void desenharDepuracao(Overlay2D overlay) {
		ConsultaAltura chao = mundo.chao;
		Camera c = cameraAtual();
		String[] linhas = {
				String.format("FPS %.0f    seed %d    árvores %d%s", fps, mundo.seed, mundo.quantidadeArvores(),
						mundo.arvores3d != null ? String.format(" (3D, %d desenhadas)", mundo.arvores3d.desenhadas())
								: " (planas)"),
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
				"F lanterna   ESC pausa   T som de teste   V vitória   X derrota   G monstro surge   K mata",
		};
		int tamanho = 16;
		float linha = tamanho * 1.45f;
		overlay.retangulo(8, 8, 640, linhas.length * linha + 16, COR_PAINEL);
		for (int i = 0; i < linhas.length; i++) {
			Texto.desenhar(overlay, linhas[i], 18, 16 + i * linha, tamanho, COR_DEPURACAO, Texto.Alinhamento.ESQUERDA);
		}
	}
}
