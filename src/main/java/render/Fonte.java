package render;

import java.awt.Font;
import java.awt.FontFormatException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import util.Caminhos;

/**
 * Uma fonte TrueType carregada da pasta fontes/. Se o arquivo não existir, usa uma
 * fonte do sistema no lugar, para o jogo nunca travar por falta de arquivo.
 */
public class Fonte {

	private final String nome;
	private final Font base;
	private final Map<Integer, Font> porTamanho = new HashMap<>();

	private Fonte(String nome, Font base) {
		this.nome = nome;
		this.base = base;
	}

	/**
	 * @param relativo        por exemplo "fontes/titulo.ttf"
	 * @param familiaReserva  fonte do sistema usada se o arquivo faltar (Font.SERIF, Font.SANS_SERIF...)
	 * @param estiloReserva   Font.PLAIN, Font.BOLD... da fonte reserva
	 */
	public static Fonte carregar(String relativo, String familiaReserva, int estiloReserva) {
		if (Caminhos.arquivo(relativo).exists()) { // avisa no console se faltar
			try {
				Font f = Font.createFont(Font.TRUETYPE_FONT, Caminhos.arquivo(relativo));
				return new Fonte(relativo, f);
			} catch (FontFormatException | IOException e) {
				System.out.println("[Fonte] não foi possível ler " + relativo + ": " + e.getMessage());
			}
		}
		return new Fonte(familiaReserva + "-" + estiloReserva, new Font(familiaReserva, estiloReserva, 12));
	}

	/** A fonte no tamanho pedido, em pixels. Cada tamanho é criado uma vez só. */
	public Font emTamanho(int pixels) {
		return porTamanho.computeIfAbsent(pixels, t -> base.deriveFont((float) t));
	}

	/** Identifica a fonte na chave do cache de textos. */
	public String nome() {
		return nome;
	}
}
