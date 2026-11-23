package render;

import java.util.Random;

/**
 * A "lâmpada falhando": de tempos em tempos (4 a 14 s), a tela escurece e volta várias
 * vezes seguidas, bem rápido, por uma fração de segundo. Só calcula o quanto escurecer;
 * quem desenha é o AtmosferaMenu. Não usa OpenGL (dá para testar).
 */
public class Piscadas {

	private final Random random;

	private float esperaRestante;  // segundos até a próxima crise
	private float criseRestante;   // segundos que faltam na crise atual
	private float trocaRestante;   // segundos até alternar entre apagado e aceso
	private boolean apagado = false;
	private float intensidade = 0;

	public Piscadas(Random random) {
		this.random = random;
		esperaRestante = proximaEspera();
	}

	/** dt em segundos. */
	public void atualizar(float dt) {
		if (criseRestante > 0) {
			criseRestante -= dt;
			trocaRestante -= dt;
			if (trocaRestante <= 0) {
				apagado = !apagado;
				trocaRestante = 0.03f + random.nextFloat() * 0.07f;
				intensidade = 0.45f + random.nextFloat() * 0.45f;
			}
			if (criseRestante <= 0) {
				apagado = false;
				esperaRestante = proximaEspera();
			}
		} else {
			esperaRestante -= dt;
			if (esperaRestante <= 0) {
				criseRestante = 0.15f + random.nextFloat() * 0.45f;
				trocaRestante = 0;
			}
		}
	}

	/** Quanto escurecer a tela agora: 0 = normal, até 0,9 = quase preto. */
	public float escuridao() {
		return apagado ? intensidade : 0;
	}

	private float proximaEspera() {
		return 4 + random.nextFloat() * 10;
	}
}
