package co.edu.unipamplona.servilink.web;

import co.edu.unipamplona.servilink.aplicacion.Codigo;
import co.edu.unipamplona.servilink.aplicacion.ErrorNegocio;
import co.edu.unipamplona.servilink.aplicacion.NotificacionService;
import co.edu.unipamplona.servilink.aplicacion.PasarelaPago;
import co.edu.unipamplona.servilink.aplicacion.RecargaService;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

/**
 * Recibe los eventos de Wompi. No lleva JWT: la autenticidad la da la firma del evento. Responde 200 si se procesó o
 * ya estaba procesado (Wompi reintenta mientras no reciba 200) y 401 INVALID_SIGNATURE si la firma no coincide.
 */
@RestController
@RequestMapping("/api/v1/webhooks")
public class WompiController {

    private static final Logger LOG = LoggerFactory.getLogger(WompiController.class);

    private final PasarelaPago pasarela;
    private final RecargaService recargas;
    private final NotificacionService notificaciones;

    public WompiController(PasarelaPago pasarela, RecargaService recargas, NotificacionService notificaciones) {
        this.pasarela = pasarela;
        this.recargas = recargas;
        this.notificaciones = notificaciones;
    }

    @PostMapping("/wompi")
    public Map<String, Object> evento(@RequestBody String cuerpo,
                                      @RequestHeader(name = "X-Event-Checksum", required = false) String checksum) {
        PasarelaPago.EventoPago evento;
        try {
            evento = pasarela.verificar(cuerpo, checksum);
        } catch (ErrorNegocio e) {
            if (e.codigo() == Codigo.INVALID_SIGNATURE) {
                // Auditoría del intento: el registro estructurado lleva traceId; no se guarda el cuerpo recibido.
                LOG.warn("Evento de Wompi rechazado por firma inválida: {}", e.getMessage());
            }
            throw e;
        }
        var efecto = recargas.aplicar(evento);
        if (efecto.avisoId() != null) {
            notificaciones.despachar(efecto.avisoId());
        }
        return Map.of("estado", efecto.valor().estado());
    }
}
