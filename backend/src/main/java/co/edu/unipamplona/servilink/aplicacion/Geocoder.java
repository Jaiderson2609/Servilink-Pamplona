package co.edu.unipamplona.servilink.aplicacion;

import java.util.List;

/** Puerto de salida: busca una dirección escrita y devuelve posibles lugares con sus coordenadas (DDS 4.2). */
public interface Geocoder {

    record Lugar(String nombre, double lat, double lng) {}

    /**
     * @throws ErrorNegocio RATE_LIMITED si hay demasiadas búsquedas en espera; GEOCODING_UNAVAILABLE si el
     *                      proveedor no responde o responde algo inesperado.
     */
    List<Lugar> buscar(String consulta);
}
