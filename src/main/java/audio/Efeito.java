package audio;

/**
 * Um som curto já decodificado e guardado num buffer do OpenAL.
 *
 * @param buffer  identificador do buffer no OpenAL (0 quando não há áudio)
 * @param canais  1 = mono, 2 = estéreo (só sons mono podem ser posicionados em 3D)
 * @param nome    caminho ou nome, para mensagens de aviso
 */
public record Efeito(int buffer, int canais, String nome) {}
