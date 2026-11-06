package co.edu.unipamplona.servilink.web;

import co.edu.unipamplona.servilink.aplicacion.Codigo;
import co.edu.unipamplona.servilink.aplicacion.ErrorNegocio;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Todas las respuestas de error tienen la forma {code, message, traceId} (DDS 11.1). */
@RestControllerAdvice
public class ManejadorErrores extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ManejadorErrores.class);

    public static ResponseEntity<ErrorApi> responder(Codigo codigo, String mensaje) {
        return ResponseEntity.status(codigo.estado()).contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorApi(codigo.name(), mensaje, UUID.randomUUID().toString()));
    }

    @ExceptionHandler(ErrorNegocio.class)
    ResponseEntity<ErrorApi> negocio(ErrorNegocio e) {
        return responder(e.codigo(), e.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorApi> denegado(AccessDeniedException e) {
        return responder(Codigo.FORBIDDEN, "No tienes permiso para esta operación");
    }

    /** Red de seguridad: si una restricción de la base se viola, es un conflicto y no un error 500. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorApi> integridad(DataIntegrityViolationException e) {
        return responder(Codigo.CONFLICT, "La operación choca con el estado actual de los datos");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorApi> inesperado(Exception e) {
        String traza = UUID.randomUUID().toString();
        LOG.error("Error no controlado traceId={}", traza, e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorApi(Codigo.INTERNAL_ERROR.name(), "Ocurrió un error inesperado", traza));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
            HttpHeaders headers, HttpStatusCode estado, WebRequest request) {
        String detalle = e.getBindingResult().getFieldErrors().stream().findFirst()
                .map(f -> f.getDefaultMessage()).orElse("Solicitud inválida");
        return cuerpo(Codigo.VALIDATION_ERROR, detalle);
    }

    /** Errores del propio Spring MVC (cuerpo ilegible, parámetro faltante, ruta o método inexistente...). */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception e, Object body, HttpHeaders headers,
            HttpStatusCode estado, WebRequest request) {
        Codigo c = switch (estado.value()) {
            case 404 -> Codigo.NOT_FOUND;
            case 413 -> Codigo.PAYLOAD_TOO_LARGE;
            case 415 -> Codigo.UNSUPPORTED_MEDIA_TYPE;
            case 500 -> Codigo.INTERNAL_ERROR;
            default -> Codigo.VALIDATION_ERROR;
        };
        String mensaje = switch (c) {
            case NOT_FOUND -> "El recurso no existe";
            case INTERNAL_ERROR -> "Ocurrió un error inesperado";
            case PAYLOAD_TOO_LARGE -> "Cada foto puede pesar hasta 2 MB";
            case UNSUPPORTED_MEDIA_TYPE -> "El tipo de contenido no es compatible";
            default -> "El cuerpo o un parámetro no es válido";
        };
        ResponseEntity<Object> r = cuerpo(c, mensaje);
        // Se conserva el estado que decidió Spring (405, 406...) aunque el código sea genérico.
        return ResponseEntity.status(estado).headers(headers).contentType(MediaType.APPLICATION_JSON).body(r.getBody());
    }

    private static ResponseEntity<Object> cuerpo(Codigo c, String mensaje) {
        return ResponseEntity.status(c.estado()).contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorApi(c.name(), mensaje, UUID.randomUUID().toString()));
    }
}
