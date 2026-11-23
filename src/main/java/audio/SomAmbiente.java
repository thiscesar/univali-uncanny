package audio;

import java.util.Random;

/**
 * O som da floresta: um fundo em loop (vento, grilos) e, de tempos em tempos, um som
 * solto (galho quebrando, coruja) num ponto aleatório em volta do jogador, em 3D.
 * Sem os arquivos, tudo fica em silêncio.
 */
public class SomAmbiente {

	private static final float INTERVALO_MINIMO = 12f;  // segundos entre sons soltos
	private static final float INTERVALO_MAXIMO = 35f;

	private final Audio audio;
	private final Random random = new Random();
	private Efeito fundo;
	private Efeito[] soltos;
	private SomTocando somFundo;
	private float proximo;

	public SomAmbiente(Audio audio) {
		this.audio = audio;
	}

	public void iniciar() {
		fundo = audio.carregarEfeito("audio/ambiente_floresta.ogg");
		soltos = new Efeito[] {
				audio.carregarEfeito("audio/galho_quebrando.ogg"),
				audio.carregarEfeito("audio/coruja.ogg") };
		somFundo = audio.tocar(fundo, 0.55f, true);
		proximo = sortearIntervalo();
	}

	/** dt em segundos; (x, y, z) é a posição do jogador. */
	public void atualizar(float dt, float x, float y, float z) {
		proximo -= dt;
		if (proximo > 0) {
			return;
		}
		proximo = sortearIntervalo();
		double angulo = random.nextDouble() * 2 * Math.PI;
		float distancia = 10 + random.nextFloat() * 15; // 10 a 25 m, em qualquer direção
		Efeito e = soltos[random.nextInt(soltos.length)];
		audio.tocar3D(e, x + distancia * (float) Math.cos(angulo), y + random.nextFloat() * 4,
				z + distancia * (float) Math.sin(angulo), 0.8f + random.nextFloat() * 0.2f, false);
	}

	public void parar() {
		audio.parar(somFundo);
		somFundo = null;
	}

	private float sortearIntervalo() {
		return INTERVALO_MINIMO + random.nextFloat() * (INTERVALO_MAXIMO - INTERVALO_MINIMO);
	}
}
