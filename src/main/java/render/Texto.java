package render;

import java.awt.Color;

import dados.Recursos;

/** Atalhos para desenhar texto com o Overlay2D. */
public final class Texto {

	/** Alinhamento horizontal em relação ao x informado. */
	public enum Alinhamento { ESQUERDA, CENTRO, DIREITA }

	private Texto() {}

	/** Texto com a fonte padrão da interface e sombra leve. y é o topo do texto. */
	public static void desenhar(Overlay2D overlay, String texto, float x, float y, int tamanho,
			Color cor, Alinhamento alinhamento) {
		desenhar(overlay, Recursos.fonteTexto(), texto, x, y, tamanho, cor, alinhamento, false, true);
	}

	/** Versão completa: escolhe a fonte, o contorno e a sombra. y é o topo do texto. */
	public static void desenhar(Overlay2D overlay, Fonte fonte, String texto, float x, float y, int tamanho,
			Color cor, Alinhamento alinhamento, boolean contorno, boolean sombra) {
		TextoTextura.Imagem img = Recursos.textos().obter(texto, fonte, tamanho, cor, contorno, sombra);
		if (img == null) {
			return;
		}
		float esquerda = switch (alinhamento) {
			case ESQUERDA -> x;
			case CENTRO -> x - img.largura() / 2f;
			case DIREITA -> x - img.largura();
		};
		// posição inteira: a textura cai exatamente sobre os pixels e o texto fica nítido
		overlay.imagem(img.textura(), Math.round(esquerda), Math.round(y), img.largura(), img.altura(),
				new Color(255, 255, 255, cor.getAlpha()));
	}

	/** Texto com o centro vertical em cy e alinhamento horizontal em relação a x. */
	public static void desenharAlinhado(Overlay2D overlay, Fonte fonte, String texto, float x, float cy,
			int tamanho, Color cor, Alinhamento alinhamento, boolean contorno, boolean sombra) {
		TextoTextura.Imagem img = Recursos.textos().obter(texto, fonte, tamanho, cor, contorno, sombra);
		if (img == null) {
			return;
		}
		desenhar(overlay, fonte, texto, x, cy - img.altura() / 2f, tamanho, cor, alinhamento, contorno, sombra);
	}

	/** Largura em pixels que o texto ocupa (0 para texto vazio). */
	public static int largura(Fonte fonte, String texto, int tamanho) {
		TextoTextura.Imagem img = Recursos.textos().obter(texto, fonte, tamanho, Color.WHITE, false, false);
		return img == null ? 0 : img.largura();
	}

	/** Texto centralizado nos dois eixos em torno de (cx, cy). */
	public static void desenharCentralizado(Overlay2D overlay, Fonte fonte, String texto, float cx, float cy,
			int tamanho, Color cor, boolean contorno, boolean sombra) {
		TextoTextura.Imagem img = Recursos.textos().obter(texto, fonte, tamanho, cor, contorno, sombra);
		if (img == null) {
			return;
		}
		desenhar(overlay, fonte, texto, cx, cy - img.altura() / 2f, tamanho, cor, Alinhamento.CENTRO,
				contorno, sombra);
	}
}
