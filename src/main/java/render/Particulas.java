package render;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL20.GL_POINT_SPRITE;
import static org.lwjgl.opengl.GL32.GL_PROGRAM_POINT_SIZE;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import org.lwjgl.BufferUtils;

import dados.Recursos;
import shaders.ShaderParticula;

/**
 * Partículas simples, no mundo: faíscas e poeira onde o bago bate, sangue escuro no monstro,
 * fumaça subindo do cano. Cada partícula tem posição, velocidade, gravidade, arrasto do ar,
 * tempo de vida, tamanho (que pode crescer) e cor (que some no fim da vida).
 *
 * A física é feita aqui, na CPU (dá para testar sem janela). O desenho manda tudo de uma vez
 * para um VBO e desenha como pontos (GL_POINTS), em duas levas: primeiro as que cobrem a cena
 * pela transparência (poeira, fumaça, sangue), depois as que somam luz (faíscas).
 */
public class Particulas {

	/** Uma partícula. A cor final é a cor vezes o quanto de vida ainda resta. */
	static final class Particula {
		float x, y, z, vx, vy, vz;
		float vida, duracao, tamanho, crescimento, gravidade, arrasto;
		float r, g, b, a;
		boolean aditiva;
	}

	/** Mais que isso ao mesmo tempo, as mais antigas são descartadas. */
	public static final int MAXIMO = 600;
	private static final int FLOATS = 8; // posição 3, cor 4, tamanho 1

	private final List<Particula> ativas = new ArrayList<>();
	private final Random random = new Random();
	private final FloatBuffer buffer = BufferUtils.createFloatBuffer(MAXIMO * FLOATS);
	private int vbo = 0;

	// ---------- emissores ----------

	/** Faíscas: pontos laranja que voam rápido, caem e somem em 0,2–0,4 s. */
	public void faiscas(float x, float y, float z, int quantidade) {
		for (int i = 0; i < quantidade; i++) {
			Particula p = nova(x, y, z, 0.2f + random.nextFloat() * 0.2f);
			espalhar(p, 3f + random.nextFloat() * 3f);
			p.tamanho = 0.05f;
			p.gravidade = 9f;
			p.arrasto = 2f;
			cor(p, 1.6f, 0.8f, 0.25f, 1);
			p.aditiva = true;
		}
	}

	/** Poeira ou lascas: nuvenzinha marrom que se espalha devagar e cresce. */
	public void poeira(float x, float y, float z, int quantidade) {
		for (int i = 0; i < quantidade; i++) {
			Particula p = nova(x, y, z, 0.5f + random.nextFloat() * 0.5f);
			espalhar(p, 0.6f + random.nextFloat() * 0.8f);
			p.vy += 0.4f;
			p.tamanho = 0.08f;
			p.crescimento = 0.35f;
			p.gravidade = 0.5f;
			p.arrasto = 3f;
			cor(p, 0.16f, 0.13f, 0.10f, 0.6f);
		}
	}

	/** Sangue escuro: gotas que espirram e caem. */
	public void sangue(float x, float y, float z, int quantidade) {
		for (int i = 0; i < quantidade; i++) {
			Particula p = nova(x, y, z, 0.4f + random.nextFloat() * 0.4f);
			espalhar(p, 1.5f + random.nextFloat() * 2.5f);
			p.tamanho = 0.06f + random.nextFloat() * 0.06f;
			p.gravidade = 9f;
			p.arrasto = 1f;
			cor(p, 0.22f, 0.01f, 0.01f, 0.95f);
		}
	}

	/**
	 * Um jorro de sangue (o golpe no Dragon Knight): gotas grandes e vermelho-vivo, lançadas na
	 * direção (dx, dz) do tiro (saindo pelas costas) e para cima, caindo em arco. Duram mais que o
	 * sangue de um bago, para dar tempo de ver.
	 */
	public void jorroDeSangue(float x, float y, float z, float dx, float dz, int quantidade) {
		float t = Math.max(1e-4f, (float) Math.hypot(dx, dz));
		dx /= t;
		dz /= t;
		for (int i = 0; i < quantidade; i++) {
			Particula p = nova(x, y, z, 0.9f + random.nextFloat() * 0.6f);
			espalhar(p, 1.2f + random.nextFloat() * 1.5f);
			float forca = 2.5f + random.nextFloat() * 4f;
			p.vx += dx * forca;
			p.vz += dz * forca;
			p.vy += 1.5f + random.nextFloat() * 2.5f;
			p.tamanho = 0.08f + random.nextFloat() * 0.1f;
			p.gravidade = 9f;
			p.arrasto = 0.6f;
			cor(p, 0.55f + random.nextFloat() * 0.15f, 0.02f, 0.02f, 1f);
		}
	}

	/** Fumaça do cano: sobe devagar, cresce e some em ~1 s. */
	public void fumaca(float x, float y, float z, int quantidade) {
		for (int i = 0; i < quantidade; i++) {
			Particula p = nova(x, y, z, 0.8f + random.nextFloat() * 0.5f);
			espalhar(p, 0.15f);
			p.vy += 0.35f + random.nextFloat() * 0.2f;
			p.tamanho = 0.04f;
			p.crescimento = 0.18f;
			p.gravidade = -0.1f; // sobe um pouco (ar quente)
			p.arrasto = 1.5f;
			cor(p, 0.22f, 0.22f, 0.24f, 0.25f);
		}
	}

	/**
	 * A chama na boca do cano: três bolas de luz amarela-alaranjada, uma atrás da outra na
	 * direção do tiro, que duram 1 a 3 quadros. Usada no espaço da arma (ponto e direção nele).
	 */
	public void chama(float[] ponto, float[] direcao) {
		float[] tamanhos = { 0.16f, 0.12f, 0.08f };
		for (int i = 0; i < tamanhos.length; i++) {
			float avanco = 0.03f + i * 0.06f + random.nextFloat() * 0.02f;
			Particula p = nova(ponto[0] + direcao[0] * avanco, ponto[1] + direcao[1] * avanco,
					ponto[2] + direcao[2] * avanco, 0.04f + random.nextFloat() * 0.03f);
			p.tamanho = tamanhos[i] * (0.8f + random.nextFloat() * 0.5f);
			cor(p, 2.2f, 1.3f - i * 0.2f, 0.45f, 1);
			p.aditiva = true;
		}
	}

	private Particula nova(float x, float y, float z, float duracao) {
		if (ativas.size() >= MAXIMO) {
			ativas.remove(0); // a mais antiga
		}
		Particula p = new Particula();
		p.x = x;
		p.y = y;
		p.z = z;
		p.duracao = duracao;
		ativas.add(p);
		return p;
	}

	/** Velocidade numa direção sorteada (um ponto sorteado dentro de uma esfera). */
	private void espalhar(Particula p, float velocidade) {
		float dx, dy, dz, t;
		do {
			dx = random.nextFloat() * 2 - 1;
			dy = random.nextFloat() * 2 - 1;
			dz = random.nextFloat() * 2 - 1;
			t = dx * dx + dy * dy + dz * dz;
		} while (t > 1 || t < 1e-4f);
		t = (float) Math.sqrt(t);
		p.vx = dx / t * velocidade;
		p.vy = dy / t * velocidade;
		p.vz = dz / t * velocidade;
	}

	private static void cor(Particula p, float r, float g, float b, float a) {
		p.r = r;
		p.g = g;
		p.b = b;
		p.a = a;
	}

	// ---------- simulação ----------

	/** dt em segundos: move, aplica gravidade e arrasto, envelhece e remove as que acabaram. */
	public void atualizar(float dt) {
		Iterator<Particula> it = ativas.iterator();
		while (it.hasNext()) {
			Particula p = it.next();
			p.vida += dt;
			if (p.vida >= p.duracao) {
				it.remove();
				continue;
			}
			float freio = (float) Math.exp(-p.arrasto * dt); // o ar freia aos poucos
			p.vx *= freio;
			p.vz *= freio;
			p.vy = p.vy * freio - p.gravidade * dt;
			p.x += p.vx * dt;
			p.y += p.vy * dt;
			p.z += p.vz * dt;
			p.tamanho += p.crescimento * dt;
		}
	}

	public int quantidade() {
		return ativas.size();
	}

	// ---------- desenho ----------

	/** Desenha todas as partículas com as matrizes da cena. Só na thread principal. */
	public void desenhar(FloatBuffer projecao, FloatBuffer visao, Ambiente ambiente, int alturaImagem) {
		if (ativas.isEmpty()) return;
		if (vbo == 0) {
			vbo = glGenBuffers();
			glBindBuffer(GL_ARRAY_BUFFER, vbo);
			glBufferData(GL_ARRAY_BUFFER, (long) MAXIMO * FLOATS * 4, GL_STREAM_DRAW);
		}
		ShaderParticula shader = Recursos.shaderParticula();
		shader.start();
		int prog = shader.programID;
		glUniformMatrix4fv(glGetUniformLocation(prog, "projection"), false, projecao);
		glUniformMatrix4fv(glGetUniformLocation(prog, "view"), false, visao);
		glUniform1f(glGetUniformLocation(prog, "alturaTela"), alturaImagem);
		ambiente.enviar(shader);

		boolean tinhaBlend = glIsEnabled(GL_BLEND);
		glEnable(GL_BLEND);
		glDepthMask(false); // testam a profundidade (somem atrás das árvores), mas não escrevem
		glEnable(GL_PROGRAM_POINT_SIZE);
		glEnable(GL_POINT_SPRITE); // contexto de compatibilidade: sem isto não há gl_PointCoord
		glBindBuffer(GL_ARRAY_BUFFER, vbo);
		int bytes = FLOATS * 4;
		glEnableVertexAttribArray(0);
		glVertexAttribPointer(0, 3, GL_FLOAT, false, bytes, 0);
		glEnableVertexAttribArray(1);
		glVertexAttribPointer(1, 4, GL_FLOAT, false, bytes, 12);
		glEnableVertexAttribArray(4);
		glVertexAttribPointer(4, 1, GL_FLOAT, false, bytes, 28);

		for (boolean aditivas : new boolean[] { false, true }) {
			int n = preencher(aditivas);
			if (n == 0) continue;
			glBufferSubData(GL_ARRAY_BUFFER, 0, buffer);
			glUniform1f(glGetUniformLocation(prog, "aditiva"), aditivas ? 1 : 0);
			if (aditivas) {
				glBlendFunc(GL_ONE, GL_ONE);
			} else {
				glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
			}
			glDrawArrays(GL_POINTS, 0, n);
		}

		glDisableVertexAttribArray(1);
		glDisableVertexAttribArray(4);
		glBindBuffer(GL_ARRAY_BUFFER, 0);
		glDisable(GL_POINT_SPRITE);
		glDisable(GL_PROGRAM_POINT_SIZE);
		glDepthMask(true);
		glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
		if (!tinhaBlend) glDisable(GL_BLEND);
		shader.stop();
	}

	/** Copia para o buffer as partículas de um tipo, com a cor sumindo no fim da vida. */
	private int preencher(boolean aditivas) {
		buffer.clear();
		int n = 0;
		for (Particula p : ativas) {
			if (p.aditiva != aditivas) continue;
			float resta = 1 - p.vida / p.duracao;
			buffer.put(p.x).put(p.y).put(p.z).put(p.r).put(p.g).put(p.b).put(p.a * resta).put(p.tamanho);
			n++;
		}
		buffer.flip();
		return n;
	}

	public void liberar() {
		if (vbo != 0) glDeleteBuffers(vbo);
		vbo = 0;
	}
}
