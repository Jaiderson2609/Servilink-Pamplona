package co.edu.unipamplona.servilink.aplicacion;

import co.edu.unipamplona.servilink.dominio.Solicitud;

/** Quién es quién en una solicitud: el hogar dueño o el técnico asignado. */
final class Participacion {
    private Participacion() {}

    static boolean esParte(Solicitud s, Long usuarioId) {
        return s.getHogarId().equals(usuarioId) || usuarioId.equals(s.getTecnicoId());
    }

    static void exigirParte(Solicitud s, Long usuarioId) {
        if (!esParte(s, usuarioId)) {
            throw new ErrorNegocio(Codigo.FORBIDDEN, "No participas en este servicio");
        }
    }

    /** La contraparte: el técnico si quien consulta es el hogar, y al revés. */
    static Long otraParte(Solicitud s, Long usuarioId) {
        return s.getHogarId().equals(usuarioId) ? s.getTecnicoId() : s.getHogarId();
    }
}
