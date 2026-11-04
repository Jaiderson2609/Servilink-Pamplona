package co.edu.unipamplona.servilink.web;

import co.edu.unipamplona.servilink.aplicacion.ErrorNegocio;
import java.net.URI;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Todas las respuestas de error siguen RFC 9457 (application/problem+json). */
@RestControllerAdvice
public class ManejadorErrores {

    public static ProblemDetail problema(HttpStatus estado, String titulo, String detalle) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(estado, detalle);
        p.setTitle(titulo);
        p.setType(URI.create("https://servilink.co/errores/" + titulo.toLowerCase(Locale.ROOT).replace(' ', '-')));
        return p;
    }

    @ExceptionHandler(ErrorNegocio.class)
    ProblemDetail negocio(ErrorNegocio e) {
        return problema(e.estado(), e.titulo(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validacion(MethodArgumentNotValidException e) {
        String detalle = e.getBindingResult().getFieldErrors().stream().findFirst()
                .map(f -> f.getDefaultMessage()).orElse("Solicitud inválida");
        return problema(HttpStatus.BAD_REQUEST, "Solicitud invalida", detalle);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail ilegible(Exception e) {
        return problema(HttpStatus.BAD_REQUEST, "Solicitud invalida", "El cuerpo o un parámetro no es válido");
    }

    /** Red de seguridad: si una restricción de la base se viola, es un conflicto y no un error 500. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integridad(DataIntegrityViolationException e) {
        return problema(HttpStatus.CONFLICT, "Conflicto", "La operación choca con el estado actual de los datos");
    }
}
