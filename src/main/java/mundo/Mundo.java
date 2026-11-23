package mundo;

import obj.Mapa3D;
import terreno.PerfilTerreno;

/**
 * Tudo o que forma o lugar da partida: o terreno (malha do OpenGL), as perguntas sobre
 * o chão e a grade de colisão (vazia até existirem árvores). É o que a tela de
 * carregamento entrega pronto para a tela de jogo.
 */
public class Mundo {

	public final long seed;
	public final PerfilTerreno perfil;
	/** O terreno desenhável, do projeto original (Mapa3D com a malha ObjHTGsrtm). */
	public final Mapa3D terreno;
	public final ConsultaAltura chao;
	public final GradeColisao colisao;

	public Mundo(long seed, PerfilTerreno perfil, Mapa3D terreno, ConsultaAltura chao) {
		this.seed = seed;
		this.perfil = perfil;
		this.terreno = terreno;
		this.chao = chao;
		this.colisao = new GradeColisao(chao.tamanho());
	}

	/** Lado do mapa em metros. */
	public float tamanho() {
		return chao.tamanho();
	}

	/** Apaga da placa de vídeo o que este mundo usa. Só na thread principal. */
	public void liberar() {
		terreno.model.liberar();
	}
}
