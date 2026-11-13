package co.edu.unipamplona.servilink.web;

import co.edu.unipamplona.servilink.aplicacion.BilleteraService;
import co.edu.unipamplona.servilink.aplicacion.NotificacionService;
import co.edu.unipamplona.servilink.aplicacion.PasarelaPago;
import co.edu.unipamplona.servilink.aplicacion.RecargaService;
import co.edu.unipamplona.servilink.config.Configuracion;
import co.edu.unipamplona.servilink.dominio.Oficio;
import co.edu.unipamplona.servilink.repositorio.OficioRepositorio;
import jakarta.validation.Valid;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class BilleteraController {

    private final BilleteraService billetera;
    private final RecargaService recargas;
    private final PasarelaPago pasarela;
    private final Configuracion config;
    private final OficioRepositorio oficios;

    public BilleteraController(BilleteraService billetera, RecargaService recargas, PasarelaPago pasarela,
                               Configuracion config, OficioRepositorio oficios) {
        this.billetera = billetera;
        this.recargas = recargas;
        this.pasarela = pasarela;
        this.config = config;
        this.oficios = oficios;
    }

    @GetMapping("/wallet")
    public Map<String, Object> consultar(@AuthenticationPrincipal Jwt jwt) {
        var m = new LinkedHashMap<>(forma(billetera.consultar(Long.valueOf(jwt.getSubject()))));
        m.put("pagoDisponible", pasarela.disponible());
        m.put("recargaDemoDisponible", config.recargaDemoHabilitada());
        return m;
    }

    /** Crea el intento de recarga (PENDING) y devuelve la dirección del pago en Wompi. No mueve dinero. */
    @PostMapping("/wallet/topups")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> iniciarRecarga(@Valid @RequestBody Peticiones.Recarga r, @AuthenticationPrincipal Jwt jwt) {
        var i = recargas.crear(Long.valueOf(jwt.getSubject()), r.valorCop());
        var m = new LinkedHashMap<String, Object>();
        m.put("referencia", i.referencia());
        m.put("valorCop", i.valorCop());
        m.put("estado", i.estado());
        m.put("urlPago", i.cobro().urlPago());
        return m;
    }

    /** Estado de una recarga propia: la pantalla a la que vuelve el técnico después de pagar lo consulta. */
    @GetMapping("/wallet/topups/{referencia}")
    public Map<String, Object> estadoRecarga(@PathVariable String referencia, @AuthenticationPrincipal Jwt jwt) {
        var i = recargas.consultar(Long.valueOf(jwt.getSubject()), referencia);
        return Map.of("referencia", i.referencia(), "valorCop", i.valorCop(), "estado", i.estado());
    }

    /** Sustituto de Wompi solo para demostraciones (ver BilleteraService.recargarDemo). */
    @PostMapping("/wallet/topups/demo")
    public Map<String, Object> recargarDemo(@Valid @RequestBody Peticiones.Recarga r, @AuthenticationPrincipal Jwt jwt) {
        return forma(billetera.recargarDemo(Long.valueOf(jwt.getSubject()), r.valorCop()));
    }

    @GetMapping("/trades")
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
