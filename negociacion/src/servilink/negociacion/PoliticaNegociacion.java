package servilink.negociacion;

/** Reglas de la negociación: hasta 2 ofertas del técnico y 1 contraoferta del hogar. */
public class PoliticaNegociacion {
    static final int MAX_OFERTAS_TECNICO = 2;
    static final int MAX_CONTRAOFERTAS_HOGAR = 1;

    public boolean puedeOfertarTecnico(int ofertasPrevias, Estado estado) {
        return admiteNegociacion(estado) && ofertasPrevias < MAX_OFERTAS_TECNICO;
    }

    public boolean puedeContraofertarHogar(int contraofertasPrevias, int ofertasTecnico, Estado estado) {
        return admiteNegociacion(estado)
                && ofertasTecnico >= 1
                && contraofertasPrevias < MAX_CONTRAOFERTAS_HOGAR;
    }

    private boolean admiteNegociacion(Estado estado) {
        return estado == Estado.E01 || estado == Estado.E02;
    }
}
