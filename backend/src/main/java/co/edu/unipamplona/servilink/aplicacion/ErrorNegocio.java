package co.edu.unipamplona.servilink.aplicacion;

import org.springframework.http.HttpStatus;

/** Violación de una regla de negocio; se traduce a {code, message, traceId}. */
public class ErrorNegocio extends RuntimeException {
    private final Codigo codigo;

    public ErrorNegocio(Codigo codigo, String detalle) {
        super(detalle);
        this.codigo = codigo;
    }

    public Codigo codigo() { return codigo; }
    public HttpStatus estado() { return codigo.estado(); }
}
