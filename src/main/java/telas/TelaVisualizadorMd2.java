package telas;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.glGetUniformLocation;
import static org.lwjgl.opengl.GL20.glUniform1f;
import static org.lwjgl.opengl.GL20.glUniformMatrix4fv;

import java.awt.Color;
import java.nio.FloatBuffer;
import java.util.List;

import org.lwjgl.BufferUtils;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;

import dados.Opcoes;
import dados.Recursos;
import entrada.Entrada;
import md2.Animacao;
import md2.Animador;
import md2.PersonagemMd2;
import render.Ambiente;
import render.Camera;
import render.FiltroPs1;
import render.Overlay2D;
import render.Texto;
import shaders.ShaderMd2;
import util.Utils3D;

/**
 * Bestiário (botão do menu, ou F9/M): os monstros do jogo girando, com uma ficha do que cada um
 * faz, para conferir o modelo, a escala e todas as animações.
 *
 * Tab (ou setas para cima e para baixo) troca o monstro; setas esquerda e direita trocam a
 * animação; + e - mudam a velocidade; Espaço recomeça; N liga e desliga o normal map; A liga e
 * desliga a arma; ESC volta ao menu.
 */
public class TelaVisualizadorMd2 implements Tela {

	/** Um monstro do bestiário: o modelo, a altura no jogo, de onde a câmera olha e a ficha dele. */
	private record Ficha(String nome, String pasta, float altura, Camera camera, String[] descricao) {}

	private static final Ficha[] FICHAS = {
			new Ficha("Jubilado", TelaJogo.PASTA_JUBILADO, TelaJogo.ALTURA_JUBILADO,
					new Camera(0, 1.25f, 5.4f, 0, (float) Math.toRadians(-8)), new String[] {
							"O aluno que estourou o prazo do curso e nunca saiu da floresta. São quatro.",
							"Vê a 16 m (30 m se a lanterna estiver ligada); ouve quem corre a 28 m e um tiro a 70 m.",
							"Caçando, dá botes mais rápidos que você correndo e fareja até te achar.",
							"Quem só ronda recua se iluminado, mas, provocado, ataca. A escopeta só o derruba." }),
			// um cavaleiro montado: 2,2 m de altura, mas quase 5 m de comprimento
			new Ficha("Dragon Knight", TelaJogo.PASTA_MONSTRO, TelaJogo.ALTURA_MONSTRO,
					new Camera(0, 1.5f, 7.5f, 0, (float) Math.toRadians(-6)), new String[] {
							"O chefão da volta: dorme na Univali até você entrar com as três provas.",
							"Avança em zigue-zague pelos corredores e pula para o lado quando você mira nele.",
							"Só tiros bons contam (três bagos no corpo): três deles o derrubam de vez." }),
	};
	private static final Color COR_TEXTO = new Color(215, 205, 190);
	private static final Color COR_DICA = new Color(130, 125, 118);

	private final GerenciadorTelas gerenciador;
	/** Os modelos de cada ficha (null se o arquivo não está na pasta). */
	private final PersonagemMd2[] personagens = new PersonagemMd2[FICHAS.length];
	private int ficha = 0;
	private PersonagemMd2 personagem;
	private Animador animador;
	private List<Animacao> animacoes;
	private int indice = 0;
	private float angulo = 0;
	private boolean comNormalMap = true, comArma = true;

	private Camera camera = FICHAS[0].camera();
	private final Ambiente ambiente = new Ambiente();
	private final FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
	private final FiltroPs1 filtroPs1 = new FiltroPs1();
	private boolean ps1;

	public TelaVisualizadorMd2(GerenciadorTelas gerenciador) {
		this.gerenciador = gerenciador;
	}

	@Override
	public void entrar() {
		gerenciador.definirTitulo("Bestiário");
		gerenciador.entrada().prenderCursor(false);

		for (int i = 0; i < FICHAS.length; i++) {
			PersonagemMd2.Dados dados = PersonagemMd2.ler(FICHAS[i].pasta(), FICHAS[i].altura());
			if (dados != null) personagens[i] = new PersonagemMd2(dados);
		}
		ps1 = Opcoes.atual().modoPs1;
		Recursos.aplicarModoPs1(ps1);
		mostrar(0);

		// luz de estúdio: lua mais forte e uma "lanterna" larga vinda de cima e da direita
		ambiente.corCeu[0] = 0.06f; ambiente.corCeu[1] = 0.065f; ambiente.corCeu[2] = 0.075f;
		ambiente.densidadeNeblina = 0.01f;
		ambiente.intensidadeAmbiente = 1.4f;
		ambiente.intensidadeLanterna = 1.3f;
		ambiente.posLanterna[0] = 1.2f; ambiente.posLanterna[1] = 1.0f; ambiente.posLanterna[2] = 0f;
		float[] d = { -1.2f, -0.6f, -4f };
		float t = (float) Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
		ambiente.dirLanterna[0] = d[0] / t; ambiente.dirLanterna[1] = d[1] / t; ambiente.dirLanterna[2] = d[2] / t;
		ambiente.anguloInterno = 25;
		ambiente.anguloExterno = 45;
		ambiente.alcanceLanterna = 15;
	}

	/** Mostra o monstro da ficha i, começando pela primeira animação. */
	private void mostrar(int i) {
		ficha = i;
		camera = FICHAS[i].camera();
		personagem = personagens[i];
		indice = 0;
		if (personagem == null) {
			animador = null;
			return;
		}
		animacoes = personagem.animacoes();
		animador = new Animador(animacoes);
		tocarAtual();
	}

	/** Morte e "crdeath" param no último quadro; o resto fica em loop. */
	private void tocarAtual() {
		Animacao a = animacoes.get(indice);
		boolean loop = !a.nome().startsWith("death") && !a.nome().startsWith("crdeath");
		animador.tocar(a.nome(), loop);
	}

	@Override
	public void sair() {
		for (PersonagemMd2 p : personagens) {
			if (p != null) p.liberar();
		}
		filtroPs1.liberar();
	}

	@Override
	public void atualizar(long diftime) {
		Entrada e = gerenciador.entrada();
		if (e.foiPressionada(GLFW_KEY_ESCAPE)) {
			gerenciador.trocarPara(new TelaMenu(gerenciador));
			return;
		}
		float dt = diftime / 1000f;
		angulo += dt * 0.6f;
		if (e.foiPressionada(GLFW_KEY_TAB) || e.foiPressionada(GLFW_KEY_DOWN)) mostrar((ficha + 1) % FICHAS.length);
		if (e.foiPressionada(GLFW_KEY_UP)) mostrar((ficha - 1 + FICHAS.length) % FICHAS.length);
		if (animador == null) {
			return;
		}
		if (e.foiPressionada(GLFW_KEY_RIGHT)) { indice = (indice + 1) % animacoes.size(); tocarAtual(); }
		if (e.foiPressionada(GLFW_KEY_LEFT)) { indice = (indice - 1 + animacoes.size()) % animacoes.size(); tocarAtual(); }
		if (e.foiPressionada(GLFW_KEY_SPACE)) tocarAtual();
		if (e.foiPressionada(GLFW_KEY_EQUAL) || e.foiPressionada(GLFW_KEY_KP_ADD)) animador.definirVelocidade(animador.velocidade() * 1.25f);
		if (e.foiPressionada(GLFW_KEY_MINUS) || e.foiPressionada(GLFW_KEY_KP_SUBTRACT)) animador.definirVelocidade(animador.velocidade() / 1.25f);
		if (e.foiPressionada(GLFW_KEY_N)) comNormalMap = !comNormalMap;
		if (e.foiPressionada(GLFW_KEY_A)) comArma = !comArma;
		animador.atualizar(dt);
	}

	@Override
	public void desenhar() {
		if (ps1) {
			filtroPs1.comecar(gerenciador.largura(), gerenciador.altura());
			ambiente.resolucaoPs1[0] = filtroPs1.largura();
			ambiente.resolucaoPs1[1] = filtroPs1.altura();
		}
		ambiente.modoPs1 = ps1 ? 1 : 0;
		glClearColor(ambiente.corCeu[0], ambiente.corCeu[1], ambiente.corCeu[2], 1);
		glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
		glEnable(GL_DEPTH_TEST);

		if (personagem != null) {
			ShaderMd2 shader = Recursos.shaderMd2();
			shader.start();
			int p = shader.programID;
			float topo = 0.1f * (float) Math.tan(Math.toRadians(20));
			matriz(p, "projection", Utils3D.setFrustum(-topo * gerenciador.proporcao(), topo * gerenciador.proporcao(),
					-topo, topo, 0.1f, 50f));
			matriz(p, "view", camera.matrizVisao());
			Matrix4f model = new Matrix4f();
			model.rotate(angulo, new Vector3f(0, 1, 0));
			matriz(p, "model", model);
			glUniform1f(glGetUniformLocation(p, "emissividade"), 0f);
			glUniform1f(glGetUniformLocation(p, "dano"), 0f);
			ambiente.enviar(shader);
			personagem.desenhar(shader, animador.quadroA(), animador.quadroB(), animador.fator(), comArma, comNormalMap);
			shader.stop();
		}
		if (ps1) {
			filtroPs1.terminar(gerenciador.largura(), gerenciador.altura());
		}
		desenharTexto();
	}

	private void matriz(int programa, String nome, Matrix4f m) {
		buffer.clear();
		m.storeTranspose(buffer);
		buffer.flip();
		glUniformMatrix4fv(glGetUniformLocation(programa, nome), false, buffer);
	}

	private void desenharTexto() {
		int w = gerenciador.larguraJanela(), h = gerenciador.alturaJanela();
		Overlay2D o = Recursos.overlay();
		o.iniciar(w, h);
		Ficha f = FICHAS[ficha];
		if (personagem == null) {
			Texto.desenhar(o, "Bestiário: " + f.nome() + "   (Tab: próximo)", 20, 16, 22, COR_TEXTO, Texto.Alinhamento.ESQUERDA);
			Texto.desenhar(o, "Modelo não encontrado: " + f.pasta() + "/tris.md2", w / 2f, h / 2f, 22, COR_TEXTO,
					Texto.Alinhamento.CENTRO);
		} else {
			Animacao a = animacoes.get(indice);
			String[] linhas = {
					String.format("Bestiário: %s   (%d de %d)", f.nome(), ficha + 1, FICHAS.length),
					String.format("animação %s   (%d de %d, quadros %d a %d)%s", a.nome(), indice + 1, animacoes.size(),
							a.primeiro(), a.ultimo(), animador.terminou() ? "   fim" : ""),
					String.format("quadro %d -> %d   fator %.2f", animador.quadroA(), animador.quadroB(), animador.fator()),
					String.format("velocidade %.2fx (%.0f quadros por segundo)", animador.velocidade(), a.fps() * animador.velocidade()),
					"normal map " + (comNormalMap ? "ligado" : "desligado") + "   arma "
							+ (personagem.temArma() ? (comArma ? "ligada" : "desligada") : "não existe"),
			};
			for (int i = 0; i < linhas.length; i++) {
				Texto.desenhar(o, linhas[i], 20, 16 + i * 26, i == 0 ? 22 : 18, COR_TEXTO, Texto.Alinhamento.ESQUERDA);
			}
			// a ficha do monstro, embaixo
			for (int i = 0; i < f.descricao().length; i++) {
				float y = h - 70 - (f.descricao().length - i) * 26;
				Texto.desenhar(o, f.descricao()[i], w / 2f, y, 18, COR_TEXTO, Texto.Alinhamento.CENTRO);
			}
			Texto.desenhar(o, "Tab: outro monstro    setas: animação    + -: velocidade    Espaço: recomeçar    N: normal map    A: arma    ESC: menu",
					w / 2f, h - 34, 16, COR_DICA, Texto.Alinhamento.CENTRO);
		}
		o.finalizar();
	}
}
