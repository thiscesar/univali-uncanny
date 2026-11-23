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

import dados.Recursos;
import entrada.Entrada;
import md2.Animacao;
import md2.Animador;
import md2.PersonagemMd2;
import render.Ambiente;
import render.Camera;
import render.Overlay2D;
import render.Texto;
import shaders.ShaderMd2;
import util.Utils3D;

/**
 * Bestiário (botão do menu, ou F9/M): o Dragon Knight girando, para conferir o modelo,
 * a escala e todas as animações.
 *
 * Setas esquerda e direita trocam a animação; + e - mudam a velocidade; Espaço recomeça;
 * N liga e desliga o normal map; A liga e desliga a arma; ESC volta ao menu.
 */
public class TelaVisualizadorMd2 implements Tela {

	private static final String PASTA = "modelos/dragonknight";
	private static final float ALTURA = 2.2f; // metros
	private static final Color COR_TEXTO = new Color(215, 205, 190);
	private static final Color COR_DICA = new Color(130, 125, 118);

	private final GerenciadorTelas gerenciador;
	private PersonagemMd2 personagem;
	private Animador animador;
	private List<Animacao> animacoes;
	private int indice = 0;
	private float angulo = 0;
	private boolean comNormalMap = true, comArma = true;

	// o Dragon Knight é um cavaleiro montado: 2,2 m de altura, mas quase 5 m de comprimento
	private final Camera camera = new Camera(0, 1.5f, 7.5f, 0, (float) Math.toRadians(-6));
	private final Ambiente ambiente = new Ambiente();
	private final FloatBuffer buffer = BufferUtils.createFloatBuffer(16);

	public TelaVisualizadorMd2(GerenciadorTelas gerenciador) {
		this.gerenciador = gerenciador;
	}

	@Override
	public void entrar() {
		gerenciador.definirTitulo("Bestiário");
		gerenciador.entrada().prenderCursor(false);

		PersonagemMd2.Dados dados = PersonagemMd2.ler(PASTA, ALTURA);
		if (dados != null) {
			personagem = new PersonagemMd2(dados);
			animacoes = personagem.animacoes();
			animador = new Animador(animacoes);
			tocarAtual();
		}

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

	/** Morte e "crdeath" param no último quadro; o resto fica em loop. */
	private void tocarAtual() {
		Animacao a = animacoes.get(indice);
		boolean loop = !a.nome().startsWith("death") && !a.nome().startsWith("crdeath");
		animador.tocar(a.nome(), loop);
	}

	@Override
	public void sair() {
		if (personagem != null) {
			personagem.liberar();
		}
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
		if (personagem == null) {
			Texto.desenhar(o, "Modelo não encontrado: " + PASTA + "/tris.md2", w / 2f, h / 2f, 22, COR_TEXTO,
					Texto.Alinhamento.CENTRO);
		} else {
			Animacao a = animacoes.get(indice);
			String[] linhas = {
					"Bestiário: Dragon Knight",
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
			Texto.desenhar(o, "setas: animação    + -: velocidade    Espaço: recomeçar    N: normal map    A: arma    ESC: menu",
					w / 2f, h - 34, 16, COR_DICA, Texto.Alinhamento.CENTRO);
		}
		o.finalizar();
	}
}
