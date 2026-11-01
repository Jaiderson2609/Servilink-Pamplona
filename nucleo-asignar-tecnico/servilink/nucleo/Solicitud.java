package servilink.nucleo;

import java.math.BigDecimal;
import java.time.Instant;

/** Entidad del dominio: una solicitud de servicio publicada por un hogar. */
public class Solicitud {
    private final String id;
    private final String hogarId;
    private final BigDecimal precioAcordado;
    private final Instant expiraEn;
    private Estado estado;
    private String tecnicoId;

    public Solicitud(String id, String hogarId, BigDecimal precioAcordado, Instant expiraEn, Estado estado) {
        this.id = id; this.hogarId = hogarId; this.precioAcordado = precioAcordado;
        this.expiraEn = expiraEn; this.estado = estado;
    }

    public void asignarA(String tecnicoId) {
        this.tecnicoId = tecnicoId;
        this.estado = Estado.E03_ASIGNADA;
    }

    public String id() { return id; }
    public String hogarId() { return hogarId; }
    public BigDecimal precioAcordado() { return precioAcordado; }
    public Instant expiraEn() { return expiraEn; }
    public Estado estado() { return estado; }
    public String tecnicoId() { return tecnicoId; }
}
