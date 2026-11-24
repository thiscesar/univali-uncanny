package md2;

import java.util.ArrayList;
import java.util.List;

/**
 * Um ponto preso à superfície de um MD2, achado pela coordenada de textura. Serve para saber
 * onde estão os olhos do Dragon Knight em cada quadro da animação: o olho está pintado num
 * lugar da skin, e o triângulo que cobre esse lugar da skin é o triângulo do olho no modelo.
 *
 * Como num MD2 os dois lados da cabeça costumam usar o mesmo pedaço da skin (espelhado),
 * o mesmo ponto da skin pode cair em dois triângulos: um olho de cada lado.
 *
 * Cada ponto guarda os 3 vértices do triângulo e os pesos (coordenadas baricêntricas) que
 * levam ao ponto. A posição em qualquer quadro é a soma dos 3 vértices com esses pesos.
 * Não usa OpenGL.
 */
public final class PontoMd2 {

	private final int a, b, c;        // vértices desdobrados do triângulo
	private final float pa, pb, pc;   // pesos baricêntricos (somam 1)

	private PontoMd2(int a, int b, int c, float pa, float pb, float pc) {
		this.a = a;
		this.b = b;
		this.c = c;
		this.pa = pa;
		this.pb = pb;
		this.pc = pc;
	}

	/** Todos os pontos do modelo que ficam em (u, v) na skin (de 0 a 1; v conta do topo). */
	public static List<PontoMd2> naSkin(MalhaMd2 malha, float u, float v) {
		List<PontoMd2> lista = new ArrayList<>();
		float[] uv = malha.uvs;
		for (int t = 0; t < malha.vertices; t += 3) {
			float ax = uv[t * 2], ay = uv[t * 2 + 1];
			float bx = uv[(t + 1) * 2], by = uv[(t + 1) * 2 + 1];
			float cx = uv[(t + 2) * 2], cy = uv[(t + 2) * 2 + 1];
			// baricêntricas pela área com sinal: cada peso é a área do triângulo oposto ao vértice
			float area = (bx - ax) * (cy - ay) - (cx - ax) * (by - ay);
			if (Math.abs(area) < 1e-12f) continue; // triângulo achatado na skin
			float pb = ((u - ax) * (cy - ay) - (cx - ax) * (v - ay)) / area;
			float pc = ((bx - ax) * (v - ay) - (u - ax) * (by - ay)) / area;
			float pa = 1 - pb - pc;
			if (pa >= 0 && pb >= 0 && pc >= 0) {
				lista.add(new PontoMd2(t, t + 1, t + 2, pa, pb, pc));
			}
		}
		return lista;
	}

	/** Posição no quadro (já misturando A e B pelo fator, como o vertex shader faz). */
	public float[] posicao(MalhaMd2 malha, int quadroA, int quadroB, float fator) {
		return misturar(malha.posicoes.get(quadroA), malha.posicoes.get(quadroB), fator);
	}

	/** Normal do ponto no quadro (para afastar o brilho um pouco da superfície). */
	public float[] normal(MalhaMd2 malha, int quadroA, int quadroB, float fator) {
		float[] n = misturar(malha.normais.get(quadroA), malha.normais.get(quadroB), fator);
		float t = (float) Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
		if (t > 1e-6f) {
			n[0] /= t; n[1] /= t; n[2] /= t;
		}
		return n;
	}

	private float[] misturar(float[] qa, float[] qb, float fator) {
		float[] r = new float[3];
		for (int i = 0; i < 3; i++) {
			float noA = qa[a * 3 + i] * pa + qa[b * 3 + i] * pb + qa[c * 3 + i] * pc;
			float noB = qb[a * 3 + i] * pa + qb[b * 3 + i] * pb + qb[c * 3 + i] * pc;
			r[i] = noA + (noB - noA) * fator;
		}
		return r;
	}
}
