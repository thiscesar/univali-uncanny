package render;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Confete da tela de APROVADO: papeizinhos coloridos e folhas de prova (brancas, com linhas de
 * texto e a nota em vermelho) que giram, balançam e caem.
 *
 * As posições e velocidades são medidas em alturas de tela (y = 0 no topo, 1 na base; x vai de
 * 0 até a proporção largura/altura), para o efeito ficar igual em qualquer tamanho de janela.
 * Cada peça é freada pelo ar (arrasto): a folha de prova, mais leve para o tamanho, cai devagar.
 */
public class Confete {

	/** Aceleração da gravidade, em alturas de tela por segundo². */
	static final float GRAVIDADE = 1.1f;
	/** Freio do ar, por segundo: a velocidade máxima de queda é GRAVIDADE / ARRASTO. */
	static final float ARRASTO_PAPELZINHO = 2.4f, ARRASTO_PROVA = 3.6f;
	/** Fração das peças que são folhas de prova. */
	private static final float CHANCE_PROVA = 0.18f;

	private static final Color[] CORES = {
			new Color(240, 200, 90), // dourado
			new Color(196, 32, 42),  // vermelho
			new Color(232, 224, 204), // branco-osso
			new Color(96, 176, 96),  // verde
			new Color(70, 120, 210), // azul
	};
	private static final Color COR_FOLHA = new Color(238, 233, 220);
	private static final Color COR_LINHA = new Color(120, 120, 130);
	private static final Color COR_NOTA = new Color(200, 20, 30);

	/** Uma peça de confete. */
	static final class Peca {
		float x, y, vx, vy;
		float angulo, giro;      // radianos e radianos por segundo
		float largura, altura;   // em alturas de tela
		float fase, virada;      // para o balanço e para o "virar" (a largura encolhe e volta)
		boolean prova;
		Color cor;
	}

	private final List<Peca> pecas = new ArrayList<>();
	private final Random random;
	private float tempo, chuvaPorSegundo, acumuladoChuva;

	public Confete(Random random) {
		this.random = random;
	}

	/**
	 * Um estouro de peças saindo de (x, y), na direção dada (radianos; 0 = direita,
	 * -PI/2 = para cima), espalhadas por "abertura" radianos para cada lado.
	 */
	public void explodir(float x, float y, int quantidade, float direcao, float abertura, float velocidade) {
		for (int i = 0; i < quantidade; i++) {
			float a = direcao + (random.nextFloat() * 2 - 1) * abertura;
			float v = velocidade * (0.55f + 0.45f * random.nextFloat());
			pecas.add(nova(x, y, (float) Math.cos(a) * v, (float) Math.sin(a) * v));
		}
	}

	/** Peças caindo do topo sem parar, "porSegundo" por segundo (0 para parar). */
	public void chover(float porSegundo) {
		chuvaPorSegundo = porSegundo;
	}

	/** dt em segundos; proporcao = largura / altura da janela (onde a chuva pode nascer). */
	public void atualizar(float dt, float proporcao) {
		tempo += dt;
		acumuladoChuva += chuvaPorSegundo * dt;
		while (acumuladoChuva >= 1) {
			acumuladoChuva -= 1;
			pecas.add(nova(random.nextFloat() * proporcao, -0.05f, (random.nextFloat() - 0.5f) * 0.1f, 0.1f));
		}
		for (int i = pecas.size() - 1; i >= 0; i--) {
			Peca p = pecas.get(i);
			float freio = (float) Math.exp(-(p.prova ? ARRASTO_PROVA : ARRASTO_PAPELZINHO) * dt);
			p.vy = (p.vy + GRAVIDADE * dt) * freio;
			p.vx *= freio;
			// o balanço de um lado para o outro, como papel caindo
			p.x += (p.vx + 0.08f * (float) Math.sin(tempo * 2.3f + p.fase)) * dt;
			p.y += p.vy * dt;
			p.angulo += p.giro * dt;
			if (p.y > 1.1f) {
				pecas.remove(i);
			}
		}
	}

	public int quantidade() {
		return pecas.size();
	}

	List<Peca> pecas() {
		return pecas;
	}

	public void desenhar(Overlay2D o, int w, int h) {
		for (Peca p : pecas) {
			float cx = p.x * h, cy = p.y * h;
			// virando no ar: a largura encolhe e volta, como se a peça girasse em 3D
			float vira = Math.abs((float) Math.cos(tempo * p.virada + p.fase));
			float lw = p.largura * h * Math.max(0.15f, vira), lh = p.altura * h;
			if (!p.prova) {
				o.retanguloGirado(cx, cy, lw, lh, p.angulo, p.cor);
				continue;
			}
			o.retanguloGirado(cx, cy, lw, lh, p.angulo, COR_FOLHA);
			if (vira < 0.35f) continue; // de lado, não dá para ver o que está escrito
			// três linhas de texto e a nota em vermelho, no sistema de coordenadas da folha
			for (int l = 0; l < 3; l++) {
				detalhe(o, cx, cy, p.angulo, 0, (-0.12f + l * 0.16f) * lh, lw * 0.7f, Math.max(1, lh * 0.05f), COR_LINHA);
			}
			detalhe(o, cx, cy, p.angulo, lw * 0.18f, -0.32f * lh, lw * 0.28f, lh * 0.16f, COR_NOTA);
		}
	}

	/** Um retângulo deslocado (ox, oy) do centro da folha, girando junto com ela. */
	private static void detalhe(Overlay2D o, float cx, float cy, float angulo, float ox, float oy, float largura,
			float altura, Color cor) {
		float c = (float) Math.cos(angulo), s = (float) Math.sin(angulo);
		o.retanguloGirado(cx + ox * c - oy * s, cy + ox * s + oy * c, largura, altura, angulo, cor);
	}

	private Peca nova(float x, float y, float vx, float vy) {
		Peca p = new Peca();
		p.x = x;
		p.y = y;
		p.vx = vx;
		p.vy = vy;
		p.prova = random.nextFloat() < CHANCE_PROVA;
		if (p.prova) {
			p.largura = 0.028f;
			p.altura = 0.036f;
		} else {
			p.largura = 0.010f + random.nextFloat() * 0.008f;
			p.altura = 0.006f + random.nextFloat() * 0.006f;
			p.cor = CORES[random.nextInt(CORES.length)];
		}
		p.angulo = random.nextFloat() * (float) Math.PI * 2;
		p.giro = (random.nextFloat() - 0.5f) * 8;
		p.fase = random.nextFloat() * (float) Math.PI * 2;
		p.virada = 2 + random.nextFloat() * 5;
		return p;
	}
}
