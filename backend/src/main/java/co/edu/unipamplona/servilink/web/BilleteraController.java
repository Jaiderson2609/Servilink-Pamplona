package co.edu.unipamplona.servilink.web;

import co.edu.unipamplona.servilink.aplicacion.BilleteraService;
import co.edu.unipamplona.servilink.dominio.Oficio;
import co.edu.unipamplona.servilink.repositorio.OficioRepositorio;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class BilleteraController {

    private final BilleteraService billetera;
    private final OficioRepositorio oficios;

    public BilleteraController(BilleteraService billetera, OficioRepositorio oficios) {
        this.billetera = billetera;
        this.oficios = oficios;
    }

    @GetMapping("/billetera")
    public Map<String, Object> consultar(@AuthenticationPrincipal Jwt jwt) {
        return forma(billetera.consultar(Long.valueOf(jwt.getSubject())));
    }

    /** Sustituto de Wompi para demostraciones (ver BilleteraService.recargarDemo). */
    @PostMapping("/billetera/recargas")
    public Map<String, Object> recargar(@Valid @RequestBody Peticiones.Recarga r, @AuthenticationPrincipal Jwt jwt) {
        return forma(billetera.recargarDemo(Long.valueOf(jwt.getSubject()), r.valorCop()));
    }

    @GetMapping("/oficios")
    public List<Map<String, Object>> oficios() {
        return oficios.findAll().stream().sorted((a, b) -> a.getId() - b.getId())
                .map((Oficio o) -> Map.<String, Object>of("id", o.getId(), "nombre", o.getNombre())).toList();
    }

    private static Map<String, Object> forma(BilleteraService.Estado e) {
        return Map.of("saldoDisponibleCop", e.saldoDisponibleCop(), "saldoRetenidoCop", e.saldoRetenidoCop(),
                "movimientos", e.movimientos().stream().map(m -> Map.<String, Object>of("id", m.id(), "tipo", m.tipo(),
                        "valorCop", m.valorCop(), "fecha", m.fecha().toString(), "saldoPosteriorCop",
                        m.saldoPosteriorCop())).toList());
    }
}
