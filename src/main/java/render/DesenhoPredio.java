package render;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL20.*;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.util.Random;
import java.util.function.Supplier;

import org.lwjgl.BufferUtils;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;

import dados.Recursos;
import entidades.PredioUnivali;
import shaders.ShaderObjeto;
import util.Caminhos;
import util.TextureLoader;

/**
 * Desenha o prédio da Univali com caixas (MalhaCaixas), uma malha por material, cada uma com a
 * sua textura gerada por código e o seu brilho próprio:
 *  - paredes e laje de concreto manchado (a textura se repete a cada 4 m);
 *  - marquise com duas colunas, porta de vidro e o letreiro "UNIVALI", aceso;
 *  - janelas acesas (brilham, e de longe aparecem como pontos de luz na neblina), apagadas, e
 *    uma que pisca;
 *  - a silhueta do professor numa janela acesa (sprites/professor.png, ou uma gerada);
 *  - dois postes com lâmpadas (a luz deles vem das luzes pontuais do comum.glsl).
 */
public class DesenhoPredio {

	private static final float[] COR_JANELA = { 0.30f, 0.24f, 0.13f }, COR_LAMPADA = { 0.9f, 0.75f, 0.5f };

	private final PredioUnivali predio;
	private final MalhaCaixas paredes, concreto, porta, letreiro, metal, lampadas, acesas, apagadas, piscando,
			professor;
	private final PontosBrilhantes halos;
	private final FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
	private float tempo = 0;
	/** Em que janela a silhueta do professor está montada (-1 = nenhuma). */
	private int janelaMontada = -1;

	/** Só na thread principal. */
	public DesenhoPredio(PredioUnivali p) {
		this.predio = p;
		float L = PredioUnivali.LARGURA / 2, P = PredioUnivali.PROFUNDIDADE / 2, A = PredioUnivali.ALTURA;
		float[] tudo = { 0, 0, 1, 1 };
		// a textura de concreto se repete a cada 4 m: u e t vão além de 1 (GL_REPEAT)
		paredes = new MalhaCaixas()
				.caixa(-L, L, 0, A, -P, P, new float[] { 0, 0, 2 * L / 4, A / 4 }, new float[] { 0, 0, 2 * L / 4, A / 4 },
						new float[] { 0, 0, 2 * P / 4, A / 4 })
				.enviar();
		concreto = new MalhaCaixas()
				.caixa(-L - 0.3f, L + 0.3f, A, A + 0.4f, -P - 0.3f, P + 0.3f, tudo, tudo, tudo) // laje
				.caixa(-6, 6, 3.6f, 3.9f, P, P + 4.2f, tudo, tudo, tudo)                        // marquise
				.caixa(-5.6f, -5.2f, 0, 3.6f, P + 3.6f, P + 4.0f, tudo, tudo, tudo)             // colunas
				.caixa(5.2f, 5.6f, 0, 3.6f, P + 3.6f, P + 4.0f, tudo, tudo, tudo)
				.caixa(-7, 7, 0, 0.15f, P, P + 5, tudo, tudo, tudo)                             // calçada
				.enviar();
		porta = new MalhaCaixas().caixa(-2, 2, 0, 3, P, P + 0.06f, tudo, tudo, tudo).enviar();
		letreiro = new MalhaCaixas().caixa(-4.5f, 4.5f, 3.95f, 5.15f, P + 3.9f, P + 4.05f, tudo, tudo, tudo).enviar();
		metal = new MalhaCaixas();
		lampadas = new MalhaCaixas();
		for (float lado : new float[] { -6, 6 }) {
			float z = P + 4;
			metal.caixa(lado - 0.08f, lado + 0.08f, 0, 5, z - 0.08f, z + 0.08f, tudo, tudo, tudo);
			lampadas.caixa(lado - 0.3f, lado + 0.3f, 5, 5.3f, z - 0.3f, z + 0.3f, tudo, tudo, tudo);
		}
		metal.enviar();
		lampadas.enviar();

		acesas = new MalhaCaixas();
		apagadas = new MalhaCaixas();
		piscando = new MalhaCaixas();
		halos = new PontosBrilhantes(p.janelas().size() + 4);
		for (int i = 0; i < p.janelas().size(); i++) {
			PredioUnivali.Janela j = p.janelas().get(i);
			MalhaCaixas destino = i == p.janelaPiscando() ? piscando : j.acesa() ? acesas : apagadas;
			janela(destino, j, 0.02f, PredioUnivali.JANELA_LARGURA / 2, PredioUnivali.JANELA_ALTURA / 2, 0);
		}
		acesas.enviar();
		apagadas.enviar();
		piscando.enviar();
		professor = new MalhaCaixas(); // montada a cada troca de janela (ver desenhar)
	}

	/** Uma janela: uma placa fina na fachada, deslocada "frente" metros para fora da parede. */
	private static void janela(MalhaCaixas m, PredioUnivali.Janela j, float espessura, float meiaL, float meiaA, float frente) {
		float[] tudo = { 0, 0, 1, 1 };
		float cx = j.x() + j.nx() * frente, cz = j.z() + j.nz() * frente;
		if (j.nz() != 0) {
			m.caixa(cx - meiaL, cx + meiaL, j.y() - meiaA, j.y() + meiaA, cz - espessura, cz + espessura, tudo, tudo, tudo);
		} else {
			m.caixa(cx - espessura, cx + espessura, j.y() - meiaA, j.y() + meiaA, cz - meiaL, cz + meiaL, tudo, tudo, tudo);
		}
	}

	public void atualizar(float dt) {
		tempo += dt;
	}

	public void desenhar(FloatBuffer projecao, FloatBuffer visao, Ambiente ambiente, int alturaImagem) {
		ShaderObjeto shader = Recursos.shaderObjeto();
		shader.start();
		int p = shader.programID;
		glUniformMatrix4fv(glGetUniformLocation(p, "projection"), false, projecao);
		glUniformMatrix4fv(glGetUniformLocation(p, "view"), false, visao);
		glUniform1i(glGetUniformLocation(p, "tex"), 0);
		glUniform1i(glGetUniformLocation(p, "usaNormalMap"), 0);
		ambiente.enviar(shader);
		glActiveTexture(GL_TEXTURE0);
		Matrix4f m = new Matrix4f();
		m.translate(new Vector3f(predio.x, predio.y, predio.z));
		m.rotate(predio.giro, new Vector3f(0, 1, 0));
		buffer.clear();
		m.storeTranspose(buffer);
		buffer.flip();
		glUniformMatrix4fv(glGetUniformLocation(p, "model"), false, buffer);

		desenhar(p, paredes, "predio: concreto", DesenhoPredio::concreto, 0f, true);
		desenhar(p, concreto, "predio: concreto claro", DesenhoPredio::concretoClaro, 0f, true);
		desenhar(p, porta, "predio: porta", DesenhoPredio::porta, 0.05f, false);
		desenhar(p, letreiro, "predio: letreiro", DesenhoPredio::letreiro, 0.9f, false);
		desenhar(p, metal, "predio: metal", () -> liso(new Color(40, 42, 45)), 0f, false);
		desenhar(p, lampadas, "predio: lampada", () -> liso(new Color(255, 230, 180)), 1.5f, false);
		desenhar(p, acesas, "predio: janela acesa", DesenhoPredio::janelaAcesa, 1.0f, false);
		desenhar(p, apagadas, "predio: janela apagada", DesenhoPredio::janelaApagada, 0.02f, false);
		// a que pisca: acende e apaga em rajadas irregulares (duas ondas fora de compasso)
		float pisca = (float) (Math.sin(tempo * 11.0) * Math.sin(tempo * 3.7 + 1.1));
		desenhar(p, piscando, "predio: janela acesa", DesenhoPredio::janelaAcesa, pisca > 0.1f ? 1.0f : 0.05f, false);

		int jp = predio.janelaProfessor();
		if (jp >= 0) {
			if (jp != janelaMontada) { // ele trocou de janela: a placa da silhueta é montada de novo
				professor.liberar();
				PredioUnivali.Janela j = predio.janelas().get(jp);
				janela(professor, new PredioUnivali.Janela(j.x(), j.y() - 0.15f, j.z(), j.nx(), j.nz(), true), 0.005f, 0.45f,
						0.65f, 0.04f);
				professor.enviar();
				janelaMontada = jp;
			}
			desenhar(p, professor, "predio: professor", DesenhoPredio::professor, 0f, false);
		}
		shader.stop();

		// de longe: um brilho em cada janela acesa e em cada lâmpada, que atravessa a neblina
		halos.limpar();
		for (int i = 0; i < predio.janelas().size(); i++) {
			PredioUnivali.Janela j = predio.janelas().get(i);
			if (!j.acesa() || (i == predio.janelaPiscando() && pisca <= 0.1f)) continue;
			float[] w = predio.noMundo(j.x() + j.nx() * 0.4f, j.y(), j.z() + j.nz() * 0.4f);
			halos.adicionar(w[0], w[1], w[2]);
		}
		halos.desenhar(5.0f, COR_JANELA, projecao, visao, ambiente, alturaImagem, 0.14f);
		halos.limpar();
		for (float lado : new float[] { -6, 6 }) {
			float[] w = predio.noMundo(lado, 5.0f, PredioUnivali.PROFUNDIDADE / 2 + 4);
			halos.adicionar(w[0], w[1], w[2]);
		}
		halos.desenhar(2.0f, COR_LAMPADA, projecao, visao, ambiente, alturaImagem, 0.3f);
	}

	private void desenhar(int programa, MalhaCaixas malha, String chave, Supplier<BufferedImage> textura, float emissividade,
			boolean repetir) {
		glUniform1f(glGetUniformLocation(programa, "emissividade"), emissividade);
		glBindTexture(GL_TEXTURE_2D, Recursos.texturaGerada(chave, textura, repetir, false).id());
		malha.desenhar();
	}

	// ---------- texturas (sem OpenGL) ----------

	private static Graphics2D pincel(BufferedImage img) {
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		return g;
	}

	/** Concreto pintado, com granulado, uma junta horizontal e manchas de umidade escorrendo. */
	public static BufferedImage concreto() {
		BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		Random r = new Random(41);
		for (int y = 0; y < 64; y++) {
			for (int x = 0; x < 64; x++) {
				int v = 150 + r.nextInt(18);
				img.setRGB(x, y, new Color(v, v - 6, v - 16).getRGB());
			}
		}
		Graphics2D g = pincel(img);
		g.setColor(new Color(90, 86, 80, 160));
		g.drawLine(0, 63, 63, 63); // junta entre andares
		for (int i = 0; i < 6; i++) { // escorridos de umidade
			int x = r.nextInt(64), comprimento = 12 + r.nextInt(40);
			g.setPaint(new GradientPaint(x, 0, new Color(60, 58, 50, 110), x, comprimento, new Color(60, 58, 50, 0)));
			g.fillRect(x, 0, 2 + r.nextInt(3), comprimento);
		}
		g.dispose();
		return img;
	}

	public static BufferedImage concretoClaro() {
		BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		Random r = new Random(43);
		for (int y = 0; y < 32; y++) {
			for (int x = 0; x < 32; x++) {
				int v = 175 + r.nextInt(15);
				img.setRGB(x, y, new Color(v, v - 4, v - 10).getRGB());
			}
		}
		return img;
	}

	/** Janela acesa: vidro amarelado mais claro no meio, caixilho escuro em cruz e as linhas da persiana. */
	public static BufferedImage janelaAcesa() {
		BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = pincel(img);
		g.setPaint(new GradientPaint(0, 0, new Color(255, 214, 140), 0, 32, new Color(220, 150, 70)));
		g.fillRect(0, 0, 32, 32);
		g.setColor(new Color(190, 130, 60, 120));
		for (int y = 3; y < 30; y += 3) g.drawLine(2, y, 29, y); // persiana
		g.setColor(new Color(40, 38, 36));
		g.setStroke(new BasicStroke(2));
		g.drawRect(1, 1, 30, 30);
		g.drawLine(16, 0, 16, 32);
		g.drawLine(0, 16, 32, 16);
		g.dispose();
		return img;
	}

	/** Janela apagada: vidro escuro azulado com um reflexo fraco na diagonal. */
	public static BufferedImage janelaApagada() {
		BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = pincel(img);
		g.setColor(new Color(16, 22, 32));
		g.fillRect(0, 0, 32, 32);
		g.setColor(new Color(70, 85, 105, 90));
		g.setStroke(new BasicStroke(3));
		g.drawLine(4, 28, 28, 4);
		g.setColor(new Color(40, 38, 36));
		g.setStroke(new BasicStroke(2));
		g.drawRect(1, 1, 30, 30);
		g.drawLine(16, 0, 16, 32);
		g.drawLine(0, 16, 32, 16);
		g.dispose();
		return img;
	}

	/** Porta de vidro dupla, com moldura de metal e puxadores. */
	public static BufferedImage porta() {
		BufferedImage img = new BufferedImage(64, 48, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = pincel(img);
		g.setColor(new Color(20, 28, 36));
		g.fillRect(0, 0, 64, 48);
		g.setColor(new Color(110, 112, 115));
		g.setStroke(new BasicStroke(3));
		g.drawRect(2, 2, 60, 46);
		g.drawLine(32, 2, 32, 48);
		g.fillRect(27, 22, 3, 8);
		g.fillRect(34, 22, 3, 8);
		g.dispose();
		return img;
	}

	/** O letreiro: "UNIVALI" em branco sobre o azul da universidade. */
	public static BufferedImage letreiro() {
		BufferedImage img = new BufferedImage(256, 40, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = pincel(img);
		g.setColor(new Color(0, 63, 135));
		g.fillRect(0, 0, 256, 40);
		g.setColor(Color.WHITE);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
		int w = g.getFontMetrics().stringWidth("UNIVALI");
		g.drawString("UNIVALI", (256 - w) / 2, 31);
		g.dispose();
		return img;
	}

	/** A silhueta do professor (sprites/professor.png, ou uma figura escura de cabeça e ombros). */
	public static BufferedImage professor() {
		if (Caminhos.existe("sprites/professor.png")) {
			BufferedImage img = TextureLoader.loadImage(Caminhos.caminho("sprites/professor.png"));
			if (img != null) return img;
		}
		BufferedImage img = new BufferedImage(48, 72, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = pincel(img);
		g.setColor(new Color(12, 10, 10));
		g.fillOval(15, 6, 18, 22);                          // cabeça
		g.fillRoundRect(4, 30, 40, 46, 18, 18);             // ombros e tronco
		g.fillRect(20, 24, 8, 8);                           // pescoço
		g.dispose();
		return img;
	}

	private static BufferedImage liso(Color c) {
		BufferedImage img = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) img.setRGB(x, y, c.getRGB());
		return img;
	}

	public void liberar() {
		for (MalhaCaixas m : new MalhaCaixas[] { paredes, concreto, porta, letreiro, metal, lampadas, acesas, apagadas,
				piscando, professor }) {
			m.liberar();
		}
		halos.liberar();
	}
}
