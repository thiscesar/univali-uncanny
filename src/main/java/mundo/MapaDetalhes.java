package mundo;

import java.util.List;

import fase.Ponto;
import terreno.RuidoFractal;

/**
 * Uma imagem do mapa inteiro, vista de cima, que o shader do terreno lê para pintar o chão:
 *  - vermelho: as trilhas (1 no meio, sumindo nas bordas, que são irregulares);
 *  - verde: o quanto o ponto é mata morta;
 *  - azul: o quanto é pedregal.
 * Cada pixel cobre ~0,6 m (512 pixels para 300 m). Só dados: é montada na thread de
 * carregamento e enviada para a placa de vídeo depois.
 */
public final class MapaDetalhes {

	public static final int LADO = 512;
	/** Meia largura da trilha, em metros: cheia até aqui, some até o dobro. */
	public static final float MEIA_LARGURA_TRILHA = 0.9f;

	private MapaDetalhes() {}

	/** RGB, linha por linha, começando no z = 0 (a primeira linha da textura é a de coordenada 0). */
	public static byte[] gerar(float tamanho, List<List<Ponto>> trilhas, Regioes regioes, long seed) {
		float metrosPorPixel = tamanho / LADO;
		byte[] rgb = new byte[LADO * LADO * 3];
		float[] trilha = new float[LADO * LADO];
		RuidoFractal ruido = new RuidoFractal(seed + 77);

		// trilhas: cada segmento só olha os pixels em volta dele (bem mais rápido que medir tudo contra tudo)
		float alcance = MEIA_LARGURA_TRILHA * 2;
		for (List<Ponto> t : trilhas) {
			for (int s = 1; s < t.size(); s++) {
				Ponto a = t.get(s - 1), b = t.get(s);
				int x0 = Math.max(0, (int) ((Math.min(a.x(), b.x()) - alcance) / metrosPorPixel));
				int x1 = Math.min(LADO - 1, (int) ((Math.max(a.x(), b.x()) + alcance) / metrosPorPixel));
				int z0 = Math.max(0, (int) ((Math.min(a.z(), b.z()) - alcance) / metrosPorPixel));
				int z1 = Math.min(LADO - 1, (int) ((Math.max(a.z(), b.z()) + alcance) / metrosPorPixel));
				for (int pz = z0; pz <= z1; pz++) {
					for (int px = x0; px <= x1; px++) {
						float x = (px + 0.5f) * metrosPorPixel, z = (pz + 0.5f) * metrosPorPixel;
						float d = GradeColisao.distanciaAoSegmento(x, z, a.x(), a.z(), b.x(), b.z());
						// borda irregular: a largura varia um pouco ao longo do caminho
						float largura = MEIA_LARGURA_TRILHA * (0.8f + 0.4f * (float) (ruido.fbm(x / 6, z / 6, 2) * 0.5 + 0.5));
						float v = 1 - suave(largura, largura * 2, d);
						int k = pz * LADO + px;
						trilha[k] = Math.max(trilha[k], v);
					}
				}
			}
		}
		for (int pz = 0; pz < LADO; pz++) {
			for (int px = 0; px < LADO; px++) {
				int k = pz * LADO + px;
				float[] p = regioes != null ? regioes.pesos((px + 0.5f) * metrosPorPixel, (pz + 0.5f) * metrosPorPixel)
						: new float[4];
				rgb[k * 3] = (byte) Math.round(trilha[k] * 255);
				rgb[k * 3 + 1] = (byte) Math.round(p[Regioes.Tipo.MATA_MORTA.ordinal()] * 255);
				rgb[k * 3 + 2] = (byte) Math.round(p[Regioes.Tipo.PEDREGAL.ordinal()] * 255);
			}
		}
		return rgb;
	}

	private static float suave(float inicio, float fim, float x) {
		float t = Math.max(0, Math.min(1, (x - inicio) / (fim - inicio)));
		return t * t * (3 - 2 * t);
	}
}
