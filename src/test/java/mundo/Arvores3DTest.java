package mundo;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class Arvores3DTest {

	// câmera em (100, 100) olhando para +x
	private static boolean ve(float x, float z) {
		return Arvores3D.visivel(x, z, 100, 100, 1, 0);
	}

	@Test
	void naFrenteDentroDoRaioEntra() {
		assertTrue(ve(150, 100));
		assertTrue(ve(140, 130));
	}

	@Test
	void longeDemaisNaoEntra() {
		assertFalse(ve(100 + Arvores3D.RAIO_DESENHO + 1, 100));
	}

	@Test
	void atrasSoSeEstiverPerto() {
		assertFalse(ve(70, 100));                       // 30 m atrás
		assertTrue(ve(90, 100));                        // 10 m atrás: a copa pode aparecer
		assertTrue(ve(100 - Arvores3D.MARGEM_ATRAS + 1, 140)); // ao lado, pouco atrás
	}

	@Test
	void olhandoParaCimaSoADistanciaConta() {
		assertTrue(Arvores3D.visivel(70, 100, 100, 100, 0, 0));
		assertFalse(Arvores3D.visivel(20, 100, 100, 100, 0, 0));
	}
}
