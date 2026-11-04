package co.edu.unipamplona.servilink.web;

import jakarta.validation.constraints.NotNull;

public record AsignacionCrear(@NotNull(message = "postulacionId es obligatorio") Long postulacionId) {}
