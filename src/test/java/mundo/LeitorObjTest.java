package mundo;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import util.Caminhos;

class LeitorObjTest {

	private static final int F = LeitorObj.FLOATS_VERTICE;

	@Test
	void leCorUvENormalDeUmTriangulo() {
		LeitorObj.ModeloObj m = LeitorObj.ler(List.of(
				"# comentário", "o casca", "usemtl casca",
				"v 0 0 0 0.5 0.25 1", "v 1 0 0 1 1 1", "v 0 2 0 1 1 1",
				"vt 0 0", "vt 1 0", "vt 0 1",
				"f 1/1 2/2 3/3"));
		assertEquals(3, m.quantidadeVertices());
		assertEquals(List.of(new LeitorObj.Grupo("casca", 0, 3)), m.grupos());
		assertEquals(2f, m.altura(), 1e-6);
		float[] v = m.vertices();
		assertArrayEquals(new float[] { 0, 0, 0, 0.5f, 0.25f, 1 }, java.util.Arrays.copyOfRange(v, 0, 6), 1e-6f);
		// (B - A) x (C - A) = (1,0,0) x (0,2,0) = (0,0,2): normal para +z
		assertArrayEquals(new float[] { 0, 0, 1 }, java.util.Arrays.copyOfRange(v, 6, 9), 1e-6f);
		// v do .obj invertido: a base da imagem (v = 0) é t = 1 no OpenGL do projeto
		assertEquals(1f, v[10], 1e-6f);
		assertEquals(0f, v[2 * F + 10], 1e-6f);
	}

	@Test
	void semCorFicaBrancoEQuadradoViraDoisTriangulos() {
		LeitorObj.ModeloObj m = LeitorObj.ler(List.of(
				"v 0 0 0", "v 1 0 0", "v 1 1 0", "v 0 1 0",
				"f 1 2 3 4"));
		assertEquals(6, m.quantidadeVertices());
		assertEquals("padrao", m.grupos().get(0).material());
		assertEquals(1f, m.vertices()[3], 1e-6f);
		assertEquals(1f, m.vertices()[5], 1e-6f);
	}

	@Test
	void indicesNegativosEBarraDupla() {
		LeitorObj.ModeloObj m = LeitorObj.ler(List.of(
				"v 0 0 0", "v 1 0 0", "v 0 1 0", "vn 0 0 1",
				"f -3//1 -2//1 -1//1"));
		assertEquals(3, m.quantidadeVertices());
		assertEquals(1f, m.vertices()[F], 1e-6f);      // segundo canto: x = 1
		assertEquals(1f, m.vertices()[2 * F + 1], 1e-6f); // terceiro canto: y = 1
	}

	@Test
	void materiaisRepetidosFicamNoMesmoGrupo() {
		LeitorObj.ModeloObj m = LeitorObj.ler(List.of(
				"v 0 0 0", "v 1 0 0", "v 0 1 0",
				"usemtl a", "f 1 2 3", "usemtl b", "f 1 2 3", "usemtl a", "f 1 3 2"));
		assertEquals(List.of(new LeitorObj.Grupo("a", 0, 6), new LeitorObj.Grupo("b", 6, 3)), m.grupos());
	}

	@Test
	void normalEhAMediaDasFaces() {
		// dois triângulos dobrados em 90° pela aresta do eixo x: a normal da aresta fica a 45°
		LeitorObj.ModeloObj m = LeitorObj.ler(List.of(
				"v 0 0 0", "v 1 0 0", "v 0 1 0", "v 0 0 -1",
				"f 1 2 3", "f 1 2 4"));
		float[] v = m.vertices();
		float raiz = (float) Math.sqrt(0.5);
		assertArrayEquals(new float[] { 0, raiz, raiz }, java.util.Arrays.copyOfRange(v, 6, 9), 1e-5f);
	}

	@Test
	void leOsModelosDasArvoresDoGerador() throws Exception {
		if (!Arvores3D.arquivosPresentes()) return; // sem os modelos, o jogo usa as árvores planas
		List<LeitorObj.ModeloObj> modelos = Arvores3D.lerModelos();
		assertEquals(DistribuidorArvores.VARIACOES, modelos.size());
		for (LeitorObj.ModeloObj m : modelos) {
			assertTrue(m.altura() > 3 && m.altura() < 7, "altura em metros: " + m.altura());
			int soma = 0;
			for (LeitorObj.Grupo g : m.grupos()) {
				assertTrue(List.of(Arvores3D.MATERIAIS).contains(g.material()), g.material());
				assertTrue(Caminhos.arquivo(Arvores3D.PASTA + g.material() + ".png").exists());
				soma += g.quantidade();
			}
			assertEquals(m.quantidadeVertices(), soma);
		}
	}
}
