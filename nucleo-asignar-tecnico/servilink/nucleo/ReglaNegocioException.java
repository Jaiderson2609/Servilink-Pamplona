package servilink.nucleo;

/** Error de negocio propio del núcleo (no depende de ningún framework). */
public class ReglaNegocioException extends RuntimeException {
    public ReglaNegocioException(String mensaje) { super(mensaje); }
}
