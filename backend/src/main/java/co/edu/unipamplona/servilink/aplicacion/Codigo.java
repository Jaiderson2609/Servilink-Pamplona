package co.edu.unipamplona.servilink.aplicacion;

import org.springframework.http.HttpStatus;

/** Catálogo de códigos de error estables del API (DDS 11.1). El estado HTTP depende solo del código. */
public enum Codigo {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    ACTIVE_REQUEST_EXISTS(HttpStatus.CONFLICT),
    DUPLICATE_APPLICATION(HttpStatus.CONFLICT),
    INVALID_STATE_TRANSITION(HttpStatus.CONFLICT),
    NEGOTIATION_LIMIT_REACHED(HttpStatus.CONFLICT),
    TECHNICIAN_UNAVAILABLE(HttpStatus.CONFLICT),
    SERVICE_EXPIRED(HttpStatus.GONE),
    WALLET_BELOW_MINIMUM(HttpStatus.UNPROCESSABLE_ENTITY),
    INSUFFICIENT_BALANCE(HttpStatus.UNPROCESSABLE_ENTITY),
    LOCATION_REQUIRED(HttpStatus.UNPROCESSABLE_ENTITY),
    OUT_OF_RADIUS(HttpStatus.UNPROCESSABLE_ENTITY),
    CHAT_CLOSED(HttpStatus.UNPROCESSABLE_ENTITY),
    INVALID_SIGNATURE(HttpStatus.UNAUTHORIZED),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
    // Códigos adicionales a los del catálogo, necesarios para reglas que el DDS no enumera.
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT),
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN),
    OPERATION_NOT_ALLOWED(HttpStatus.CONFLICT),
    PHOTO_LIMIT_REACHED(HttpStatus.CONFLICT),
    DUPLICATE_RATING(HttpStatus.CONFLICT),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    GEOCODING_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    PAYMENT_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    CONFLICT(HttpStatus.CONFLICT);

    private final HttpStatus estado;

    Codigo(HttpStatus estado) { this.estado = estado; }

    public HttpStatus estado() { return estado; }
}
