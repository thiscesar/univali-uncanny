package fase;

/**
 * Uma placa: do evento (Computer on the Beach) ou de trilha (seta pintada com o nome de uma prova).
 * O giro é a rotação em volta do eixo vertical: a placa do evento mostra a frente (+z) na direção
 * (sen g, cos g); a seta da placa de trilha aponta para o +x do modelo, que girado fica em (cos g, -sen g).
 */
public record Placa(boolean evento, float x, float z, float giro, String texto) {}
