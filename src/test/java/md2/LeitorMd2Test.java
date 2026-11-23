package md2;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Um MD2 mínimo montado byte a byte aqui mesmo: 2 quadros, 1 triângulo, skin de 64 x 32.
 *
 * Quadro 0 ("stand01"): escala 1, translação 0, vértices (0,0,0), (0,10,0), (10,0,20).
 * Quadro 1 ("stand02"): escala 2, translação 1, os mesmos bytes (posição = byte * 2 + 1).
 */
class LeitorMd2Test {

	static byte[] md2Minimo(int ident, int versao) {
		int ofsSt = 68, ofsTris = ofsSt + 3 * 4, tamanhoQuadro = 40 + 3 * 4, ofsQuadros = ofsTris + 12;
		int ofsFim = ofsQuadros + 2 * tamanhoQuadro;
		ByteBuffer b = ByteBuffer.allocate(ofsFim).order(ByteOrder.LITTLE_ENDIAN);
		b.putInt(ident).putInt(versao).putInt(64).putInt(32).putInt(tamanhoQuadro);
		b.putInt(0).putInt(3).putInt(3).putInt(1).putInt(0).putInt(2);      // skins, xyz, st, tris, glcmds, frames
		b.putInt(ofsSt).putInt(ofsSt).putInt(ofsTris).putInt(ofsQuadros).putInt(ofsFim).putInt(ofsFim);
		b.putShort((short) 0).putShort((short) 0).putShort((short) 64).putShort((short) 0).putShort((short) 0).putShort((short) 32);
		b.putShort((short) 0).putShort((short) 1).putShort((short) 2).putShort((short) 0).putShort((short) 1).putShort((short) 2);
		for (int q = 0; q < 2; q++) {
			float e = q == 0 ? 1 : 2, t = q == 0 ? 0 : 1;
			b.putFloat(e).putFloat(e).putFloat(e).putFloat(t).putFloat(t).putFloat(t);
			byte[] nome = new byte[16];
			byte[] texto = ("stand0" + (q + 1)).getBytes(StandardCharsets.US_ASCII);
			System.arraycopy(texto, 0, nome, 0, texto.length);
			b.put(nome);
			b.put(new byte[] { 0, 0, 0, 0, 0, 10, 0, 0, 10, 0, 20, 0 }); // 3 vértices: x, y, z, normal
		}
		return b.array();
	}

	static byte[] md2Minimo() {
		return md2Minimo(LeitorMd2.IDENT, LeitorMd2.VERSAO);
	}

	@Test
	void leOCabecalhoEmLittleEndian() {
		ArquivoMd2 m = LeitorMd2.ler(md2Minimo());
		assertEquals(64, m.larguraSkin());
		assertEquals(32, m.alturaSkin());
		assertEquals(3, m.numeroPosicoes());
		assertEquals(1, m.numeroTriangulos());
		assertEquals(2, m.quadros().size());
		assertEquals("stand01", m.quadros().get(0).nome());
		assertEquals("stand02", m.quadros().get(1).nome());
	}

	@Test
	void descomprimeAsPosicoesComEscalaETranslacao() {
		float[] q1 = LeitorMd2.ler(md2Minimo()).quadros().get(1).posicoes();
		assertArrayEquals(new float[] { 1, 1, 1, 1, 21, 1, 21, 1, 41 }, q1, 1e-6f);
	}

	@Test
	void recusaArquivoQueNaoEMd2OuDeOutraVersao() {
		var semAssinatura = assertThrows(IllegalArgumentException.class, () -> LeitorMd2.ler(md2Minimo(0x12345678, 8)));
		assertTrue(semAssinatura.getMessage().contains("IDP2"));
		var outraVersao = assertThrows(IllegalArgumentException.class, () -> LeitorMd2.ler(md2Minimo(LeitorMd2.IDENT, 7)));
		assertTrue(outraVersao.getMessage().contains("7"));
		assertThrows(IllegalArgumentException.class, () -> LeitorMd2.ler(new byte[10]));
	}

	@Test
	void desdobraUmVerticePorCantoDeTriangulo() {
		ArquivoMd2 m = LeitorMd2.ler(md2Minimo());
		MalhaMd2 malha = new MalhaMd2(m, MalhaMd2.ajustePara(m, 2f));
		assertEquals(3, malha.vertices);
		assertEquals(2, malha.quadros());
		assertEquals(9, malha.posicoes.get(0).length);
		assertArrayEquals(new float[] { 0, 0, 1, 0, 0, 1 }, malha.uvs, 1e-6f, "s/64 e t/32, sem inverter o t");
	}

	@Test
	void converteZParaCimaEmYParaCimaComOsPesNoChao() {
		ArquivoMd2 m = LeitorMd2.ler(md2Minimo());
		MalhaMd2.Ajuste ajuste = MalhaMd2.ajustePara(m, 2f); // o quadro 0 vai de z = 0 a z = 20
		assertEquals(0.1f, ajuste.escala(), 1e-6f);
		float[] p = new MalhaMd2(m, ajuste).posicoes.get(0);
		// (x, y, z) do Quake vira (x, z, -y), vezes 0,1
		assertArrayEquals(new float[] { 0, 0, 0, 0, 0, -1, 1, 2, 0 }, p, 1e-5f);
	}

	@Test
	void agrupaOsQuadrosPeloNomeSemDigitos() {
		ArquivoMd2 m = LeitorMd2.ler(md2Minimo());
		List<Animacao> animacoes = new MalhaMd2(m, MalhaMd2.ajustePara(m, 2f)).animacoes;
		assertEquals(1, animacoes.size());
		assertEquals(new Animacao("stand", 0, 1, Animacao.FPS_PADRAO), animacoes.get(0));
	}

	@Test
	void agrupamentoComNomesForaDoPadrao() {
		List<Animacao> a = Animacao.agrupar(List.of("standdr001", "standdr002", "run000", "run001",
				"painaupp001", "painb001", "painb002", "dragon1"));
		assertEquals(List.of("standdr", "run", "painaupp", "painb", "dragon"), a.stream().map(Animacao::nome).toList());
		assertEquals(5, a.get(3).primeiro());
		assertEquals(6, a.get(3).ultimo());
	}

	@Test
	void normalDeFaceApontaParaFora() {
		// triângulo deitado (z = 5 no Quake), no sentido horário visto de cima, como o MD2 guarda
		float[] quake = { 0, 0, 5, 0, 10, 5, 10, 0, 5 };
		float[] p = MalhaMd2.converter(quake, new MalhaMd2.Ajuste(1, 0));
		float[] n = MalhaMd2.calcularNormais(p, new int[] { 0, 1, 2 }, 3);
		assertArrayEquals(new float[] { 0, 1, 0 }, new float[] { n[0], n[1], n[2] }, 1e-6f, "para cima");
	}
}
