package telas;

import java.awt.Color;

import audio.Audio;
import audio.Efeito;
import audio.Sintetizador;
import audio.SonsInterface;
import dados.Recursos;
import jogo.Nota;
import jogo.Partida;
import render.AtmosferaMenu;
import render.Confete;
import render.Overlay2D;
import render.TextoTextura;

/**
 * APROVADO. Depois da noite inteira na floresta, a comemoração:
 *  - toca o audio/aprovado.ogg e o título dourado é carimbado com um brilho pulsando atrás;
 *  - a noite acaba: a Univali ganha uma luz quente de amanhecer subindo da base e raios de luz
 *    girando atrás do título;
 *  - no carimbo, dois canhões de confete estouram dos cantos de baixo (papeizinhos e folhas de
 *    prova), e depois o confete continua caindo do alto;
 *  - a média final sobe de 0 até a nota, com um tique a cada décimo e um sino no fim;
 *  - brilhos piscando em volta do título. Sem a lâmpada falhando: aqui o susto acabou.
 */
public class TelaVitoria extends TelaFim {

	private static final Color COR_TITULO = new Color(240, 200, 90);
	private static final Color COR_BRILHO = new Color(255, 214, 120);
	private static final Color COR_ESTRELA = new Color(255, 244, 200);

	/** A média sobe de 0 até a nota entre esses instantes (segundos). */
	private static final float INICIO_NOTA = 1.0f, DURACAO_NOTA = 1.4f;
	/** Quanto tempo a luz do amanhecer leva para subir. */
	private static final float DURACAO_AMANHECER = 3f;

	private final AtmosferaMenu atmosfera = new AtmosferaMenu();
	private final Confete confete = new Confete(random);
	private final float nota;
	private float notaMostrada = 0;
	private int ultimoDecimo = 0;
	private boolean notaCompleta = false;
	private Efeito somSino;

	public TelaVitoria(GerenciadorTelas gerenciador, Partida.Estatisticas estatisticas) {
		this(gerenciador, estatisticas, Nota.calcular(estatisticas.segundos(), estatisticas.cacadasSobrevividas()));
	}

	private TelaVitoria(GerenciadorTelas gerenciador, Partida.Estatisticas estatisticas, float nota) {
		super(gerenciador, estatisticas, Nota.comentario(nota));
		this.nota = nota;
	}

	@Override
	protected String titulo() {
		return "APROVADO";
	}

	@Override
	protected Color corTitulo() {
		return COR_TITULO;
	}

	@Override
	protected String linhaDestaque() {
		return "Média final: " + Nota.formatar(notaMostrada);
	}

	@Override
	protected boolean luzFalhando() {
		return false;
	}

	@Override
	protected void tocarEntrada(Audio audio) {
		audio.musica().fadeOut(400);
		audio.tocar(audio.carregarEfeito("audio/aprovado.ogg"), 0.9f);
		somSino = audio.criarEfeito("sino da nota", Sintetizador.ping(44100, 1318, 500, 700), 44100);
	}

	@Override
	public void atualizar(long diftime) {
		float antes = tempo;
		atmosfera.atualizar(diftime);
		super.atualizar(diftime);
		float proporcao = (float) gerenciador.larguraJanela() / Math.max(1, gerenciador.alturaJanela());

		// o carimbo: os dois canhões de confete, dos cantos de baixo para o centro, e a chuva
		if (antes < CARIMBO && tempo >= CARIMBO) {
			float angulo = (float) Math.toRadians(58);
			confete.explodir(0.02f, 1.02f, 110, -angulo, 0.32f, 3.0f);
			confete.explodir(proporcao - 0.02f, 1.02f, 110, (float) Math.PI + angulo, 0.32f, 3.0f);
			confete.chover(9);
		}

		// a média subindo, desacelerando no fim (como um contador de pontos)
		float p = Math.max(0, Math.min(1, (tempo - INICIO_NOTA) / DURACAO_NOTA));
		notaMostrada = nota * (1 - (1 - p) * (1 - p) * (1 - p));
		int decimo = Math.round(notaMostrada * 10);
		if (decimo != ultimoDecimo) {
			ultimoDecimo = decimo;
			SonsInterface.ajuste(gerenciador.audio());
		}
		if (p >= 1 && !notaCompleta) {
			notaCompleta = true;
			gerenciador.audio().tocar(somSino, 0.7f);
			// mais uma leva caindo do alto (de baixo, cobriria a frase do professor)
			confete.explodir(proporcao / 2, -0.05f, 70, (float) Math.PI / 2, 1.1f, 1.2f);
		}

		confete.atualizar(diftime / 1000f, proporcao);
	}

	@Override
	protected void desenharFundo(Overlay2D o, int w, int h) {
		atmosfera.desenharFundo(o, w, h);
		// véu escuro e quente por cima da foto: os textos continuam legíveis
		o.retangulo(0, 0, w, h, new Color(22, 13, 8, 135));

		// o amanhecer: uma luz alaranjada subindo da base, em faixas cada vez mais fortes
		float amanhecer = Math.min(1, tempo / DURACAO_AMANHECER);
		int faixas = 24;
		float topo = h * 0.4f, altura = (h - topo) / faixas;
		for (int i = 0; i < faixas; i++) {
			float f = (i + 1f) / faixas;
			int alfa = Math.round(f * f * 85 * amanhecer);
			// bordas arredondadas para o pixel: faixas encostadas, sem sobrar linha entre elas
			int y0 = Math.round(topo + i * altura), y1 = Math.round(topo + (i + 1) * altura);
			o.retangulo(0, y0, w, y1 - y0, new Color(255, 150, 60, alfa));
		}

		// raios de luz girando devagar atrás do título (cada barra atravessa o centro: dois raios)
		float aparece = Math.max(0, Math.min(1, (tempo - CARIMBO) / 0.8f));
		float cx = w / 2f, cy = h * 0.3f, comprimento = Math.max(w, h) * 1.6f;
		for (int i = 0; i < 8; i++) {
			float a = i * (float) Math.PI / 8 + tempo * 0.12f;
			o.retanguloGirado(cx, cy, comprimento, h * 0.055f, a, new Color(255, 215, 120, Math.round(15 * aparece)));
			o.retanguloGirado(cx, cy, comprimento, h * 0.02f, -a * 1.3f, new Color(255, 230, 160, Math.round(10 * aparece)));
		}
	}

	/**
	 * Um brilho dourado pulsando atrás das letras do título: cópias claras do texto, do mesmo
	 * tamanho, deslocadas em volta (um "desfoque" barato que forma um halo).
	 */
	@Override
	protected void desenharJuntoDoTitulo(Overlay2D o, float x, float y, float escala) {
		TextoTextura.Imagem titulo = Recursos.textos().obter(titulo(), Recursos.fonteTitulo(), TAMANHO_TITULO, corTitulo(),
				true, true);
		TextoTextura.Imagem brilho = Recursos.textos().obter(titulo(), Recursos.fonteTitulo(), TAMANHO_TITULO, COR_BRILHO,
				false, false);
		if (titulo == null || brilho == null) return;
		float cx = x + titulo.largura() * escala / 2, cy = y + titulo.altura() * escala / 2;
		float lw = brilho.largura() * escala, lh = brilho.altura() * escala;
		float pulso = 0.5f + 0.5f * (float) Math.sin(tempo * 3);
		Color cor = new Color(1f, 1f, 1f, 0.07f + 0.07f * pulso);
		for (float raio : new float[] { 6, 12 }) {
			for (int i = 0; i < 8; i++) {
				double a = i * Math.PI / 4;
				float ox = (float) Math.cos(a) * raio * escala * 2, oy = (float) Math.sin(a) * raio * escala * 2;
				o.imagem(brilho.textura(), cx - lw / 2 + ox, cy - lh / 2 + oy, lw, lh, cor);
			}
		}
	}

	@Override
	protected void desenharPorCima(Overlay2D o, int w, int h) {
		confete.desenhar(o, w, h);
		desenharEstrelas(o, w, h);
	}

	/** Estrelinhas de quatro pontas que acendem e apagam em volta do título, cada uma no seu ritmo. */
	private void desenharEstrelas(Overlay2D o, int w, int h) {
		if (tempo < CARIMBO) return;
		for (int i = 0; i < 12; i++) {
			float periodo = 1.1f + (i % 4) * 0.25f;
			float ciclo = (tempo + i * 0.37f) / periodo;
			int volta = (int) ciclo;
			float fase = ciclo - volta;
			// a cada volta a estrela nasce num lugar novo (sorteado pelo número dela e da volta)
			long semente = i * 7919L + volta * 104729L;
			float px = w / 2f + (hash(semente) - 0.5f) * w * 0.62f;
			float py = h * 0.3f + (hash(semente + 1) - 0.5f) * h * 0.26f;
			float tamanho = (float) Math.sin(fase * Math.PI) * h * 0.022f;
			Color cor = new Color(COR_ESTRELA.getRed(), COR_ESTRELA.getGreen(), COR_ESTRELA.getBlue(),
					Math.round(230 * (float) Math.sin(fase * Math.PI)));
			o.retanguloGirado(px, py, tamanho, tamanho * 0.16f, 0, cor);
			o.retanguloGirado(px, py, tamanho, tamanho * 0.16f, (float) Math.PI / 2, cor);
		}
	}

	/** Um número de 0 a 1 que parece aleatório, mas é sempre o mesmo para a mesma semente. */
	private static float hash(long semente) {
		long x = semente * 0x9E3779B97F4A7C15L;
		x ^= x >>> 31;
		x *= 0xBF58476D1CE4E5B9L;
		x ^= x >>> 29;
		return (x >>> 40) / (float) (1L << 24);
	}
}
