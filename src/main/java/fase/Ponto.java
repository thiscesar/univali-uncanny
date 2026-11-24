package fase;

/** Um lugar no chão, em metros. */
public record Ponto(float x, float z) {

	public float distancia(Ponto p) {
		return (float) Math.hypot(x - p.x, z - p.z);
	}
}
