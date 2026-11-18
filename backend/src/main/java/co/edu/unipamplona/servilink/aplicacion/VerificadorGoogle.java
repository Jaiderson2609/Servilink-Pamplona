package co.edu.unipamplona.servilink.aplicacion;

/** Puerto de salida: comprueba el ID token que entrega Google Identity Services y devuelve quién es la persona. */
public interface VerificadorGoogle {

    record Identidad(String correo, String nombre) {}

    /** Falso si no hay ID de cliente configurado: el ingreso con Google queda desactivado. */
    boolean disponible();

    /** ID de cliente público ({@code GOOGLE_CLIENT_ID}); vacío si no está configurado. */
    String clienteId();

    /**
     * @throws ErrorNegocio GOOGLE_UNAVAILABLE si no está configurado; UNAUTHENTICATED si el token no es válido,
     *                      no es para esta aplicación, venció o el correo no está verificado por Google.
     */
    Identidad verificar(String credencial);
}
