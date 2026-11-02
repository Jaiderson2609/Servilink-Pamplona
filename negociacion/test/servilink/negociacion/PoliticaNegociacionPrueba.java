package servilink.negociacion;

public class PoliticaNegociacionPrueba {
    static int ok = 0, mal = 0;

    static void verificar(String nombre, boolean condicion) {
        if (condicion) { ok++; System.out.println("  PASA  " + nombre); }
        else { mal++; System.out.println("  FALLA " + nombre); }
    }

    public static void main(String[] args) {
        PoliticaNegociacion p = new PoliticaNegociacion();
        verificar("el técnico puede hacer su primera oferta", p.puedeOfertarTecnico(0, Estado.E01));
        verificar("el técnico puede hacer su segunda oferta", p.puedeOfertarTecnico(1, Estado.E02));
        verificar("el técnico no puede hacer una tercera oferta", !p.puedeOfertarTecnico(2, Estado.E02));
        verificar("el técnico no puede ofertar si ya está asignada (E03)", !p.puedeOfertarTecnico(0, Estado.E03));
        verificar("el técnico no puede ofertar si expiró (E06)", !p.puedeOfertarTecnico(0, Estado.E06));
        verificar("el hogar puede contraofertar una vez tras recibir una oferta", p.puedeContraofertarHogar(0, 1, Estado.E02));
        verificar("el hogar no puede contraofertar sin ofertas previas", !p.puedeContraofertarHogar(0, 0, Estado.E01));
        verificar("el hogar no puede hacer una segunda contraoferta", !p.puedeContraofertarHogar(1, 2, Estado.E02));
        System.out.println(ok + " pasaron, " + mal + " fallaron");
        if (mal > 0) System.exit(1);
    }
}
