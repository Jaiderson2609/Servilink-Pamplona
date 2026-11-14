package co.edu.unipamplona.servilink.web;

import co.edu.unipamplona.servilink.aplicacion.Codigo;
import co.edu.unipamplona.servilink.aplicacion.ErrorNegocio;
import co.edu.unipamplona.servilink.aplicacion.VerificacionService;
import co.edu.unipamplona.servilink.aplicacion.ZonaTrabajoService;
import co.edu.unipamplona.servilink.dominio.UbicacionTecnico;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/technicians/me")
public class TecnicoController {

    private final ZonaTrabajoService zona;
    private final VerificacionService verificacion;

    public TecnicoController(ZonaTrabajoService zona, VerificacionService verificacion) {
        this.zona = zona;
        this.verificacion = verificacion;
    }

    /** Cédula por ambos lados y selfie, en una sola petición. Quedan privadas hasta que el administrador decide. */
    @PostMapping(path = "/verification", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public VerificacionService.Estado enviarVerificacion(
            @RequestParam(value = "cedulaFrente", required = false) org.springframework.web.multipart.MultipartFile frente,
            @RequestParam(value = "cedulaReverso", required = false) org.springframework.web.multipart.MultipartFile reverso,
            @RequestParam(value = "selfie", required = false) org.springframework.web.multipart.MultipartFile selfie,
            @AuthenticationPrincipal Jwt jwt) throws java.io.IOException {
        var archivos = new java.util.HashMap<String, byte[]>();
        archivos.put("CEDULA_FRENTE", frente == null ? null : frente.getBytes());
        archivos.put("CEDULA_REVERSO", reverso == null ? null : reverso.getBytes());
        archivos.put("SELFIE", selfie == null ? null : selfie.getBytes());
        return verificacion.enviar(Long.valueOf(jwt.getSubject()), archivos);
    }

    @GetMapping("/verification")
    public VerificacionService.Estado estadoVerificacion(@AuthenticationPrincipal Jwt jwt) {
        return verificacion.estado(Long.valueOf(jwt.getSubject()));
    }

    /** Define o cambia la zona de trabajo; con ella, "solicitudes abiertas" filtra por radio y ordena por cercanía. */
    @PutMapping("/location")
    public Map<String, Object> guardar(@Valid @RequestBody Peticiones.ZonaTrabajo z, @AuthenticationPrincipal Jwt jwt) {
        return forma(zona.guardar(Long.valueOf(jwt.getSubject()), z.lat(), z.lng(), z.radioKm()));
    }

    @GetMapping("/location")
    public Map<String, Object> consultar(@AuthenticationPrincipal Jwt jwt) {
        return zona.consultar(Long.valueOf(jwt.getSubject())).map(TecnicoController::forma)
                .orElseThrow(() -> new ErrorNegocio(Codigo.NOT_FOUND, "Aún no defines tu zona de trabajo"));
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
