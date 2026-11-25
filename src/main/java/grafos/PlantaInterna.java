package grafos;

import java.util.ArrayList;
import java.util.List;

import mundo.GradeColisao;

/**
 * Por onde algo grande anda dentro de um prédio (o Dragon Knight na Univali): uma área retangular
 * do mundo, uma grade de caminhada montada a partir da grade de colisão (paredes e móveis) e os
 * pontos de onde ele pode surgir.
 *
 * A célula é livre quando um círculo de "raio" metros cabe no centro dela sem encostar em nada:
 * com um raio de 0,75 m, ele passa pelo corredor (4 m) e pelas laterais do balcão do saguão
 * (1,9 m), mas não pelas portas das salas (1,4 m). O caminho é o do A* (sem custo de inclinação: o chão é plano), em metros do mundo.
 */
public class PlantaInterna {

	private final GradeColisao colisao;
	private final float x0, z0, largura, profundidade, raio;
	/** A altura dos olhos dele (y do mundo): o que é mais baixo (balcão, carteiras) não atrapalha a vista. */
	private final float alturaVisao;
	private final GradeCaminhada grade;
	private final List<float[]> pontas;

	public PlantaInterna(GradeColisao colisao, GradeColisao.Retangulo area, float raio, float celula, List<float[]> pontas,
			float alturaVisao) {
		this.colisao = colisao;
		this.alturaVisao = alturaVisao;
		this.x0 = area.xMin();
		this.z0 = area.zMin();
		this.largura = area.xMax() - area.xMin();
		this.profundidade = area.zMax() - area.zMin();
		this.raio = raio;
		this.pontas = List.copyOf(pontas);
		int lado = (int) Math.ceil(Math.max(largura, profundidade) / celula);
		grade = GradeCaminhada.deLivres(celula, lado, (lx, lz) -> livre(x0 + lx, z0 + lz));
	}

	/** Cabe ali (dentro da área e sem encostar em parede nem móvel)? */
	public boolean livre(float x, float z) {
		return x - raio >= x0 && x + raio <= x0 + largura && z - raio >= z0 && z + raio <= z0 + profundidade
				&& !colisao.colide(x, z, raio);
	}

	/** Dá para ir em linha reta (nada no meio, nem móvel, com metade do raio de folga)? */
	public boolean linhaLivre(float x1, float z1, float x2, float z2) {
		return colisao.linhaLivre(x1, z1, x2, z2, raio * 0.5f);
	}

	/** Dá para ver (na altura dos olhos: por cima dos móveis baixos)? */
	public boolean ve(float x1, float z1, float x2, float z2) {
		return colisao.linhaLivreNaAltura(x1, z1, x2, z2, 0.1f, alturaVisao);
	}

	/** O caminho do A* de (xa, za) até (xb, zb), em pontos {x, z} do mundo (o último é o destino), ou null. */
	public List<float[]> caminho(float xa, float za, float xb, float zb) {
		int[] de = celulaLivrePerto(xa - x0, za - z0), para = celulaLivrePerto(xb - x0, zb - z0);
		if (de == null || para == null) return null;
		AEstrela.Caminho c = AEstrela.buscar(grade, de[0], de[1], para[0], para[1], 0);
		if (c == null) return null;
		List<float[]> pontos = new ArrayList<>();
		for (int[] celula : c.celulas()) {
			pontos.add(new float[] { x0 + grade.centro(celula[0]), z0 + grade.centro(celula[1]) });
		}
		if (livre(xb, zb)) pontos.set(pontos.size() - 1, new float[] { xb, zb });
		return pontos;
	}

	/** A célula livre mais perto do ponto local (até 4 células em volta), ou null. */
	private int[] celulaLivrePerto(float lx, float lz) {
		int ci = grade.celulaDe(lx), cj = grade.celulaDe(lz);
		for (int r = 0; r <= 4; r++) {
			for (int dj = -r; dj <= r; dj++) {
				for (int di = -r; di <= r; di++) {
					if (Math.max(Math.abs(di), Math.abs(dj)) == r && grade.caminhavel(ci + di, cj + dj)) {
						return new int[] { ci + di, cj + dj };
					}
				}
			}
		}
		return null;
	}

	/** De onde ele pode surgir, {x, z} no mundo. */
	public List<float[]> pontas() {
		return pontas;
	}

	public float raio() {
		return raio;
	}
}
