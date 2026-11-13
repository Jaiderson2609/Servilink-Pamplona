package co.edu.unipamplona.servilink.aplicacion;

import co.edu.unipamplona.servilink.dominio.Movimiento;
import co.edu.unipamplona.servilink.dominio.NotificacionPendiente;
import co.edu.unipamplona.servilink.dominio.Recarga;
import co.edu.unipamplona.servilink.repositorio.BilleteraRepositorio;
import co.edu.unipamplona.servilink.repositorio.MovimientoRepositorio;
import co.edu.unipamplona.servilink.repositorio.NotificacionRepositorio;
import co.edu.unipamplona.servilink.repositorio.RecargaRepositorio;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-038 (DAD CU-03, DDS UC-WEBHOOK): el saldo solo sube con un evento firmado y aprobado, una sola vez por
 * referencia. Crear la recarga no mueve dinero: deja un intento PENDING.
 */
@Service
public class RecargaService {

    private static final Logger LOG = LoggerFactory.getLogger(RecargaService.class);
    /** Tope de intentos por técnico y hora: evita llenar la tabla con recargas que nunca se pagan. */
    static final int MAX_INTENTOS_POR_HORA = 5;

    public record Intento(String referencia, long valorCop, String estado, PasarelaPago.Cobro cobro) {}

    public record Resultado(String estado, boolean acreditada) {}

    private final RecargaRepositorio recargas;
    private final BilleteraRepositorio billeteras;
    private final MovimientoRepositorio libro;
    private final NotificacionRepositorio avisos;
    private final PasarelaPago pasarela;
    private final Clock reloj;

    public RecargaService(RecargaRepositorio recargas, BilleteraRepositorio billeteras, MovimientoRepositorio libro,
                          NotificacionRepositorio avisos, PasarelaPago pasarela, Clock reloj) {
        this.recargas = recargas;
        this.billeteras = billeteras;
        this.libro = libro;
        this.avisos = avisos;
        this.pasarela = pasarela;
        this.reloj = reloj;
    }

    @Transactional
    public Intento crear(Long tecnicoId, long valorCop) {
        if (!pasarela.disponible()) {
            throw new ErrorNegocio(Codigo.PAYMENT_UNAVAILABLE, "Los pagos con Wompi no están disponibles por ahora");
        }
        Instant ahora = reloj.instant();
        if (recargas.countByTecnicoIdAndCreadaEnAfter(tecnicoId, ahora.minus(Duration.ofHours(1))) >= MAX_INTENTOS_POR_HORA) {
            throw new ErrorNegocio(Codigo.RATE_LIMITED, "Creaste muchos intentos de recarga seguidos. Intenta de nuevo en un rato");
        }
        // 3 + 36 caracteres: cabe en la columna de 40 y en la referencia única del libro contable.
        String referencia = "SL-" + UUID.randomUUID();
        recargas.save(new Recarga(tecnicoId, referencia, valorCop, ahora));
        return new Intento(referencia, valorCop, Recarga.PENDING, pasarela.iniciar(referencia, valorCop));
    }

    @Transactional(readOnly = true)
    public Intento consultar(Long tecnicoId, String referencia) {
        Recarga r = recargas.findByReferenciaAndTecnicoId(referencia, tecnicoId).orElseThrow(() ->
                new ErrorNegocio(Codigo.NOT_FOUND, "No existe esa recarga"));
        return new Intento(r.getReferencia(), r.getValor(), r.getEstado(), null);
    }

    /**
     * Aplica un evento ya verificado. Es idempotente: repetirlo, o recibirlo a la vez varias veces, deja un solo asiento.
     * El aviso al técnico se despacha después del COMMIT (lo hace quien llama).
     */
    @Transactional
    public Efecto<Resultado> aplicar(PasarelaPago.EventoPago e) {
        if (!"transaction.updated".equals(e.tipo())) {
            return Efecto.sinAviso(new Resultado("IGNORADO", false));
        }
        var encontrada = recargas.bloquearPorReferencia(e.referencia());
        if (encontrada.isEmpty()) {
            LOG.warn("Evento de Wompi para una referencia desconocida: {}", e.referencia());
            return Efecto.sinAviso(new Resultado("IGNORADO", false));
        }
        Recarga r = encontrada.get();
        if (!r.admiteResultado()) {
            return Efecto.sinAviso(new Resultado(r.getEstado(), false));
        }
        Instant ahora = reloj.instant();
        switch (e.estado()) {
            case "APPROVED" -> {
                return aprobar(r, e, ahora);
            }
            case "DECLINED", "ERROR", "VOIDED" -> {
                r.resolver(Recarga.FAILED, e.transaccionId(), ahora);
                return Efecto.sinAviso(new Resultado(Recarga.FAILED, false));
            }
            default -> { // PENDING u otro estado intermedio: se espera el definitivo
                return Efecto.sinAviso(new Resultado(r.getEstado(), false));
            }
        }
    }

    private Efecto<Resultado> aprobar(Recarga r, PasarelaPago.EventoPago e, Instant ahora) {
        boolean montoIgual = e.montoCentavos() == r.getValor() * 100L && "COP".equals(e.moneda());
        // Wompi no firma la referencia: una misma transacción jamás puede acreditar dos recargas.
        boolean transaccionRepetida = !e.transaccionId().isEmpty()
                && recargas.existsByTransaccionWompiAndReferenciaNot(e.transaccionId(), r.getReferencia());
        if (!montoIgual || transaccionRepetida || e.transaccionId().isEmpty()) {
            LOG.warn("Pago aprobado que no se acredita y queda en revisión: referencia={} transaccion={} monto={} moneda={}",
                    r.getReferencia(), e.transaccionId(), e.montoCentavos(), e.moneda());
            r.resolver(Recarga.REVIEW, transaccionRepetida ? null : e.transaccionId(), ahora);
            return Efecto.sinAviso(new Resultado(Recarga.REVIEW, false));
        }
        var billetera = billeteras.bloquear(r.getTecnicoId()).orElseThrow(() ->
                new ErrorNegocio(Codigo.NOT_FOUND, "El técnico no tiene billetera"));
        billetera.recargar(r.getValor());
        libro.save(new Movimiento(r.getTecnicoId(), null, "RECARGA", r.getValor(), billetera.getDisponible(),
                r.getReferencia(), ahora));
        r.resolver(Recarga.APPROVED, e.transaccionId(), ahora);
        Long aviso = avisos.save(new NotificacionPendiente("REC-" + r.getId(), r.getTecnicoId(),
                "Tu recarga de $ " + String.format("%,d", r.getValor()).replace(',', '.') + " fue acreditada", ahora)).getId();
        return new Efecto<>(new Resultado(Recarga.APPROVED, true), aviso);
    }
}
