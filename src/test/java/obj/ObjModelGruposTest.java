package obj;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ObjModelGruposTest {

	private static Face3D face(int vertices) {
		Face3D f = new Face3D();
		f.nvertices = (byte) vertices;
		return f;
	}

	@Test
	void grupoOcupaOTrechoCertoDoVbo() {
		// corpo: 2 triângulos e 1 quadrilátero (3 + 3 + 6 = 12 vértices); telha: 1 quadrilátero (6)
		List<Face3D> faces = new ArrayList<>(List.of(face(3), face(3), face(4), face(4)));
		assertArrayEquals(new int[] { 0, 12 }, ObjModelGrupos.faixa(faces, 0, 2));
		assertArrayEquals(new int[] { 12, 6 }, ObjModelGrupos.faixa(faces, 3, 3));
	}
}
