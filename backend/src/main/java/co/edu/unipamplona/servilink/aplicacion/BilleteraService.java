package co.edu.unipamplona.servilink.aplicacion;

import co.edu.unipamplona.servilink.config.Configuracion;
import co.edu.unipamplona.servilink.dominio.Billetera;
import co.edu.unipamplona.servilink.dominio.Movimiento;
import co.edu.unipamplona.servilink.repositorio.BilleteraRepositorio;
import co.edu.unipamplona.servilink.repositorio.MovimientoRepositorio;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF-037, RF-042 y RF-043: billetera prepago con libro de movimientos. */
@Service
public class BilleteraService {

    public record Mov(Long id, String tipo, long valorCop, Instant fecha, Long solicitudId, long saldoPosteriorCop) {}

    public record Estado(long saldoDisponibleCop, long saldoRetenidoCop, List<Mov> movimientos) {}

    private final BilleteraRepositorio billeteras;
    private final MovimientoRepositorio libro;
    private final Configuracion config;
    private final Clock reloj;

    public BilleteraService(BilleteraRepositorio billeteras, MovimientoRepositorio libro, Configuracion config,
                            Clock reloj) {
        this.billeteras = billeteras;
        this.libro = libro;
        this.config = config;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public Estado consultar(Long tecnicoId) {
        Billetera b = billeteras.findById(tecnicoId).orElseThrow(() ->
                new ErrorNegocio(Codigo.NOT_FOUND, "El técnico no tiene billetera"));
        var movs = libro.findTop20ByTecnicoIdOrderByIdDesc(tecnicoId).stream()
                .map(m -> new Mov(m.getId(), m.getTipo(), m.getValor(), m.getCreadoEn(), m.getSolicitudId(),
                        m.getSaldoPosterior())).toList();
        return new Estado(b.getDisponible(), b.getRetenido(), movs);
    }

    /**
     * Sustituto de la recarga con Wompi (RF-038), solo para demostraciones. En producción el saldo debe
     * acreditarse únicamente con un evento firmado de Wompi, una sola vez por referencia.
     */
    @Transactional
    public Estado recargarDemo(Long tecnicoId, long valor) {
        if (!config.recargaDemoHabilitada()) {
            throw new ErrorNegocio(Codigo.NOT_FOUND, "La recarga de demostración está desactivada");
        }
        Billetera b = billeteras.bloquear(tecnicoId).orElseThrow(() ->
                new ErrorNegocio(Codigo.NOT_FOUND, "El técnico no tiene billetera"));
        b.recargar(valor);
        libro.save(new Movimiento(tecnicoId, null, "RECARGA", valor, b.getDisponible(),
                "REC-" + UUID.randomUUID(), reloj.instant()));
        return consultar(tecnicoId);
    }
}
