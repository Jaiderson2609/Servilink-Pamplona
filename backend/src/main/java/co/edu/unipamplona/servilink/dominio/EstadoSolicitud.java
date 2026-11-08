package co.edu.unipamplona.servilink.dominio;

import java.util.Arrays;
import java.util.List;

/**
 * Catálogo único de estados de una solicitud (E01 a E07). Es la fuente de verdad del código:
 * ningún otro archivo debe escribir estos códigos como texto suelto. La columna de la base de datos
 * sigue siendo texto, así que el nombre de cada constante es el código que se guarda.
 *
 * E01 abierta, E02 con postulaciones, E03 asignada, E04 en ejecución, E05 finalizada,
 * E06 vencida y E07 cancelada.
 */
public enum EstadoSolicitud {
    E01, E02, E03, E04, E05, E06, E07;

    /** Código tal como se guarda en la base de datos. */
    public String codigo() {
        return name();
    }

    /** Indica si el código recibido (por ejemplo, el de una entidad) corresponde a este estado. */
    public boolean es(String codigo) {
        return name().equals(codigo);
    }

    public static boolean esUnoDe(String codigo, EstadoSolicitud... estados) {
        return Arrays.stream(estados).anyMatch(e -> e.es(codigo));
    }

    public static List<String> codigos(EstadoSolicitud... estados) {
        return Arrays.stream(estados).map(EstadoSolicitud::codigo).toList();
    }

    public static boolean existe(String codigo) {
        return Arrays.stream(values()).anyMatch(e -> e.es(codigo));
    }
}
