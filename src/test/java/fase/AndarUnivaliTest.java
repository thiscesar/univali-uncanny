package fase;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import fase.AndarUnivali.Sala;
import fase.AndarUnivali.Tipo;

class AndarUnivaliTest {

	@Test
	void dozeSalasComUmaDeCadaTipoEspecial() {
		for (long seed = 1; seed <= 30; seed++) {
			AndarUnivali a = new AndarUnivali(seed);
			List<Sala> s = a.salas();
			assertEquals(12, s.size());
			assertEquals(6, s.stream().filter(Sala::naFrente).count());
			for (Tipo t : List.of(Tipo.PROFESSORES, Tipo.BANHEIROS, Tipo.LABORATORIO, Tipo.COORDENACAO)) {
				assertEquals(1, s.stream().filter(x -> x.tipo() == t).count(), "seed " + seed + ": " + t);
			}
			assertEquals(3, s.stream().filter(Sala::trancada).count(), "os professores e mais duas");
		}
	}

	@Test
	void professoresNumCantoDosFundosEInicioDoOutroLado() {
		for (long seed = 1; seed <= 30; seed++) {
			AndarUnivali a = new AndarUnivali(seed);
			Sala p = a.professores(), i = a.inicio();
			assertFalse(p.naFrente());
			assertTrue(p.trancada());
			assertTrue(p.xMin() == AndarUnivali.X0 || p.xMax() == -AndarUnivali.X0, "num canto");
			assertTrue(i.naFrente());
			assertEquals(Tipo.AULA, i.tipo());
			assertFalse(i.trancada());
			assertTrue(Math.signum(p.centro()[0]) != Math.signum(i.centro()[0]), "lados opostos do corredor");
		}
	}

	@Test
	void crachaNumaSalaAbertaQueNaoEhOInicio() {
		for (long seed = 1; seed <= 30; seed++) {
			AndarUnivali a = new AndarUnivali(seed);
			Sala c = a.cracha();
			assertFalse(c.trancada());
			assertNotSame(a.inicio(), c);
			assertNotEquals(Tipo.BANHEIROS, c.tipo());
			assertNotEquals(Tipo.PROFESSORES, c.tipo());
		}
	}

	@Test
	void todaSalaTemOVaoDaPortaLivreNaParede() {
		AndarUnivali a = new AndarUnivali(4);
		for (Sala s : a.salas()) {
			float[] p = s.porta();
			for (AndarUnivali.Caixa w : a.paredes()) {
				boolean dentro = p[0] > w.xMin() && p[0] < w.xMax() && p[1] >= w.zMin() && p[1] <= w.zMax();
				assertFalse(dentro, s.nome() + ": a porta está dentro de uma parede");
			}
		}
	}

	@Test
	void noCorredorNuncaDuasLampadasQueimadasSeguidas() {
		for (long seed = 1; seed <= 30; seed++) {
			List<AndarUnivali.Lampada> corredor = new AndarUnivali(seed).lampadas().stream().filter(l -> l.z() == 0).toList();
			assertEquals(12, corredor.size());
			for (int i = 1; i < corredor.size(); i++) {
				assertFalse(corredor.get(i).estado() == AndarUnivali.Lampada.Estado.QUEIMADA
						&& corredor.get(i - 1).estado() == AndarUnivali.Lampada.Estado.QUEIMADA, "seed " + seed);
			}
		}
	}

	@Test
	void paredesDentroDoPredioEMesmaSeedMesmoAndar() {
		AndarUnivali a = new AndarUnivali(9), b = new AndarUnivali(9);
		assertFalse(a.paredes().isEmpty());
		assertEquals(a.paredes(), b.paredes());
		assertEquals(a.salas(), b.salas());
		for (AndarUnivali.Caixa c : a.paredes()) {
			assertTrue(c.xMin() >= AndarUnivali.X0 - 0.2f && c.xMax() <= -AndarUnivali.X0 + 0.2f);
			assertTrue(c.zMin() >= AndarUnivali.Z0 - 0.2f && c.zMax() <= -AndarUnivali.Z0 + 0.2f);
		}
	}
}
