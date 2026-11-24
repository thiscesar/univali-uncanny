package render;

/**
 * Um modelo de arma desenhado por partes ("grupos"): o corpo parado e a telha, que anda.
 * O OBJ baixado (obj.ObjModelGrupos) e a escopeta montada por código (MalhaEscopeta)
 * seguem o mesmo contrato, e o desenho da arma não precisa saber qual está em uso.
 */
public interface ModeloArma {

	/** Desenha só o grupo com esse nome (o shader e as matrizes já estão prontos). */
	void desenharGrupo(String nome);

	boolean temGrupo(String nome);

	void liberar();
}
