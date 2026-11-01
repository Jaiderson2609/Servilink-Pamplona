package servilink.prueba;

import servilink.nucleo.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** Pruebas del núcleo con dobles en memoria: no hay base de datos, ni red, ni framework. */
public class AsignarTecnicoPrueba {
    // ---- Dobles de prueba (adaptadores falsos) ----
    static class SolicitudesEnMemoria implements SolicitudRepository {
        final Map<String, Solicitud> datos = new HashMap<>();
        public Optional<Solicitud> buscar(String id) { return Optional.ofNullable(datos.get(id)); }
        public void guardar(Solicitud s) { datos.put(s.id(), s); }
    }
    static class BilleteraEnMemoria implements BilleteraRepository {
        BigDecimal saldo; final List<String> asientos = new ArrayList<>();
        BilleteraEnMemoria(String saldo) { this.saldo = new BigDecimal(saldo); }
        public BigDecimal saldo(String t) { return saldo; }
        public void retener(String t, BigDecimal monto, String ref) { saldo = saldo.subtract(monto); asientos.add(ref + ":" + monto); }
    }
    static class NotificadorFalso implements Notificador {
        int enviados = 0;
        public void asignacionConfirmada(String h, String t, String s) { enviados++; }
    }

    static final Instant AHORA = Instant.parse("2026-11-01T10:00:00Z");
    static final Reloj RELOJ = () -> AHORA;
    static int pasaron = 0, fallaron = 0;

    static Solicitud solicitud(Estado e, Instant expira) {
        return new Solicitud("S1", "H1", new BigDecimal("200000"), expira, e);
    }
    static void caso(String nombre, Runnable prueba) {
        try { prueba.run(); pasaron++; System.out.println("OK    " + nombre); }
        catch (AssertionError | RuntimeException e) { fallaron++; System.out.println("FALLA " + nombre + " -> " + e); }
    }
    static void exige(boolean condicion, String mensaje) { if (!condicion) throw new AssertionError(mensaje); }
    static void exigeRegla(Runnable r, String fragmento) {
        try { r.run(); } catch (ReglaNegocioException e) { exige(e.getMessage().contains(fragmento), "mensaje: " + e.getMessage()); return; }
        throw new AssertionError("se esperaba ReglaNegocioException");
    }

    public static void main(String[] args) {
        caso("CP-1 asignación correcta retiene el 10 % y pasa a E03", () -> {
            var sol = new SolicitudesEnMemoria(); sol.guardar(solicitud(Estado.E02_EN_SELECCION, AHORA.plusSeconds(300)));
            var bil = new BilleteraEnMemoria("50000"); var not = new NotificadorFalso();
            var r = new AsignarTecnico(sol, bil, not, RELOJ).ejecutar("S1", "T1");
            exige(r.estado() == Estado.E03_ASIGNADA, "estado");
            exige(r.retencion().compareTo(new BigDecimal("20000")) == 0, "retención " + r.retencion());
            exige(bil.saldo.compareTo(new BigDecimal("30000")) == 0, "saldo " + bil.saldo);
            exige(not.enviados == 1, "notificaciones");
        });
        caso("CP-2 saldo insuficiente no cambia estado ni retiene", () -> {
            var sol = new SolicitudesEnMemoria(); sol.guardar(solicitud(Estado.E02_EN_SELECCION, AHORA.plusSeconds(300)));
            var bil = new BilleteraEnMemoria("10000"); var not = new NotificadorFalso();
            exigeRegla(() -> new AsignarTecnico(sol, bil, not, RELOJ).ejecutar("S1", "T1"), "Saldo insuficiente");
            exige(sol.datos.get("S1").estado() == Estado.E02_EN_SELECCION, "debe seguir en E02");
            exige(bil.asientos.isEmpty() && not.enviados == 0, "sin efectos");
        });
        caso("CP-3 ventana expirada se rechaza", () -> {
            var sol = new SolicitudesEnMemoria(); sol.guardar(solicitud(Estado.E02_EN_SELECCION, AHORA.minusSeconds(1)));
            var bil = new BilleteraEnMemoria("50000");
            exigeRegla(() -> new AsignarTecnico(sol, bil, new NotificadorFalso(), RELOJ).ejecutar("S1", "T1"), "expiró");
            exige(bil.asientos.isEmpty(), "no debe retener");
        });
        caso("CP-4 reintento idempotente: una sola retención", () -> {
            var sol = new SolicitudesEnMemoria(); sol.guardar(solicitud(Estado.E02_EN_SELECCION, AHORA.plusSeconds(300)));
            var bil = new BilleteraEnMemoria("50000"); var not = new NotificadorFalso();
            var caso = new AsignarTecnico(sol, bil, not, RELOJ);
            caso.ejecutar("S1", "T1"); var segunda = caso.ejecutar("S1", "T1");
            exige(segunda.repetida(), "debe marcarse como repetida");
            exige(bil.asientos.size() == 1 && not.enviados == 1, "asientos=" + bil.asientos.size());
        });
        caso("CP-5 solo se asigna desde E02", () -> {
            var sol = new SolicitudesEnMemoria(); sol.guardar(solicitud(Estado.E01_ABIERTA, AHORA.plusSeconds(300)));
            exigeRegla(() -> new AsignarTecnico(sol, new BilleteraEnMemoria("50000"), new NotificadorFalso(), RELOJ).ejecutar("S1", "T1"), "E02");
        });
        caso("CP-6 solicitud inexistente", () ->
            exigeRegla(() -> new AsignarTecnico(new SolicitudesEnMemoria(), new BilleteraEnMemoria("0"), new NotificadorFalso(), RELOJ).ejecutar("X", "T1"), "no existe"));
        System.out.println("\nResultado: " + pasaron + " pasaron, " + fallaron + " fallaron");
        if (fallaron > 0) System.exit(1);
    }
}
