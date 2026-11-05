package co.edu.unipamplona.servilink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import co.edu.unipamplona.servilink.apoyo.PruebaApi;
import org.junit.jupiter.api.Test;

class AuthTest extends PruebaApi {

    String registro(String correo, String clave, String rol, boolean acepta) {
        return "{\"nombre\":\"Ana Pérez\",\"correo\":\"" + correo + "\",\"telefono\":\"3001234567\",\"clave\":\"" + clave
                + "\",\"rol\":\"" + rol + "\",\"aceptaTerminos\":" + acepta + "}";
    }

    @Test
    void registraUnHogarYNoGuardaLaClaveEnTextoPlano() throws Exception {
        enviar(null, "/api/v1/auth/registro", registro("ana@correo.co", "clave-segura-1", "HOGAR", true))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.rol").value("HOGAR"));
        String hash = jdbc.queryForObject("SELECT password_hash FROM usuarios WHERE correo = 'ana@correo.co'", String.class);
        assertThat(hash).startsWith("$2").doesNotContain("clave-segura-1");
    }

    @Test
    void alRegistrarUnTecnicoNaceSuBilleteraEnCero() throws Exception {
        enviar(null, "/api/v1/auth/registro", registro("tec@correo.co", "clave-segura-1", "TECNICO", true))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT disponible FROM billeteras", Long.class)).isZero();
    }

    @Test
    void elCorreoSeNormalizaYNoSePuedeRepetir() throws Exception {
        enviar(null, "/api/v1/auth/registro", registro("Ana@Correo.co", "clave-segura-1", "HOGAR", true)).andExpect(status().isCreated());
        enviar(null, "/api/v1/auth/registro", registro("ana@correo.co", "otra-clave-22", "HOGAR", true))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.title").value("Correo registrado"));
    }

    @Test
    void exigeAceptarLosTerminos() throws Exception {
        enviar(null, "/api/v1/auth/registro", registro("ana@correo.co", "clave-segura-1", "HOGAR", false))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("debes aceptar la política de datos y los términos"));
    }

    @Test
    void rechazaClavesCortasRolesInventadosYCorreosInvalidos() throws Exception {
        enviar(null, "/api/v1/auth/registro", registro("ana@correo.co", "corta", "HOGAR", true)).andExpect(status().isBadRequest());
        enviar(null, "/api/v1/auth/registro", registro("ana@correo.co", "clave-segura-1", "ADMIN", true)).andExpect(status().isBadRequest());
        enviar(null, "/api/v1/auth/registro", registro("no-es-correo", "clave-segura-1", "HOGAR", true)).andExpect(status().isBadRequest());
    }

    @Test
    void iniciaSesionYElTokenSirveEnUnaRutaProtegida() throws Exception {
        String token = cuenta("TECNICO", "tec@correo.co");
        pedir(token, "/api/v1/billetera").andExpect(status().isOk()).andExpect(jsonPath("$.saldoDisponibleCop").value(0));
        var r = enviar(null, "/api/v1/auth/login", "{\"correo\":\"tec@correo.co\",\"clave\":\"clave-segura-1\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    void claveIncorrectaYCorreoInexistenteDanLaMismaRespuesta() throws Exception {
        cuenta("HOGAR", "ana@correo.co");
        var mala = enviar(null, "/api/v1/auth/login", "{\"correo\":\"ana@correo.co\",\"clave\":\"incorrecta-123\"}")
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        var inexistente = enviar(null, "/api/v1/auth/login", "{\"correo\":\"nadie@correo.co\",\"clave\":\"incorrecta-123\"}")
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        assertThat(mala).isEqualTo(inexistente);
    }

    @Test
    void sinTokenLasRutasProtegidasResponden401() throws Exception {
        pedir(null, "/api/v1/billetera").andExpect(status().isUnauthorized());
        pedir(null, "/api/v1/solicitudes/mias").andExpect(status().isUnauthorized());
    }

    @Test
    void cadaRolSoloEntraALoSuyo() throws Exception {
        String hogar = cuenta("HOGAR", "hogar@correo.co");
        String tecnico = cuenta("TECNICO", "tec@correo.co");
        enviar(tecnico, "/api/v1/solicitudes", solicitudJson("Cambiar un tomacorriente roto")).andExpect(status().isForbidden());
        pedir(hogar, "/api/v1/billetera").andExpect(status().isForbidden());
        pedir(hogar, "/api/v1/solicitudes/abiertas").andExpect(status().isForbidden());
    }

    @Test
    void laRecargaDeDemostracionExigeUnValorValido() throws Exception {
        String tecnico = cuenta("TECNICO", "tec@correo.co");
        enviar(tecnico, "/api/v1/billetera/recargas", "{\"valorCop\":10}").andExpect(status().isBadRequest());
        enviar(tecnico, "/api/v1/billetera/recargas", "{\"valorCop\":50000}").andExpect(status().isOk())
                .andExpect(jsonPath("$.saldoDisponibleCop").value(50000))
                .andExpect(jsonPath("$.movimientos[0].tipo").value("RECARGA"));
    }
}
