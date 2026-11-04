package co.edu.unipamplona.servilink.aplicacion;

import org.springframework.http.HttpStatus;

/** Violación de una regla de negocio; se traduce a una respuesta application/problem+json. */
public class ErrorNegocio extends RuntimeException {
    private final HttpStatus estado;
    private final String titulo;

    public ErrorNegocio(HttpStatus estado, String titulo, String detalle) {
        super(detalle);
        this.estado = estado;
        this.titulo = titulo;
    }

    public HttpStatus estado() { return estado; }
    public String titulo() { return titulo; }
}
