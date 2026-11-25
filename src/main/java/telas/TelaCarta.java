package telas;

import static org.lwjgl.glfw.GLFW.*;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import audio.Sintetizador;
import dados.Recursos;
import entrada.Entrada;
import render.Overlay2D;
import render.Texto;
import util.Caminhos;

/**
 * A carta do aluno que tentou fugir antes, lida como os arquivos de Resident Evil: a partida
 * congela (a tela fica POR CIMA dela, como a pausa) e a folha de caderno aparece no meio da tela,
 * escrita à caneta, com a letra de quem estava com medo. E, ESC, Enter, Espaço ou um clique fecham.
 *
 * Ela aparece ao pegar a escopeta no balcão do saguão e pode ser relida pela pausa ("Carta").
 */
public class TelaCarta implements Tela {

	/**
	 * O texto da carta. Linha vazia = pula uma pauta; as linhas longas quebram sozinhas; ~~assim~~
	 * sai riscado (escreveu e se arrependeu).
	 */
	public static final String[] TEXTO = {
			"23:58. De novo.",
			"",
			"Se você está lendo isso, também dormiu na carteira.",
			"",
			"As provas estão na floresta. Deixei a escopeta aqui; o resto da munição está perto delas.",
			"",
			"Lá fora estão os jubilados. A luz atrai eles, e eles ouvem quem corre. A escopeta só derruba.",
			"",
			"Na volta, cuidado: tem uma coisa a cavalo aqui dentro.",
			"",
			"~~Eu vou~~ Eu não consegui.",
			"",
			"— R." };

	private static final Color VEU = new Color(0, 0, 0, 200);
	private static final Color COR_DICA = new Color(150, 145, 140);
	/** Quanto da altura da tela a folha ocupa, e o tamanho da imagem dela, em pixels. */
	private static final float ALTURA = 0.86f;
	private static final int LARGURA_FOLHA = 760, ALTURA_FOLHA = 1040;
	/** As pautas: a primeira, a distância entre elas, a margem e o tamanho da letra (pixels da imagem). */
	private static final int TOPO = 150, ENTRE_LINHAS = 46, MARGEM = 108;
	private static final float TAMANHO_LETRA = 44f;
	private static final String ARQUIVO_CANETA = "fontes/manuscrita.ttf";
	/** Tempo para a folha aparecer, e antes de aceitar fechar (para o E que a abriu não a fechar). */
	private static final float APARECER = 0.25f, TRAVA = 0.3f;

	private final GerenciadorTelas gerenciador;
	/** O que fazer ao fechar (relida pela pausa: volta para a pausa); null = volta para a partida. */
	private final Runnable depois;
	private float tempo = 0;

	public TelaCarta(GerenciadorTelas gerenciador, Runnable depois) {
		this.gerenciador = gerenciador;
		this.depois = depois;
	}

	@Override
	public void entrar() {
		gerenciador.definirTitulo("Carta");
		gerenciador.entrada().prenderCursor(false);
		gerenciador.audio().pausarTudo();
		folha();
	}

	@Override
	public void sair() {
		if (depois == null) {
			gerenciador.audio().retomarTudo();
			gerenciador.entrada().prenderCursor(true);
			gerenciador.definirTitulo(TelaJogo.TITULO);
		}
	}

	/** O som de papel ao abrir e ao fechar. */
	private void folha() {
		var audio = gerenciador.audio();
		audio.tocar(audio.criarEfeito("folha de papel", Sintetizador.folhaPapel(44100, 7), 44100), 0.8f);
	}

	@Override
	public void atualizar(long diftime) {
		tempo += diftime / 1000f;
		Entrada e = gerenciador.entrada();
		boolean fechar = e.foiPressionada(GLFW_KEY_E) || e.foiPressionada(GLFW_KEY_ESCAPE) || e.foiPressionada(GLFW_KEY_ENTER)
				|| e.foiPressionada(GLFW_KEY_SPACE) || e.botaoFoiPressionado(GLFW_MOUSE_BUTTON_LEFT);
		if (tempo > TRAVA && fechar) {
			folha();
			gerenciador.fecharSobreposicao();
			if (depois != null) depois.run();
		}
	}

	@Override
	public void desenhar() {
		int w = gerenciador.larguraJanela(), h = gerenciador.alturaJanela();
		float alfa = Math.min(1, tempo / APARECER);
		Overlay2D o = Recursos.overlay();
		o.iniciar(w, h);
		o.retangulo(0, 0, w, h, new Color(0, 0, 0, Math.round(VEU.getAlpha() * alfa)));
		float fh = h * ALTURA, fw = fh * LARGURA_FOLHA / ALTURA_FOLHA;
		if (fw > w * 0.9f) {
			fw = w * 0.9f;
			fh = fw * ALTURA_FOLHA / LARGURA_FOLHA;
		}
		float fx = (w - fw) / 2, fy = (h - fh) / 2 + (1 - alfa) * h * 0.03f; // sobe um pouco ao aparecer
		int folha = Recursos.texturaGerada("carta: folha escrita", () -> carta(caneta()), false, false).id();
		o.imagem(folha, fx, fy, fw, fh, new Color(1f, 1f, 1f, alfa));
		if (tempo > TRAVA) {
			Texto.desenhar(o, "E ou ESC para fechar", w / 2f, fy + fh + h * 0.012f, Math.max(13, Math.round(h * 0.02f)),
					COR_DICA, Texto.Alinhamento.CENTRO);
		}
		o.finalizar();
	}

	/** A fonte de caneta (fontes/manuscrita.ttf); sem o arquivo, uma itálica do sistema. */
	static Font caneta() {
		if (Caminhos.existe(ARQUIVO_CANETA)) {
			try {
				return Font.createFont(Font.TRUETYPE_FONT, Caminhos.arquivo(ARQUIVO_CANETA));
			} catch (Exception e) {
				System.out.println("[Carta] não foi possível ler " + ARQUIVO_CANETA + ": " + e.getMessage());
			}
		}
		return new Font(Font.SERIF, Font.ITALIC, 1);
	}

	/**
	 * A carta inteira numa imagem (gerada uma vez, Java2D): uma folha de caderno velha (pautada, com
	 * margem, amarelada nas bordas, dobrada e com um canto rasgado) e o texto escrito à
	 * caneta por quem estava com medo: cada letra um pouco girada, fora da linha e de tamanho
	 * diferente, a tinta mais forte e mais fraca, as linhas tortas, e uma palavra riscada (~~assim~~).
	 */
	public static BufferedImage carta(Font caneta) {
		int w = LARGURA_FOLHA, h = ALTURA_FOLHA;
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		Random r = new Random(1958);

		// o contorno da folha: bordas um pouco irregulares e o canto de baixo à direita rasgado
		Path2D borda = new Path2D.Float();
		borda.moveTo(6, 8);
		for (int x = 6; x < w - 8; x += 14) borda.lineTo(x, 6 + r.nextFloat() * 4);
		for (int y = 8; y < h - 170; y += 14) borda.lineTo(w - 6 - r.nextFloat() * 4, y);
		float[][] rasgo = { { w - 8, h - 170 }, { w - 40, h - 140 }, { w - 30, h - 112 }, { w - 82, h - 86 },
				{ w - 76, h - 58 }, { w - 130, h - 34 }, { w - 150, h - 8 } };
		for (float[] p : rasgo) borda.lineTo(p[0] + r.nextFloat() * 6 - 3, p[1] + r.nextFloat() * 6 - 3);
		for (int x = w - 160; x > 6; x -= 14) borda.lineTo(x, h - 6 - r.nextFloat() * 4);
		for (int y = h - 8; y > 8; y -= 14) borda.lineTo(6 + r.nextFloat() * 4, y);
		borda.closePath();
		g.setClip(borda);

		// o papel: velho, manchado, amassado e queimado nas bordas (gerado pixel a pixel)
		g.drawImage(pergaminho(w, h, 1958), 0, 0, null);
		for (int i = 0; i < 1800; i++) { // pontinhos de sujeira
			float d = 0.8f + r.nextFloat() * 1.8f;
			g.setColor(new Color(70, 45, 20, 30 + r.nextInt(70)));
			g.fill(new java.awt.geom.Ellipse2D.Float(r.nextFloat() * w, r.nextFloat() * h, d, d));
		}
		// as pautas azuis e a margem vermelha, desbotadas
		g.setStroke(new BasicStroke(1.6f));
		for (int y = TOPO; y < h - 30; y += ENTRE_LINHAS) {
			g.setColor(new Color(90, 115, 160, 55));
			g.drawLine(0, y, w, y + 1);
		}
		g.setColor(new Color(170, 60, 50, 70));
		g.drawLine(MARGEM - 14, 0, MARGEM - 12, h);
		g.drawLine(0, TOPO - ENTRE_LINHAS, w, TOPO - ENTRE_LINHAS);
		// as dobras, em cruz
		g.setStroke(new BasicStroke(2f));
		g.setColor(new Color(140, 120, 90, 60));
		g.drawLine(0, h / 2, w, h / 2 + 3);
		g.drawLine(w / 2, 0, w / 2 - 2, h);
		g.setColor(new Color(255, 252, 240, 60));
		g.drawLine(0, h / 2 + 3, w, h / 2 + 6);
		// o contorno da folha (o rasgo inclusive) também escurece, como as bordas
		for (int i = 0; i < 50; i++) {
			g.setColor(new Color(110, 65, 25, 8));
			g.setStroke(new BasicStroke(2f));
			g.draw(borda);
			g.translate(w / 2.0, h / 2.0);
			g.scale(0.997, 0.997);
			g.translate(-w / 2.0, -h / 2.0);
		}
		g.setTransform(new java.awt.geom.AffineTransform());

		escrever(g, caneta, r, w);
		g.dispose();
		return img;
	}

	/**
	 * O texto à caneta, letra por letra, nas pautas. A letra encolhe até o texto caber na folha,
	 * e a mão treme cada vez mais a cada linha (o medo aumentando até o fim).
	 */
	private static void escrever(Graphics2D g, Font caneta, Random r, int w) {
		float largura = w - MARGEM - 50;
		int pautas = (ALTURA_FOLHA - 40 - TOPO) / ENTRE_LINHAS + 1;
		float tamanho = TAMANHO_LETRA;
		List<String> linhas = quebrar(g, caneta.deriveFont(Font.PLAIN, tamanho), largura);
		while (linhas.size() > pautas && tamanho > 30) {
			tamanho -= 2;
			linhas = quebrar(g, caneta.deriveFont(Font.PLAIN, tamanho), largura);
		}
		Font fonte = caneta.deriveFont(Font.PLAIN, tamanho);
		g.setFont(fonte);
		FontMetrics medidas = g.getFontMetrics();
		int linha = 0;
		for (String texto : linhas) {
			if (texto.isEmpty()) { // parágrafo novo: pula uma pauta
				linha++;
				continue;
			}
			{
				float tremor = 1 + linha * 0.07f;
				float base = TOPO + linha * ENTRE_LINHAS - 7;
				float inclinacao = (r.nextFloat() - 0.5f) * 0.035f * tremor; // a linha sobe ou desce um pouco
				float x = MARGEM + r.nextFloat() * 8;
				boolean riscando = false;
				float inicioRisco = 0;
				for (int i = 0; i < texto.length(); i++) {
					if (texto.startsWith("~~", i)) {
						if (riscando) riscar(g, r, inicioRisco, x, base + (x - MARGEM) * inclinacao - tamanho * 0.28f);
						else inicioRisco = x;
						riscando = !riscando;
						i++;
						continue;
					}
					String letra = texto.substring(i, i + 1);
					float y = base + (x - MARGEM) * inclinacao + (r.nextFloat() - 0.5f) * 3.5f * tremor;
					float escala = 1 + (r.nextFloat() - 0.5f) * 0.14f * tremor;
					java.awt.geom.AffineTransform antes = g.getTransform();
					g.translate(x, y);
					g.rotate((r.nextFloat() - 0.5f) * 0.12f * tremor);
					g.scale(escala, escala);
					int forca = 190 + r.nextInt(60); // a pressão da caneta
					g.setColor(new Color(22, 30, 78, forca));
					g.drawString(letra, 0, 0);
					g.setTransform(antes);
					x += medidas.stringWidth(letra) * escala + (r.nextFloat() - 0.3f) * 1.6f;
				}
				linha++;
			}
		}
	}

	/**
	 * O papel envelhecido, pixel a pixel: manchas amarelas e marrons em várias escalas (ruído
	 * suave somado em oitavas), vincos de papel amassado (o mesmo ruído "dobrado": claro na crista
	 * e escuro em volta), grão fino e as bordas queimadas, com a queimadura entrando de forma
	 * irregular (a distância da borda é mexida pelas manchas).
	 */
	static BufferedImage pergaminho(int w, int h, long semente) {
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				float m = fbm(x / 230f, y / 230f, 5, semente);
				float dobra = 1 - Math.abs(2 * fbm(x / 120f, y / 120f, 4, semente + 1) - 1);
				float grao = ruido(x / 2.2f, y / 2.2f, semente + 2) - 0.5f;
				float t = Math.max(0, Math.min(1, m * 1.5f - 0.3f)); // claro ↔ manchado
				float cr = 232 - 52 * t, cg = 210 - 62 * t, cb = 164 - 72 * t;
				float luz = dobra > 0.82f ? (dobra - 0.82f) * 90 : (dobra - 0.82f) * 14; // os vincos
				luz += grao * 16;
				float borda = Math.min(Math.min(x, w - 1 - x), Math.min(y, h - 1 - y)) + (m - 0.5f) * 90;
				float queimado = (float) Math.pow(1 - Math.max(0, Math.min(1, borda / 120f)), 1.6) * 0.9f;
				cr = (cr + luz) * (1 - queimado) + 112 * queimado;
				cg = (cg + luz) * (1 - queimado) + 64 * queimado;
				cb = (cb + luz * 0.8f) * (1 - queimado) + 24 * queimado;
				img.setRGB(x, y, 0xFF000000 | (canal(cr) << 16) | (canal(cg) << 8) | canal(cb));
			}
		}
		return img;
	}

	private static int canal(float v) {
		return Math.max(0, Math.min(255, Math.round(v)));
	}

	/** Ruído suave em oitavas (fBm): cada oitava tem o dobro da frequência e metade da força. De 0 a 1. */
	static float fbm(float x, float y, int oitavas, long semente) {
		float soma = 0, forca = 0.5f, total = 0;
		for (int o = 0; o < oitavas; o++) {
			soma += ruido(x, y, semente + o * 101) * forca;
			total += forca;
			x *= 2;
			y *= 2;
			forca *= 0.5f;
		}
		return soma / total;
	}

	/** Ruído de valor: números sorteados nos cantos de uma grade, misturados suavemente no meio. De 0 a 1. */
	static float ruido(float x, float y, long semente) {
		int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
		float fx = x - x0, fy = y - y0;
		fx = fx * fx * (3 - 2 * fx); // suaviza (smoothstep)
		fy = fy * fy * (3 - 2 * fy);
		float a = canto(x0, y0, semente), b = canto(x0 + 1, y0, semente);
		float c = canto(x0, y0 + 1, semente), d = canto(x0 + 1, y0 + 1, semente);
		return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fy;
	}

	/** Um número de 0 a 1 "sorteado" para cada canto da grade (sempre o mesmo para o mesmo canto). */
	private static float canto(int x, int y, long semente) {
		long n = x * 374761393L + y * 668265263L + semente * 2147483647L;
		n = (n ^ (n >>> 13)) * 1274126177L;
		n ^= n >>> 16;
		return (n & 0xFFFFFF) / (float) 0xFFFFFF;
	}

	/** Os parágrafos quebrados em linhas que cabem na largura ("" = parágrafo novo; ~~riscado~~ conta sem os ~~). */
	private static List<String> quebrar(Graphics2D g, Font fonte, float largura) {
		FontMetrics medidas = g.getFontMetrics(fonte);
		List<String> linhas = new ArrayList<>();
		for (String paragrafo : TEXTO) {
			if (paragrafo.isEmpty()) {
				linhas.add("");
				continue;
			}
			StringBuilder atual = new StringBuilder();
			for (String palavra : paragrafo.split(" ")) {
				String tentativa = atual.length() == 0 ? palavra : atual + " " + palavra;
				if (atual.length() > 0 && medidas.stringWidth(tentativa.replace("~~", "")) > largura) {
					linhas.add(atual.toString());
					atual = new StringBuilder(palavra);
				} else {
					atual = new StringBuilder(tentativa);
				}
			}
			linhas.add(atual.toString());
		}
		return linhas;
	}

	/** O risco por cima de uma palavra escrita errado: um zigue-zague apressado. */
	private static void riscar(Graphics2D g, Random r, float x0, float x1, float y) {
		g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(new Color(22, 30, 78, 225));
		Path2D risco = new Path2D.Float();
		risco.moveTo(x0 - 3, y + 4);
		for (float x = x0; x < x1 + 4; x += 7) risco.lineTo(x, y + (r.nextBoolean() ? -6 : 6) + r.nextFloat() * 3);
		g.draw(risco);
		g.drawLine(Math.round(x0 - 4), Math.round(y), Math.round(x1 + 4), Math.round(y - 2));
	}
}
