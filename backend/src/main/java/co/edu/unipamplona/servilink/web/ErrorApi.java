package co.edu.unipamplona.servilink.web;

/** Cuerpo de toda respuesta de error: código estable, mensaje en español y traza para soporte. */
public record ErrorApi(String code, String message, String traceId) { }
