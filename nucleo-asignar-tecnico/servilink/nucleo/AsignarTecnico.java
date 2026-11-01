package servilink.nucleo;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Caso de uso: asignar un técnico a una solicitud (E02 a E03) y retener la comisión. */
public class AsignarTecnico implements AsignarTecnicoCasoDeUso {
    private static final BigDecimal COMISION = new BigDecimal("0.10");

    private final SolicitudRepository solicitudes;
    private final BilleteraRepository billetera;
    private final Notificador notificador;
    private final Reloj reloj;

    public AsignarTecnico(SolicitudRepository solicitudes, BilleteraRepository billetera,
                          Notificador notificador, Reloj reloj) {
        this.solicitudes = solicitudes; this.billetera = billetera;
        this.notificador = notificador; this.reloj = reloj;
    }

    @Override
    public Resultado ejecutar(String solicitudId, String tecnicoId) {
        Solicitud s = solicitudes.buscar(solicitudId)
                .orElseThrow(() -> new ReglaNegocioException("La solicitud no existe"));
        BigDecimal retencion = s.precioAcordado().multiply(COMISION).setScale(0, RoundingMode.HALF_UP);

        if (s.estado() == Estado.E03_ASIGNADA && tecnicoId.equals(s.tecnicoId())) {
            return new Resultado(s.id(), s.estado(), retencion, true);   // reintento: no retiene dos veces
        }
        if (s.estado() != Estado.E02_EN_SELECCION) {
            throw new ReglaNegocioException("Solo se puede asignar desde E02 (en selección)");
        }
        if (!reloj.ahora().isBefore(s.expiraEn())) {
            throw new ReglaNegocioException("La ventana de selección ya expiró");
        }
        if (billetera.saldo(tecnicoId).compareTo(retencion) < 0) {
            throw new ReglaNegocioException("Saldo insuficiente para retener la comisión");
        }
        billetera.retener(tecnicoId, retencion, "ASIG-" + s.id());
        s.asignarA(tecnicoId);
        solicitudes.guardar(s);
        notificador.asignacionConfirmada(s.hogarId(), tecnicoId, s.id());
        return new Resultado(s.id(), s.estado(), retencion, false);
    }
}
