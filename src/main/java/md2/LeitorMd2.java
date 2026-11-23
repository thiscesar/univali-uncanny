package md2;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Lê arquivos MD2 (o formato de modelos animados do Quake 2). Não usa OpenGL: pode rodar
 * na thread de carregamento.
 *
 * O arquivo é binário e LITTLE-ENDIAN (o byte menos significativo vem primeiro). O Java lê
 * em big-endian por padrão: sem o ByteOrder.LITTLE_ENDIAN, o cabeçalho vira números absurdos.
 *
 * Cabeçalho: 17 inteiros de 4 bytes (ident "IDP2", versão 8, tamanho da skin, tamanho de cada
 * quadro, quantidades e as posições de cada bloco no arquivo). Depois:
 *  - coordenadas de textura: pares de short (s, t), em pixels;
 *  - triângulos: 3 índices de posição e 3 índices de textura, todos short;
 *  - quadros: escala[3] e translação[3] em float, nome de 16 bytes, e para cada vértice
 *    3 bytes de posição comprimida (posição real = byte * escala + translação) e 1 byte
 *    de índice de normal (ignorado: as normais são recalculadas pela geometria).
 */
public final class LeitorMd2 {

	public static final int IDENT = ('2' << 24) | ('P' << 16) | ('D' << 8) | 'I'; // "IDP2" lido em little-endian
	public static final int VERSAO = 8;

	private LeitorMd2() {}

	public static ArquivoMd2 ler(File arquivo) {
		try {
			return ler(Files.readAllBytes(arquivo.toPath()));
		} catch (IOException e) {
			throw new UncheckedIOException("não foi possível ler " + arquivo, e);
		}
	}

	public static ArquivoMd2 ler(byte[] bytes) {
		if (bytes.length < 68) {
			throw new IllegalArgumentException("arquivo pequeno demais para ser um MD2 (" + bytes.length + " bytes)");
		}
		ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

		int ident = b.getInt();
		int versao = b.getInt();
		if (ident != IDENT) {
			throw new IllegalArgumentException("não é um MD2: a assinatura deveria ser IDP2");
		}
		if (versao != VERSAO) {
			throw new IllegalArgumentException("versão de MD2 não suportada: " + versao + " (esperada " + VERSAO + ")");
		}
		int larguraSkin = b.getInt();
		int alturaSkin = b.getInt();
		int tamanhoQuadro = b.getInt();
		b.getInt();                        // num_skins (os caminhos das skins são ignorados)
		int numeroPosicoes = b.getInt();   // num_xyz
		int numeroSt = b.getInt();         // num_st
		int numeroTriangulos = b.getInt(); // num_tris
		b.getInt();                        // num_glcmds (não usados)
		int numeroQuadros = b.getInt();    // num_frames
		b.getInt();                        // ofs_skins
		int posSt = b.getInt();            // ofs_st
		int posTriangulos = b.getInt();    // ofs_tris
		int posQuadros = b.getInt();       // ofs_frames (vem ANTES de ofs_glcmds no cabeçalho)
		b.getInt();                        // ofs_glcmds
		b.getInt();                        // ofs_end

		// coordenadas de textura
		short[] s = new short[numeroSt];
		short[] t = new short[numeroSt];
		b.position(posSt);
		for (int i = 0; i < numeroSt; i++) {
			s[i] = b.getShort();
			t[i] = b.getShort();
		}

		// triângulos
		int[] vertices = new int[numeroTriangulos * 3];
		int[] textura = new int[numeroTriangulos * 3];
		b.position(posTriangulos);
		for (int i = 0; i < numeroTriangulos; i++) {
			for (int k = 0; k < 3; k++) vertices[i * 3 + k] = b.getShort() & 0xFFFF;
			for (int k = 0; k < 3; k++) textura[i * 3 + k] = b.getShort() & 0xFFFF;
		}

		// quadros
		List<ArquivoMd2.Quadro> quadros = new ArrayList<>(numeroQuadros);
		for (int q = 0; q < numeroQuadros; q++) {
			b.position(posQuadros + q * tamanhoQuadro);
			float ex = b.getFloat(), ey = b.getFloat(), ez = b.getFloat();
			float tx = b.getFloat(), ty = b.getFloat(), tz = b.getFloat();
			byte[] nome = new byte[16];
			b.get(nome);
			float[] posicoes = new float[numeroPosicoes * 3];
			for (int v = 0; v < numeroPosicoes; v++) {
				posicoes[v * 3] = (b.get() & 0xFF) * ex + tx;
				posicoes[v * 3 + 1] = (b.get() & 0xFF) * ey + ty;
				posicoes[v * 3 + 2] = (b.get() & 0xFF) * ez + tz;
				b.get(); // índice da normal na tabela do Quake: ignorado
			}
			quadros.add(new ArquivoMd2.Quadro(textoC(nome), posicoes));
		}
		return new ArquivoMd2(larguraSkin, alturaSkin, numeroPosicoes, s, t, vertices, textura, quadros);
	}

	/** Texto terminado em zero (como em C). */
	private static String textoC(byte[] bytes) {
		int fim = 0;
		while (fim < bytes.length && bytes[fim] != 0) fim++;
		return new String(bytes, 0, fim, StandardCharsets.US_ASCII);
	}
}
