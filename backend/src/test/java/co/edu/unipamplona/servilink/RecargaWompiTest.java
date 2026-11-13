package co.edu.unipamplona.servilink;

import static co.edu.unipamplona.servilink.apoyo.EventoWompi.TIMESTAMP;
import static co.edu.unipamplona.servilink.apoyo.EventoWompi.SECRETO;
import static co.edu.unipamplona.servilink.apoyo.EventoWompi.checksum;
import static co.edu.unipamplona.servilink.apoyo.EventoWompi.evento;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.unipamplona.servilink.apoyo.PruebaApi;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** CP-WAL-01 a 03 (Addendum) y CP-13, CP-14 (DDS): recargas con evento firmado de Wompi. */
class RecargaWompiTest extends PruebaApi {

    private String tecnico;
    private long tecnicoId;

    @BeforeEach
    void preparar() throws Exception {
        tecnico = cuenta("TECNICO", "wompi-tecnico@ejemplo.co");
        tecnicoId = idDe(tecnico);
    }

    private String iniciar(long valor) throws Exception {
        return campo(enviar(tecnico, "/api/v1/wallet/topups", "{\"valorCop\":" + valor + "}")
                .andExpect(status().isCreated()), "$.referencia");
    }

    private ResultActions webhook(String cuerpo) throws Exception {
        return mvc.perform(post("/api/v1/webhooks/wompi").contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private long saldo() throws Exception {
        return ((Number) campo(pedir(tecnico, "/api/v1/wallet"), "$.saldoDisponibleCop")).longValue();
    }

    private int asientosDeRecarga() {
        return jdbc.queryForObject("select count(*) from libro_contable where tecnico_id = ? and tipo = 'RECARGA'",
                Integer.class, tecnicoId);
    }

    private String estadoDe(String referencia) {
        return jdbc.queryForObject("select estado from recargas where referencia = ?", String.class, referencia);
    }

    @Test
    void crearLaRecargaNoMueveDineroYDevuelveElPagoFirmado() throws Exception {
        enviar(tecnico, "/api/v1/wallet/topups", "{\"valorCop\":50000}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDING")).andExpect(jsonPath("$.valorCop").value(50000))
                .andExpect(jsonPath("$.referencia").value(org.hamcrest.Matchers.startsWith("SL-")))
                .andExpect(jsonPath("$.urlPago").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.startsWith("https://checkout.wompi.co/p/?"),
                        org.hamcrest.Matchers.containsString("amount-in-cents=5000000"),
                        org.hamcrest.Matchers.containsString("currency=COP"),
                        org.hamcrest.Matchers.containsString("signature:integrity="),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("test_integrity_ficticio")))));
        assertThat(saldo()).isZero();
        assertThat(asientosDeRecarga()).isZero();
    }

    @Test
    void elValorSeValida() throws Exception {
        enviar(tecnico, "/api/v1/wallet/topups", "{\"valorCop\":10}").andExpect(status().isBadRequest());
        enviar(tecnico, "/api/v1/wallet/topups", "{\"valorCop\":2000000}").andExpect(status().isBadRequest());
        enviar(tecnico, "/api/v1/wallet/topups", "{}").andExpect(status().isBadRequest());
    }

    @Test
    void soloUnTecnicoAutenticadoRecarga() throws Exception {
        enviar(null, "/api/v1/wallet/topups", "{\"valorCop\":50000}").andExpect(status().isUnauthorized());
        String hogar = cuenta("HOGAR", "wompi-hogar@ejemplo.co");
        enviar(hogar, "/api/v1/wallet/topups", "{\"valorCop\":50000}").andExpect(status().isForbidden());
    }

    @Test
    void cp_wal_01_pagoAprobadoAcreditaUnAsientoYSubeElSaldo() throws Exception {
        String ref = iniciar(50000);
        webhook(evento(ref, "T-100", "APPROVED", 5_000_000)).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APPROVED"));
        assertThat(saldo()).isEqualTo(50000);
        assertThat(asientosDeRecarga()).isEqualTo(1);
        assertThat(estadoDe(ref)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("select saldo_posterior from libro_contable where referencia = ?", Long.class, ref))
                .isEqualTo(50000L);
        pedir(tecnico, "/api/v1/wallet/topups/" + ref).andExpect(jsonPath("$.estado").value("APPROVED"));
    }

    @Test
    void elTecnicoRecibeUnAvisoAlAcreditarse() throws Exception {
        String ref = iniciar(50000);
        webhook(evento(ref, "T-100", "APPROVED", 5_000_000)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from notificaciones_pendientes where clave = 'REC-' || "
                + "(select id from recargas where referencia = ?) and destinatario_id = ?", Integer.class, ref, tecnicoId))
                .isEqualTo(1);
    }

    @Test
    void cp_wal_02_elMismoEventoCincoVecesDejaUnSoloAsiento() throws Exception {
        String ref = iniciar(50000);
        String cuerpo = evento(ref, "T-100", "APPROVED", 5_000_000);
        for (int i = 0; i < 5; i++) {
            webhook(cuerpo).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("APPROVED"));
        }
        assertThat(saldo()).isEqualTo(50000);
        assertThat(asientosDeRecarga()).isEqualTo(1);
    }

    @Test
    void eventosSimultaneosDeLaMismaReferenciaAcreditanUnaSolaVez() throws Exception {
        String ref = iniciar(50000);
        String cuerpo = evento(ref, "T-100", "APPROVED", 5_000_000);
        var pool = Executors.newFixedThreadPool(10);
        try {
            List<Callable<Integer>> tareas = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                tareas.add(() -> webhook(cuerpo).andReturn().getResponse().getStatus());
            }
            for (var f : pool.invokeAll(tareas)) {
                assertThat(f.get()).isEqualTo(200);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(saldo()).isEqualTo(50000);
        assertThat(asientosDeRecarga()).isEqualTo(1);
    }

    @Test
    void cp_wal_03_firmaAlteradaEsRechazadaYElSaldoNoCambia() throws Exception {
        String ref = iniciar(50000);
        String firmaDeUnPagoChico = checksum("T-100", "APPROVED", 100_000, TIMESTAMP, SECRETO);
        webhook(evento(ref, "T-100", "APPROVED", 5_000_000, "COP", firmaDeUnPagoChico)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_SIGNATURE"));
        webhook(evento(ref, "T-100", "APPROVED", 5_000_000, "COP", "0".repeat(64))).andExpect(status().isUnauthorized());
        webhook("{}").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_SIGNATURE"));
        webhook("esto no es json").andExpect(status().isUnauthorized());
        assertThat(saldo()).isZero();
        assertThat(estadoDe(ref)).isEqualTo("PENDING");
    }

    @Test
    void laCabeceraDeChecksumTambienSeVerifica() throws Exception {
        String ref = iniciar(50000);
        String cuerpo = evento(ref, "T-100", "APPROVED", 5_000_000);
        mvc.perform(post("/api/v1/webhooks/wompi").contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                .header("X-Event-Checksum", "f".repeat(64))).andExpect(status().isUnauthorized());
        assertThat(saldo()).isZero();
        String buena = checksum("T-100", "APPROVED", 5_000_000, TIMESTAMP, SECRETO);
        mvc.perform(post("/api/v1/webhooks/wompi").contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                .header("X-Event-Checksum", buena)).andExpect(status().isOk());
        assertThat(saldo()).isEqualTo(50000);
    }

    @Test
    void pagoRechazadoNoGeneraMovimiento() throws Exception {
        String ref = iniciar(50000);
        for (String estado : List.of("DECLINED", "ERROR", "VOIDED")) {
            webhook(evento(ref, "T-" + estado, estado, 5_000_000)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("FAILED"));
        }
        assertThat(saldo()).isZero();
        assertThat(asientosDeRecarga()).isZero();
        assertThat(estadoDe(ref)).isEqualTo("FAILED");
    }

    @Test
    void unPagoRechazadoPuedeAprobarseEnUnReintentoDelCliente() throws Exception {
        String ref = iniciar(50000);
        webhook(evento(ref, "T-1", "DECLINED", 5_000_000)).andExpect(status().isOk());
        webhook(evento(ref, "T-2", "APPROVED", 5_000_000)).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APPROVED"));
        assertThat(saldo()).isEqualTo(50000);
        assertThat(asientosDeRecarga()).isEqualTo(1);
    }

    @Test
    void unPagoAprobadoNoSeRevierteConUnEventoPosterior() throws Exception {
        String ref = iniciar(50000);
        webhook(evento(ref, "T-1", "APPROVED", 5_000_000)).andExpect(status().isOk());
        webhook(evento(ref, "T-2", "DECLINED", 5_000_000)).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APPROVED"));
        assertThat(saldo()).isEqualTo(50000);
        assertThat(estadoDe(ref)).isEqualTo("APPROVED");
    }

    @Test
    void unEstadoIntermedioDejaLaRecargaPendiente() throws Exception {
        String ref = iniciar(50000);
        webhook(evento(ref, "T-1", "PENDING", 5_000_000)).andExpect(status().isOk());
        assertThat(estadoDe(ref)).isEqualTo("PENDING");
        webhook(evento(ref, "T-1", "APPROVED", 5_000_000)).andExpect(status().isOk());
        assertThat(saldo()).isEqualTo(50000);
    }

    @Test
    void unMontoQueNoCoincideConLaRecargaNoSeAcredita() throws Exception {
        String ref = iniciar(50000);
        webhook(evento(ref, "T-1", "APPROVED", 100_000)).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("REVIEW"));
        assertThat(saldo()).isZero();
        assertThat(estadoDe(ref)).isEqualTo("REVIEW");
        webhook(evento(ref, "T-1", "APPROVED", 5_000_000)).andExpect(status().isOk());
        assertThat(saldo()).as("una recarga en revisión no se acredita sola").isZero();
    }

    @Test
    void unaMonedaDistintaDeCopNoSeAcredita() throws Exception {
        String ref = iniciar(50000);
        webhook(evento(ref, "T-1", "APPROVED", 5_000_000, "USD", checksum("T-1", "APPROVED", 5_000_000, TIMESTAMP, SECRETO)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("REVIEW"));
        assertThat(saldo()).isZero();
    }

    @Test
    void unaMismaTransaccionNoPuedeAcreditarDosRecargas() throws Exception {
        String primera = iniciar(50000);
        String segunda = iniciar(50000);
        webhook(evento(primera, "T-777", "APPROVED", 5_000_000)).andExpect(status().isOk());
        // El campo reference de Wompi no va firmado: el mismo pago reenviado con otra referencia no debe valer.
        webhook(evento(segunda, "T-777", "APPROVED", 5_000_000)).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("REVIEW"));
        assertThat(saldo()).isEqualTo(50000);
        assertThat(asientosDeRecarga()).isEqualTo(1);
        assertThat(estadoDe(segunda)).isEqualTo("REVIEW");
    }

    @Test
    void unaReferenciaDesconocidaOUnEventoDeOtroTipoSeIgnoranConOk() throws Exception {
        webhook(evento("SL-no-existe", "T-1", "APPROVED", 5_000_000)).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("IGNORADO"));
        String otro = evento("SL-x", "T-1", "APPROVED", 5_000_000).replace("transaction.updated", "nequi_token.updated");
        webhook(otro).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("IGNORADO"));
        assertThat(saldo()).isZero();
    }

    @Test
    void laRecargaDeOtroTecnicoNoSeConsulta() throws Exception {
        String ref = iniciar(50000);
        String otro = cuenta("TECNICO", "wompi-otro@ejemplo.co");
        pedir(otro, "/api/v1/wallet/topups/" + ref).andExpect(status().isNotFound());
        pedir(tecnico, "/api/v1/wallet/topups/SL-inexistente").andExpect(status().isNotFound());
        pedir(null, "/api/v1/wallet/topups/" + ref).andExpect(status().isUnauthorized());
    }

    @Test
    void haySeisIntentosPorHoraComoMaximo() throws Exception {
        for (int i = 0; i < 5; i++) {
            iniciar(10000);
        }
        enviar(tecnico, "/api/v1/wallet/topups", "{\"valorCop\":10000}").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
        reloj.fijar(reloj.instant().plus(java.time.Duration.ofMinutes(61)));
        String nuevoToken = campo(enviar(null, "/api/v1/auth/login",
                "{\"correo\":\"wompi-tecnico@ejemplo.co\",\"clave\":\"clave-segura-1\"}"), "$.accessToken");
        enviar(nuevoToken, "/api/v1/wallet/topups", "{\"valorCop\":10000}").andExpect(status().isCreated());
    }

    @Test
    void laBilleteraInformaQueFormasDeRecargaHay() throws Exception {
        pedir(tecnico, "/api/v1/wallet").andExpect(jsonPath("$.pagoDisponible").value(true))
                .andExpect(jsonPath("$.recargaDemoDisponible").value(true));
    }

    @Test
    void laRecargaDeDemostracionSigueDisponibleEnSuRutaPropia() throws Exception {
        enviar(tecnico, "/api/v1/wallet/topups/demo", "{\"valorCop\":50000}").andExpect(status().isOk())
                .andExpect(jsonPath("$.saldoDisponibleCop").value(50000));
    }
}
