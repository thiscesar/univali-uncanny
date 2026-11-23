package telas;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_T;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_V;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_X;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL20.glGetUniformLocation;
import static org.lwjgl.opengl.GL20.glUniform1f;
import static org.lwjgl.opengl.GL20.glUniform1i;
import static org.lwjgl.opengl.GL20.glUniform3f;
import static org.lwjgl.opengl.GL20.glUniformMatrix4fv;

import java.awt.Color;
import java.nio.FloatBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.util.vector.Matrix4f;

import audio.Audio;
import audio.Efeito;
import audio.Sintetizador;
import audio.SomTocando;
import dados.Recursos;
import mundo.ConsultaAltura;
import mundo.Mundo;
import mundo.TexturasFloresta;
import render.CameraLivre;
import render.Overlay2D;
import render.Texto;
import shaders.ShaderTerrenoFloresta;
import util.Utils3D;

/**
 * Partida provisória: o terreno visto pela câmera livre.
 * ESC abre a pausa por cima; V e X forçam vitória e derrota (o D já é andar para a direita).
 */
public class TelaJogo implements Tela {

	static final String TITULO = "JOGO (ESC: pausa, T: som de teste, V: vitória, X: derrota)";

	/** Ponto fixo do som de teste: 10 m à direita de onde a câmera começa. Gire a câmera para ouvi-lo mudar de lado. */
	private static final float[] POSICAO_TESTE = { 160f, 30f, 330f };
	private SomTocando somTeste = null;
	private static final Color COR_ROTULO = new Color(200, 195, 185, 200);

	private static final float CAMPO_DE_VISAO = 60f; // graus, na vertical
	private static final float PERTO = 0.1f;
	private static final float LONGE = 400f; // provisório: dá para ver o mapa inteiro de cima
	private static final float[] COR_CEU = { 0.02f, 0.025f, 0.03f };
	private static final Color COR_DEPURACAO = new Color(160, 155, 145, 200);

	private final GerenciadorTelas gerenciador;
	/** O mundo pronto (terreno e consulta do chão), entregue pela tela de carregamento. */
	private final Mundo mundo;

	/** Começa fora do mapa, ao sul (o mapa vai de 0 a 300 m), olhando para o centro e para baixo. */
	private final CameraLivre camera = new CameraLivre(150f, 30f, 330f, 0f, (float) Math.toRadians(-20));

	private final FloatBuffer bufferProjecao = BufferUtils.createFloatBuffer(16);
	private final FloatBuffer bufferVisao = BufferUtils.createFloatBuffer(16);
	private int[] texturasTerreno;

	/** Tempo da partida em segundos; para quando a partida está pausada (anima a água). */
	private float tempo = 0;

	public TelaJogo(GerenciadorTelas gerenciador, Mundo mundo) {
		this.gerenciador = gerenciador;
		this.mundo = mundo;
	}

	@Override
	public void entrar() {
		gerenciador.definirTitulo(TITULO);
		gerenciador.entrada().prenderCursor(true);
		texturasTerreno = TexturasFloresta.carregar(); // já carregadas na tela de carregamento: aqui só pega
	}

	@Override
	public void sair() {
		gerenciador.entrada().prenderCursor(false);
		gerenciador.audio().parar(somTeste);
		somTeste = null;
	}

	@Override
	public void atualizar(long diftime) {
		if (gerenciador.entrada().foiPressionada(GLFW_KEY_ESCAPE)) {
			gerenciador.sobrepor(new TelaPausa(gerenciador));
			return;
		}
		if (gerenciador.entrada().foiPressionada(GLFW_KEY_V)) {
			gerenciador.trocarPara(new TelaVitoria(gerenciador));
			return;
		}
		if (gerenciador.entrada().foiPressionada(GLFW_KEY_X)) {
			gerenciador.trocarPara(new TelaDerrota(gerenciador));
			return;
		}
		if (gerenciador.entrada().foiPressionada(GLFW_KEY_T)) {
			alternarSomTeste();
		}
		camera.atualizar(gerenciador.entrada(), diftime);
		tempo += diftime / 1000.0f;

		// o ouvinte acompanha a câmera: é assim que o OpenAL sabe de que lado vem cada som
		gerenciador.audio().atualizarOuvinte(camera.x, camera.y, camera.z,
				camera.frente.x, camera.frente.y, camera.frente.z, camera.cima.x, camera.cima.y, camera.cima.z);
	}

	/** Liga ou desliga um bipe em loop num ponto fixo do mundo (teste do som 3D). */
	private void alternarSomTeste() {
		Audio audio = gerenciador.audio();
		if (audio.estaTocando(somTeste)) {
			audio.parar(somTeste);
			somTeste = null;
			return;
		}
		// um "ping" gerado por código, repetido a cada 0,7 s: não depende de arquivo
		Efeito bipe = audio.criarEfeito("bipe de teste", Sintetizador.ping(44100, 880, 120, 700), 44100);
		somTeste = audio.tocar3D(bipe, POSICAO_TESTE[0], POSICAO_TESTE[1], POSICAO_TESTE[2], 1f, true);
	}

	@Override
	public void desenhar() {
		glClearColor(COR_CEU[0], COR_CEU[1], COR_CEU[2], 1.0f);
		glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
		glEnable(GL_DEPTH_TEST);

		guardar(projecao(), bufferProjecao);
		guardar(camera.matrizVisao(), bufferVisao);

		if (mundo != null) {
			desenharTerreno();
		}

		// rótulo 2D por cima da cena 3D (o overlay devolve o estado do OpenGL no fim)
		Overlay2D overlay = Recursos.overlay();
		overlay.iniciar(gerenciador.larguraJanela(), gerenciador.alturaJanela());
		Texto.desenhar(overlay, "JOGO    ESC: pausa    T: som de teste    V: vitória    X: derrota",
				gerenciador.larguraJanela() / 2f, 14, 20, COR_ROTULO, Texto.Alinhamento.CENTRO);
		if (mundo != null) {
			desenharDepuracao(overlay);
		}
		overlay.finalizar();
	}

	/** Uma linha embaixo da legenda com o que a consulta do chão diz sobre o ponto sob a câmera. */
	private void desenharDepuracao(Overlay2D overlay) {
		ConsultaAltura chao = mundo.chao;
		String texto = String.format("x %.0f  z %.0f  |  chão %.2f m  |  inclinação %.0f°  |  %s",
				camera.x, camera.z, chao.alturaEm(camera.x, camera.z), chao.anguloEm(camera.x, camera.z),
				!chao.dentroDoMapa(camera.x, camera.z, 0) ? "fora do mapa"
						: chao.ehAgua(camera.x, camera.z)
								? String.format("água, %.2f m de fundo", chao.profundidadeAgua(camera.x, camera.z))
								: "terra firme");
		Texto.desenhar(overlay, texto, gerenciador.larguraJanela() / 2f, 42, 16, COR_DEPURACAO,
				Texto.Alinhamento.CENTRO);
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
		glUniform3f(glGetUniformLocation(prog, "corNeblina"), COR_CEU[0], COR_CEU[1], COR_CEU[2]);

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
}
