package telas;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.lwjgl.opengl.GL11.glDeleteTextures;

import audio.Audio;
import audio.Sintetizador;
import dados.Recursos;
import render.Escorridos;
import render.Overlay2D;
import render.TextoTextura;

/**
 * REPROVADO. O fundo é o último quadro da partida (o Dragon Knight bem de perto), congelado,
 * escurecido e tingido de vermelho, chegando mais perto devagar. O título é carimbado em
 * vermelho-sangue e começa a escorrer.
 */
public class TelaDerrota extends TelaFim {

	private static final List<String> FRASES = List.of(
			"O Dragon Knight não aceita atestado.",
			"Faltou na prova final.",
			"Nem o DCE pode te ajudar agora.",
			"Presença: 0%. Dragon Knight: 100%.",
			"Trancou a matrícula. Para sempre.");
	private static final Color COR_TITULO = new Color(150, 8, 6);
	private static final Color COR_SANGUE = new Color(105, 2, 2);
	private static final Color COR_SANGUE_CLARO = new Color(160, 12, 8);

	/** O último quadro da partida (null se não houver: fundo escuro). */
	private final Recursos.Textura foto;
	private final List<Escorridos.Escorrido> escorridos;

	public TelaDerrota(GerenciadorTelas gerenciador, Resumo resumo, Recursos.Textura foto) {
		super(gerenciador, resumo, FRASES);
		this.foto = foto;
		// os escorridos saem das letras da mesma imagem que vai para a tela
		BufferedImage letras = TextoTextura.gerarImagem(titulo(), Recursos.fonteTitulo().emTamanho(TAMANHO_TITULO),
				COR_TITULO, true, true);
		escorridos = Escorridos.gerar(letras, 16, random);
	}

	@Override
	protected String titulo() {
		return "REPROVADO";
	}

	@Override
	protected Color corTitulo() {
		return COR_TITULO;
	}

	@Override
	protected void tocarEntrada(Audio audio) {
		// um rugido grave sumindo, como se ele ainda estivesse ali
		audio.tocar(audio.criarEfeito("rugido do fim", Sintetizador.rugido(44100, 2600, 70, 26, 0.5, 9), 44100), 0.8f);
	}

	@Override
	protected void desenharFundo(Overlay2D o, int w, int h) {
		if (foto != null) {
			// cobre a tela sem esticar e se aproxima devagar; a foto do OpenGL vem de cabeça
			// para baixo (a linha 0 é a de baixo da tela), então o recorte inverte o v
			float escala = Math.max((float) w / foto.largura(), (float) h / foto.altura()) * (1.02f + tempo * 0.012f);
			float lw = foto.largura() * escala, lh = foto.altura() * escala;
			o.imagemRecorte(foto.id(), (w - lw) / 2, (h - lh) / 2, lw, lh, 0, 1, 1, 0, new Color(255, 120, 105));
		}
		// escurece, mais ainda com o tempo
		o.retangulo(0, 0, w, h, new Color(0, 0, 0, Math.round(40 + 70 * Math.min(1, tempo / 6))));
	}

	@Override
	protected void desenharJuntoDoTitulo(Overlay2D o, float x, float y, float escala) {
		float t = tempo - CARIMBO;
		for (Escorridos.Escorrido e : escorridos) {
			float descido = e.descido(t);
			if (descido <= 0) continue;
			float ex = x + e.x() * escala, ey = y + e.y() * escala, ew = e.largura() * escala;
			o.retangulo(ex, ey, ew, descido * escala, COR_SANGUE);
			o.retangulo(ex + ew * 0.2f, ey, ew * 0.25f, descido * escala * 0.9f, COR_SANGUE_CLARO); // brilho
			float gota = ew * 1.5f; // a gota na ponta, um pouco mais larga
			o.retangulo(ex - (gota - ew) / 2, ey + descido * escala - gota * 0.6f, gota, gota, COR_SANGUE);
		}
	}

	@Override
	public void sair() {
		if (foto != null) {
			glDeleteTextures(foto.id());
		}
	}
}
