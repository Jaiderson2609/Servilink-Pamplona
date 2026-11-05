package co.edu.unipamplona.servilink.aplicacion;

/** Resultado de una operación junto con el aviso que debe despacharse después del COMMIT (puede ser nulo). */
public record Efecto<T>(T valor, Long avisoId) {
    public static <T> Efecto<T> sinAviso(T valor) { return new Efecto<>(valor, null); }
}
