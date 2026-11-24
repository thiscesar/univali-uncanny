package telas;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
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

import audio.Audio;
import audio.Efeito;
import audio.Sintetizador;
import audio.SomAmbiente;
import audio.SomTocando;
import dados.Opcoes;
import dados.Recursos;
import entidades.Jogador;
import entrada.Entrada;
import mundo.ConsultaAltura;
import mundo.Mundo;
import mundo.DistribuidorArvores;
import mundo.TexturasFloresta;
import render.Ambiente;
import render.Camera;
import render.CameraLivre;
import render.FiltroPs1;
import render.Lanterna;
import render.Overlay2D;
import render.Texto;
import shaders.ShaderArvore;
import shaders.ShaderArvore3D;
import shaders.ShaderTerrenoFloresta;
import util.Utils3D;

/**
 * A partida: o jogador andando pela floresta à noite, em primeira pessoa, com a lanterna.
 *
 * F liga e desliga a lanterna. ESC abre a pausa por cima. F3 mostra o painel de depuração; F4 troca para a câmera livre
 * (voa e atravessa o chão, para inspecionar o mapa). V e X forçam vitória e derrota, e T liga
 * um som 3D de teste: são atalhos provisórios até existirem as regras do jogo.
 */
public class TelaJogo implements Tela {

	static final String TITULO = "Jogo";

	private static final float CAMPO_DE_VISAO = 70f; // graus, na vertical
	private static final float PERTO = 0.1f;
	private static final float LONGE = 60f;          // além disso a neblina já escondeu tudo

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

	/** Tempo da partida em segundos; para quando a partida está pausada (anima a água). */
	private float tempo = 0;
	// FPS medido em janelas de meio segundo, para o painel
	private int quadrosContados = 0;
	private float tempoContado = 0, fps = 0;

	public TelaJogo(GerenciadorTelas gerenciador, Mundo mundo) {
		this.gerenciador = gerenciador;
		this.mundo = mundo;
		this.jogador = new Jogador(mundo.inicio[0], mundo.inicio[1], mundo.chao, mundo.colisao);
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
	}

	// ---------- atualização ----------

	@Override
	public void atualizar(long diftime) {
		Entrada entrada = gerenciador.entrada();
		if (entrada.foiPressionada(GLFW_KEY_ESCAPE)) {
			gerenciador.sobrepor(new TelaPausa(gerenciador));
			return;
		}
		if (entrada.foiPressionada(GLFW_KEY_V)) {
			gerenciador.trocarPara(new TelaVitoria(gerenciador));
			return;
		}
		if (entrada.foiPressionada(GLFW_KEY_X)) {
			gerenciador.trocarPara(new TelaDerrota(gerenciador));
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

		if (cameraLivre != null) {
			cameraLivre.atualizar(entrada, diftime);
		} else {
			comandarJogador(entrada);
			jogador.SimulaSe(diftime);
			tocarSonsDoJogador();
		}

		tempo += diftime / 1000.0f;
		contarQuadros(diftime);

		Camera c = cameraAtual();
		lanterna.atualizar(c, diftime / 1000f);
		somAmbiente.atualizar(diftime / 1000f, c.x, c.y, c.z);

		// o ouvinte acompanha a câmera: é assim que o OpenAL sabe de que lado vem cada som
		gerenciador.audio().atualizarOuvinte(c.x, c.y, c.z,
				c.frente.x, c.frente.y, c.frente.z, c.cima.x, c.cima.y, c.cima.z);
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
		guardar(cameraAtual().matrizVisao(), bufferVisao);
		lanterna.aplicar(ambiente);
		desenharTerreno();
		if (mundo.arvores3d != null) {
			desenharArvores3D();
		} else {
			desenharArvores();
		}
		if (ps1) {
			filtroPs1.terminar(gerenciador.largura(), gerenciador.altura()); // amplia sem suavização
		}

		Overlay2D overlay = Recursos.overlay();
		overlay.iniciar(gerenciador.larguraJanela(), gerenciador.alturaJanela());
		if (mostrarDepuracao) {
			desenharDepuracao(overlay);
		} else if (tempo < 8) {
			Texto.desenhar(overlay, "F3: depuração", 16, gerenciador.alturaJanela() - 30, 14, COR_DICA,
					Texto.Alinhamento.ESQUERDA);
		}
		overlay.finalizar();
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
				"F lanterna   ESC pausa   T som de teste   V vitória   X derrota",
		};
		int tamanho = 16;
		float linha = tamanho * 1.45f;
		overlay.retangulo(8, 8, 470, linhas.length * linha + 16, COR_PAINEL);
		for (int i = 0; i < linhas.length; i++) {
			Texto.desenhar(overlay, linhas[i], 18, 16 + i * linha, tamanho, COR_DEPURACAO, Texto.Alinhamento.ESQUERDA);
		}
	}
}
