package render;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Random;

import dados.Recursos;
import terreno.RuidoFractal;
import util.Caminhos;
import util.TextureLoader;

/**
 * O clima do menu, todo em 2D com o Overlay2D:
 *  - a foto da Univali tratada para parecer noite, com uma aproximação lenta (zoom);
 *  - duas camadas de neblina (ruído fractal) deslizando em velocidades diferentes;
 *  - vinheta escura nas bordas;
 *  - granulação de filme, sorteada a cada quadro;
 *  - a lâmpada falhando (Piscadas);
 *  - o título tremendo, com um "fantasma" vermelho de vez em quando.
 *
 * As texturas são geradas uma vez só e guardadas no Recursos.
 */
public class AtmosferaMenu {

	private static final String ARQUIVO_FUNDO = "interface/menu_fundo.jpg";

	private static final Color COR_NEBLINA_1 = new Color(70, 78, 92, 60);
	private static final Color COR_NEBLINA_2 = new Color(55, 62, 76, 50);
	private static final Color COR_GRANULACAO = new Color(255, 255, 255, 14);
	private static final Color COR_TITULO = new Color(214, 204, 188);
	private static final Color COR_FANTASMA = new Color(170, 10, 10, 150);

	private final Random random = new Random();
	private final Piscadas piscadas = new Piscadas(random);
	private float tempo = 0;

	// tremor do título
	private float tremorX, tremorY, proximoTremor;
	private float fantasmaRestante; // segundos que o fantasma vermelho ainda aparece
	private float fantasmaX, fantasmaY;

	/** diftime em milissegundos. */
	public void atualizar(long diftime) {
		float dt = diftime / 1000f;
		tempo += dt;
		piscadas.atualizar(dt);

		proximoTremor -= dt;
		if (proximoTremor <= 0) {
			proximoTremor = 0.06f;
			tremorX = (random.nextFloat() - 0.5f) * 3f;
			tremorY = (random.nextFloat() - 0.5f) * 3f;
			if (fantasmaRestante <= 0 && random.nextFloat() < 0.04f) {
				fantasmaRestante = 0.08f + random.nextFloat() * 0.15f;
				fantasmaX = (random.nextFloat() - 0.5f) * 14f;
				fantasmaY = (random.nextFloat() - 0.5f) * 6f;
			}
		}
		fantasmaRestante -= dt;
	}

	/** Fundo: foto noturna cobrindo a tela (corta as bordas sem esticar) e neblina. */
	public void desenharFundo(Overlay2D overlay, int w, int h) {
		Recursos.Textura fundo = Recursos.texturaGerada("menu_fundo_noite", AtmosferaMenu::fundoNoturno, false, false);

		// "cobrir": a escala que faz a imagem ocupar a tela toda; o resto sai pelas bordas
		float escala = Math.max((float) w / fundo.largura(), (float) h / fundo.altura());
		escala *= 1.04f + 0.03f * (float) Math.sin(tempo * 0.05f); // aproximação lenta, quase imperceptível
		float lw = fundo.largura() * escala;
		float lh = fundo.altura() * escala;
		overlay.imagem(fundo.id(), (w - lw) / 2, (h - lh) / 2, lw, lh, Color.WHITE);

		// neblina: a mesma textura repetida, em duas escalas, andando para lados opostos
		Recursos.Textura neblina = Recursos.texturaGerada("menu_neblina", () -> gerarNeblina(256, 7), true, false);
		float u = tempo * 0.010f;
		overlay.imagemRecorte(neblina.id(), 0, 0, w, h, u, 0.1f, u + w / 900f, 0.1f + h / 900f, COR_NEBLINA_1);
		float u2 = -tempo * 0.006f + 0.37f;
		overlay.imagemRecorte(neblina.id(), 0, 0, w, h, u2, 0.6f, u2 + w / 1400f, 0.6f + h / 1400f, COR_NEBLINA_2);

		Recursos.Textura vinheta = Recursos.texturaGerada("menu_vinheta", () -> gerarVinheta(256), false, false);
		overlay.imagem(vinheta.id(), 0, 0, w, h, Color.WHITE);
	}

	/** Título tremendo, com o fantasma vermelho deslocado de vez em quando. */
	public void desenharTitulo(Overlay2D overlay, String texto, float cx, float cy, int tamanho) {
		Fonte fonte = Recursos.fonteTitulo();
		if (fantasmaRestante > 0) {
			Texto.desenharCentralizado(overlay, fonte, texto, cx + fantasmaX, cy + fantasmaY, tamanho,
					COR_FANTASMA, false, false);
		}
		Texto.desenharCentralizado(overlay, fonte, texto, cx + tremorX, cy + tremorY, tamanho, COR_TITULO,
				true, true);
	}

	/** Por cima de tudo: granulação e a lâmpada falhando. */
	public void desenharEfeitos(Overlay2D overlay, int w, int h) {
		desenharEfeitos(overlay, w, h, true);
	}

	/** O mesmo, podendo deixar de fora a lâmpada falhando (fica só a granulação). */
	public void desenharEfeitos(Overlay2D overlay, int w, int h, boolean luzFalhando) {
		Recursos.Textura grao = Recursos.texturaGerada("menu_granulacao", () -> gerarGranulacao(128, 3), true, true);
		float u = random.nextFloat(), v = random.nextFloat(); // posição nova a cada quadro: o grão "ferve"
		overlay.imagemRecorte(grao.id(), 0, 0, w, h, u, v, u + w / 128f, v + h / 128f, COR_GRANULACAO);

		float escuro = luzFalhando ? piscadas.escuridao() : 0;
		if (escuro > 0) {
			overlay.retangulo(0, 0, w, h, new Color(0, 0, 0, Math.round(escuro * 255)));
		}
	}

	// ---------- geração das imagens (sem OpenGL) ----------

	/** A foto da Univali tratada para noite; sem o arquivo, um degradê escuro no lugar. */
	static BufferedImage fundoNoturno() {
		BufferedImage foto = null;
		if (Caminhos.arquivo(ARQUIVO_FUNDO).exists()) {
			foto = TextureLoader.loadImage(Caminhos.caminho(ARQUIVO_FUNDO));
		}
		if (foto == null) {
			return gerarDegrade(64, 64);
		}
		return TratamentoNoturno.aplicar(foto);
	}

	/** Substituto do fundo: do quase preto (topo) a um azul muito escuro (base). */
	public static BufferedImage gerarDegrade(int largura, int altura) {
		BufferedImage img = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < altura; y++) {
			float t = (float) y / (altura - 1);
			int r = Math.round(4 + 10 * t), g = Math.round(5 + 12 * t), b = Math.round(8 + 22 * t);
			for (int x = 0; x < largura; x++) {
				img.setRGB(x, y, 0xFF000000 | (r << 16) | (g << 8) | b);
			}
		}
		return img;
	}

	/** Preto com transparência crescendo do centro para as bordas (elipse). */
	public static BufferedImage gerarVinheta(int n) {
		BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < n; y++) {
			for (int x = 0; x < n; x++) {
				float dx = (x + 0.5f) / n * 2 - 1;
				float dy = (y + 0.5f) / n * 2 - 1;
				float d = (float) Math.sqrt(dx * dx + dy * dy); // 0 no centro, ~1,41 nos cantos
				float alfa = TratamentoNoturno.suave(0.30f, 1.20f, d) * 0.96f;
				img.setRGB(x, y, Math.round(alfa * 255) << 24); // RGB preto
			}
		}
		return img;
	}

	/**
	 * Neblina que se repete sem emenda: ruído fractal (o mesmo do terreno) misturado com
	 * cópias deslocadas de si mesmo, para a borda direita combinar com a esquerda e a de
	 * baixo com a de cima.
	 */
	public static BufferedImage gerarNeblina(int n, long seed) {
		RuidoFractal ruido = new RuidoFractal(seed);
		float escala = 4f / n; // 4 "manchas" grandes por ladrilho
		BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < n; y++) {
			for (int x = 0; x < n; x++) {
				float px = (float) x / n, py = (float) y / n;
				double a = ruido.fbm(x * escala, y * escala, 5);
				double b = ruido.fbm((x - n) * escala, y * escala, 5);
				double c = ruido.fbm(x * escala, (y - n) * escala, 5);
				double d = ruido.fbm((x - n) * escala, (y - n) * escala, 5);
				double valor = a * (1 - px) * (1 - py) + b * px * (1 - py) + c * (1 - px) * py + d * px * py;
				float densidade = TratamentoNoturno.suave(-0.15f, 0.45f, (float) valor);
				int alfa = Math.round(densidade * 255);
				img.setRGB(x, y, (alfa << 24) | 0xFFFFFF); // branca; a cor vem na hora de desenhar
			}
		}
		return img;
	}

	/** Pontinhos de cinza aleatórios, opacos (a transparência vem na hora de desenhar). */
	public static BufferedImage gerarGranulacao(int n, long seed) {
		Random r = new Random(seed);
		BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < n; y++) {
			for (int x = 0; x < n; x++) {
				int v = r.nextInt(256);
				img.setRGB(x, y, 0xFF000000 | (v << 16) | (v << 8) | v);
			}
		}
		return img;
	}
}
