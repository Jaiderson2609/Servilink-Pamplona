package co.edu.unipamplona.servilink.dominio;

import jakarta.persistence.*;

@Entity
@Table(name = "billeteras")
public class Billetera {
    @Id
    @Column(name = "tecnico_id")
    private Long tecnicoId;
    private long disponible;
    private long retenido;

    protected Billetera() {}

    public Billetera(Long tecnicoId, long disponible) {
        this.tecnicoId = tecnicoId;
        this.disponible = disponible;
    }

    public Long getTecnicoId() { return tecnicoId; }
    public long getDisponible() { return disponible; }
    public long getRetenido() { return retenido; }

    /** RF-041: la comisión se retiene al asignar; el saldo disponible nunca baja de cero. */
    public void retener(long valor) {
        if (disponible < valor) {
            throw new IllegalStateException("Saldo insuficiente");
        }
        disponible -= valor;
        retenido += valor;
    }

    /** RF-041: se libera si el servicio se cancela antes de finalizar. */
    public void liberar(long valor) {
        retenido -= valor;
        disponible += valor;
    }

    /** RF-041: al finalizar, la retención se descuenta definitivamente. */
    public void cobrar(long valor) {
        retenido -= valor;
    }

    public void recargar(long valor) {
        disponible += valor;
    }
}
