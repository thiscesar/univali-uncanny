package md2;

import java.util.List;

/**
 * O conteúdo de um arquivo MD2, como está no arquivo (coordenadas do Quake, com Z para cima).
 *
 * @param larguraSkin   largura da textura em pixels (as coordenadas s vêm em pixels)
 * @param alturaSkin    altura da textura em pixels
 * @param numeroPosicoes quantas posições (vértices) cada quadro tem
 * @param s             coordenada horizontal da textura de cada entrada, em pixels
 * @param t             coordenada vertical da textura de cada entrada, em pixels (0 no topo)
 * @param verticesTriangulo para cada triângulo, os 3 índices de posição
 * @param texturaTriangulo  para cada triângulo, os 3 índices de coordenada de textura
 * @param quadros       os quadros da animação, na ordem do arquivo
 */
public record ArquivoMd2(
		int larguraSkin,
		int alturaSkin,
		int numeroPosicoes,
		short[] s,
		short[] t,
		int[] verticesTriangulo,
		int[] texturaTriangulo,
		List<Quadro> quadros) {

	/** Um quadro: o nome (ex.: "stand01") e as posições x, y, z de cada vértice, já em float. */
	public record Quadro(String nome, float[] posicoes) {}

	public int numeroTriangulos() {
		return verticesTriangulo.length / 3;
	}
}
