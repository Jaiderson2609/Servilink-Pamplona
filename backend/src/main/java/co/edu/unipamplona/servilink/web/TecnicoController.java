package co.edu.unipamplona.servilink.web;

import co.edu.unipamplona.servilink.aplicacion.ErrorNegocio;
import co.edu.unipamplona.servilink.aplicacion.ZonaTrabajoService;
import co.edu.unipamplona.servilink.dominio.UbicacionTecnico;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tecnicos/yo")
public class TecnicoController {

    private final ZonaTrabajoService zona;

    public TecnicoController(ZonaTrabajoService zona) {
        this.zona = zona;
    }

    /** Define o cambia la zona de trabajo; con ella, "solicitudes abiertas" filtra por radio y ordena por cercanía. */
    @PutMapping("/ubicacion")
    public Map<String, Object> guardar(@Valid @RequestBody Peticiones.ZonaTrabajo z, @AuthenticationPrincipal Jwt jwt) {
        return forma(zona.guardar(Long.valueOf(jwt.getSubject()), z.lat(), z.lng(), z.radioKm()));
    }

    @GetMapping("/ubicacion")
    public Map<String, Object> consultar(@AuthenticationPrincipal Jwt jwt) {
        return zona.consultar(Long.valueOf(jwt.getSubject())).map(TecnicoController::forma)
                .orElseThrow(() -> new ErrorNegocio(HttpStatus.NOT_FOUND, "No encontrado", "Aún no defines tu zona de trabajo"));
    }

    private static Map<String, Object> forma(UbicacionTecnico u) {
        var m = new LinkedHashMap<String, Object>();
        m.put("lat", u.getLat());
        m.put("lng", u.getLng());
        m.put("radioKm", u.getRadioKm());
        m.put("actualizadaEn", u.getActualizadaEn());
        return m;
    }
}
