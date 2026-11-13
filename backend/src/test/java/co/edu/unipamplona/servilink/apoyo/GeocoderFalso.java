package co.edu.unipamplona.servilink.apoyo;

import co.edu.unipamplona.servilink.aplicacion.Geocoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Doble de Nominatim para las pruebas de integración: ninguna prueba sale a internet. */
public class GeocoderFalso implements Geocoder {
    public final List<String> consultas = new CopyOnWriteArrayList<>();
    public volatile List<Lugar> respuesta = new ArrayList<>();
    public volatile RuntimeException fallo;

    @Override
    public List<Lugar> buscar(String consulta) {
        consultas.add(consulta);
        if (fallo != null) {
            throw fallo;
        }
        return respuesta;
    }

    public void reiniciar() {
        consultas.clear();
        respuesta = new ArrayList<>();
        fallo = null;
    }
}
