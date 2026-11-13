package co.edu.unipamplona.servilink.web;

import co.edu.unipamplona.servilink.aplicacion.Codigo;
import co.edu.unipamplona.servilink.aplicacion.ErrorNegocio;
import co.edu.unipamplona.servilink.aplicacion.Geocoder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/geocoding")
public class GeocodingController {

    static final int MIN = 3;
    static final int MAX = 200;

    private final Geocoder geocoder;

    public GeocodingController(Geocoder geocoder) {
        this.geocoder = geocoder;
    }

    /** Busca una dirección (solo cuando el usuario toca "Buscar"; el cliente no autocompleta mientras escribe). */
    @GetMapping("/search")
    public List<Map<String, Object>> buscar(@RequestParam(name = "q", required = false) String q) {
        String texto = q == null ? "" : q.trim();
        if (texto.length() < MIN || texto.length() > MAX) {
            throw new ErrorNegocio(Codigo.VALIDATION_ERROR,
                    "Escribe entre " + MIN + " y " + MAX + " caracteres para buscar una dirección");
        }
        return geocoder.buscar(texto).stream().map(l -> {
            var m = new LinkedHashMap<String, Object>();
            m.put("nombre", l.nombre());
            m.put("lat", l.lat());
            m.put("lng", l.lng());
            return (Map<String, Object>) m;
        }).toList();
    }
}
