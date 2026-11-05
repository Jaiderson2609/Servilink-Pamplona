package co.edu.unipamplona.servilink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import co.edu.unipamplona.servilink.apoyo.PruebaApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Administración: permisos, suspensión de cuentas, cancelación de disputas, libro y auditoría. */
class AdminTest extends PruebaApi {

    String admin, hogar, tecnico;

    @BeforeEach
    void escenario() throws Exception {
        admin = cuentaAdmin("admin@servilink.co");
        hogar = cuenta("HOGAR", "hogar@correo.co");
        tecnico = cuenta("TECNICO", "tec@correo.co");
        recargar(tecnico, 50_000);
    }

    org.springframework.test.web.servlet.ResultActions quitarSuspension(String token, long id) throws Exception {
        return mvc.perform(delete("/api/v1/admin/usuarios/" + id + "/suspension").header("Authorization", "Bearer " + token));
    }

    String motivo(String m) { return "{\"motivo\":\"" + m + "\"}"; }

    @Test
    void soloElAdministradorEntraAlPanel() throws Exception {
        for (String url : new String[] {"/api/v1/admin/resumen", "/api/v1/admin/usuarios", "/api/v1/admin/solicitudes",
                "/api/v1/admin/auditoria", "/api/v1/admin/libro?solicitudId=1"}) {
            pedir(hogar, url).andExpect(status().isForbidden());
            pedir(tecnico, url).andExpect(status().isForbidden());
            pedir(null, url).andExpect(status().isUnauthorized());
        }
        enviar(hogar, "/api/v1/admin/usuarios/1/suspension", motivo("intento de abuso")).andExpect(status().isForbidden());
        quitarSuspension(tecnico, 1).andExpect(status().isForbidden());
    }

    @Test
    void elAdministradorNoUsaLasFuncionesDeHogarNiTecnico() throws Exception {
        enviar(admin, "/api/v1/solicitudes", solicitudJson("Cambiar un tomacorriente roto")).andExpect(status().isForbidden());
        pedir(admin, "/api/v1/billetera").andExpect(status().isForbidden());
    }

    @Test
    void elRegistroPublicoNuncaCreaAdministradores() throws Exception {
        enviar(null, "/api/v1/auth/registro", "{\"nombre\":\"Zeta\",\"correo\":\"z@correo.co\",\"telefono\":\"3001234567\","
                + "\"clave\":\"clave-segura-1\",\"rol\":\"ADMIN\",\"aceptaTerminos\":true}").andExpect(status().isBadRequest());
    }

    @Test
    void elAdministradorInicialSeCreaUnaSolaVezYExigeClaveLarga() {
        assertThat(auth.asegurarAdmin("otro-admin@servilink.co", "clave-admin-segura-1")).isTrue();
        assertThat(auth.asegurarAdmin("otro-admin@servilink.co", "clave-admin-segura-1")).isFalse();
        assertThrows(IllegalStateException.class, () -> auth.asegurarAdmin("corta@servilink.co", "corta"));
        String hash = jdbc.queryForObject("SELECT password_hash FROM usuarios WHERE correo = 'otro-admin@servilink.co'", String.class);
        assertThat(hash).startsWith("$2").doesNotContain("clave-admin");
        assertThat(jdbc.queryForObject("SELECT rol FROM usuarios WHERE correo = 'otro-admin@servilink.co'", String.class)).isEqualTo("ADMIN");
    }

    @Test
    void elResumenCuentaUsuariosSolicitudesYComisiones() throws Exception {
        long s1 = asignado(hogar, tecnico, "Cambiar un tomacorriente roto");
        finalizar(hogar, tecnico, s1);
        String hogar2 = cuenta("HOGAR", "h2@correo.co");
        enviar(hogar2, "/api/v1/solicitudes", solicitudJson("Revisar la instalación de gas")).andExpect(status().isCreated());
        pedir(admin, "/api/v1/admin/resumen").andExpect(status().isOk())
                .andExpect(jsonPath("$.solicitudesPorEstado.E05").value(1)).andExpect(jsonPath("$.solicitudesPorEstado.E01").value(1))
                .andExpect(jsonPath("$.solicitudesPorEstado.E07").value(0))
                .andExpect(jsonPath("$.usuariosPorRol.HOGAR").value(2)).andExpect(jsonPath("$.usuariosPorRol.TECNICO").value(1))
                .andExpect(jsonPath("$.usuariosPorRol.ADMIN").value(1)).andExpect(jsonPath("$.comisionesCobradasCop").value(2000))
                .andExpect(jsonPath("$.comisionesRetenidasCop").value(0)).andExpect(jsonPath("$.cuentasSuspendidas").value(0));
    }

    @Test
    void listaUsuariosConFiltroBusquedaYPaginaSinExponerClaves() throws Exception {
        var r = pedir(admin, "/api/v1/admin/usuarios").andExpect(status().isOk()).andExpect(jsonPath("$.total").value(3));
        assertThat(r.andReturn().getResponse().getContentAsString()).doesNotContain("password").doesNotContain("$2");
        pedir(admin, "/api/v1/admin/usuarios?rol=TECNICO").andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].correo").value("tec@correo.co"));
        pedir(admin, "/api/v1/admin/usuarios?q=HOGAR@").andExpect(jsonPath("$.items.length()").value(1));
        pedir(admin, "/api/v1/admin/usuarios?tamano=2&pagina=1").andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.total").value(3));
        pedir(admin, "/api/v1/admin/usuarios?rol=ROOT").andExpect(status().isBadRequest());
        pedir(admin, "/api/v1/admin/usuarios?tamano=500").andExpect(status().isBadRequest());
        // Un comodín del usuario no se interpreta: "%" se descarta y no puede usarse para forzar coincidencias raras.
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/admin/usuarios")
                .param("q", "%zzz%").header("Authorization", "Bearer " + admin)).andExpect(jsonPath("$.total").value(0));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/admin/usuarios")
                .param("q", "%").header("Authorization", "Bearer " + admin)).andExpect(jsonPath("$.total").value(3));
    }

    @Test
    void suspenderCortaElAccesoDeInmediatoYReactivarLoDevuelve() throws Exception {
        long id = idDe(hogar);
        pedir(hogar, "/api/v1/solicitudes/mias").andExpect(status().isOk());
        enviar(admin, "/api/v1/admin/usuarios/" + id + "/suspension", motivo("Comportamiento abusivo reiterado"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.suspendido").value(true))
                .andExpect(jsonPath("$.motivoSuspension").value("Comportamiento abusivo reiterado"));

        pedir(hogar, "/api/v1/solicitudes/mias").andExpect(status().isUnauthorized());           // el token que ya tenía deja de servir
        enviar(null, "/api/v1/auth/login", "{\"correo\":\"hogar@correo.co\",\"clave\":\"clave-segura-1\"}")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.title").value("Cuenta suspendida"));
        enviar(null, "/api/v1/auth/login", "{\"correo\":\"hogar@correo.co\",\"clave\":\"incorrecta-123\"}")
                .andExpect(status().isUnauthorized());                                           // sin la clave, no se sabe que está suspendida

        quitarSuspension(admin, id).andExpect(status().isOk()).andExpect(jsonPath("$.suspendido").value(false));
        String nuevo = campo(enviar(null, "/api/v1/auth/login", "{\"correo\":\"hogar@correo.co\",\"clave\":\"clave-segura-1\"}"), "$.accessToken");
        pedir(nuevo, "/api/v1/solicitudes/mias").andExpect(status().isOk());
    }

    @Test
    void suspenderEsIdempotenteNoTocaAdministradoresYValidaElMotivo() throws Exception {
        long id = idDe(tecnico);
        enviar(admin, "/api/v1/admin/usuarios/" + id + "/suspension", motivo("Documentos falsos")).andExpect(status().isOk());
        enviar(admin, "/api/v1/admin/usuarios/" + id + "/suspension", motivo("Documentos falsos")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria_admin WHERE accion = 'SUSPENDER_USUARIO'", Integer.class)).isEqualTo(1);
        enviar(admin, "/api/v1/admin/usuarios/" + idDe(admin) + "/suspension", motivo("Me suspendo a mí mismo")).andExpect(status().isConflict());
        enviar(admin, "/api/v1/admin/usuarios/" + id + "/suspension", motivo("no")).andExpect(status().isBadRequest());
        enviar(admin, "/api/v1/admin/usuarios/" + id + "/suspension", "{}").andExpect(status().isBadRequest());
        enviar(admin, "/api/v1/admin/usuarios/9999/suspension", motivo("No existe el usuario")).andExpect(status().isNotFound());
        quitarSuspension(admin, 9999).andExpect(status().isNotFound());
    }

    @Test
    void listaSolicitudesPorEstado() throws Exception {
        asignado(hogar, tecnico, "Cambiar un tomacorriente roto");
        String hogar2 = cuenta("HOGAR", "h2@correo.co");
        enviar(hogar2, "/api/v1/solicitudes", solicitudJson("Revisar la instalación de gas"));
        pedir(admin, "/api/v1/admin/solicitudes").andExpect(jsonPath("$.total").value(2));
        pedir(admin, "/api/v1/admin/solicitudes?estado=E03").andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].estado").value("E03")).andExpect(jsonPath("$.items[0].tecnicoId").value(idDe(tecnico)));
        pedir(admin, "/api/v1/admin/solicitudes?estado=E05").andExpect(jsonPath("$.items.length()").value(0));
        pedir(admin, "/api/v1/admin/solicitudes?estado=E99").andExpect(status().isBadRequest());
    }

    @Test
    void cancelarUnServicioAsignadoLiberaLaComisionAvisaYAudita() throws Exception {
        long sol = asignado(hogar, tecnico, "Cambiar un tomacorriente roto");
        notificador.enviados.clear();
        enviar(admin, "/api/v1/admin/solicitudes/" + sol + "/cancelacion", motivo("Disputa por el precio acordado"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("E07")).andExpect(jsonPath("$.canceladaPor").value("ADMIN"));
        pedir(tecnico, "/api/v1/billetera").andExpect(jsonPath("$.saldoDisponibleCop").value(50000))
                .andExpect(jsonPath("$.saldoRetenidoCop").value(0));
        assertThat(jdbc.queryForList("SELECT tipo FROM libro_contable WHERE solicitud_id = ? ORDER BY id", String.class, sol))
                .containsExactly("RETENCION", "LIBERACION");
        assertThat(notificador.enviados).hasSize(1); // el hogar, de inmediato; el técnico queda en la bandeja de reintentos
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificaciones_pendientes WHERE clave LIKE 'CANCA-%'", Integer.class)).isEqualTo(2);
        pedir(admin, "/api/v1/admin/auditoria").andExpect(jsonPath("$.items[0].accion").value("CANCELAR_SOLICITUD"))
                .andExpect(jsonPath("$.items[0].objetivoId").value(sol)).andExpect(jsonPath("$.items[0].adminId").value(idDe(admin)));
        enviar(hogar, "/api/v1/solicitudes", solicitudJson("Una solicitud nueva tras la cancelación")).andExpect(status().isCreated());
        enviar(tecnico, "/api/v1/solicitudes/" + sol + "/llegada", null).andExpect(status().isConflict());
    }

    @Test
    void cancelarUnaSolicitudAbiertaNoCobraNiLiberaNada() throws Exception {
        long sol = ((Number) campo(enviar(hogar, "/api/v1/solicitudes", solicitudJson("Cambiar un tomacorriente roto")), "$.id")).longValue();
        enviar(admin, "/api/v1/admin/solicitudes/" + sol + "/cancelacion", motivo("Contenido inapropiado")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM libro_contable WHERE tipo <> 'RECARGA'", Integer.class)).isZero();
    }

    @Test
    void noSeCancelaLoYaFinalizadoNiSinMotivoNiLoInexistente() throws Exception {
        long sol = asignado(hogar, tecnico, "Cambiar un tomacorriente roto");
        enviar(admin, "/api/v1/admin/solicitudes/" + sol + "/cancelacion", motivo("no")).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT estado FROM solicitudes WHERE id = ?", String.class, sol)).isEqualTo("E03");
        finalizar(hogar, tecnico, sol);
        enviar(admin, "/api/v1/admin/solicitudes/" + sol + "/cancelacion", motivo("Ya está finalizada")).andExpect(status().isConflict());
        enviar(admin, "/api/v1/admin/solicitudes/9999/cancelacion", motivo("No existe la solicitud")).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria_admin", Integer.class)).isZero(); // lo fallido no se audita
    }

    @Test
    void elLibroMuestraLosMovimientosDeUnaSolicitud() throws Exception {
        long sol = asignado(hogar, tecnico, "Cambiar un tomacorriente roto");
        finalizar(hogar, tecnico, sol);
        pedir(admin, "/api/v1/admin/libro?solicitudId=" + sol).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].tipo").value("RETENCION")).andExpect(jsonPath("$[1].tipo").value("COMISION"))
                .andExpect(jsonPath("$[1].valorCop").value(2000));
        pedir(admin, "/api/v1/admin/libro?solicitudId=9999").andExpect(status().isNotFound());
        pedir(admin, "/api/v1/admin/libro").andExpect(status().isBadRequest());
    }

    @Test
    void laAuditoriaListaLoMasRecientePrimero() throws Exception {
        long id = idDe(hogar);
        enviar(admin, "/api/v1/admin/usuarios/" + id + "/suspension", motivo("Motivo de prueba uno"));
        quitarSuspension(admin, id);
        pedir(admin, "/api/v1/admin/auditoria").andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].accion").value("REACTIVAR_USUARIO")).andExpect(jsonPath("$.items[1].accion").value("SUSPENDER_USUARIO"))
                .andExpect(jsonPath("$.items[1].detalle").value("Motivo de prueba uno"));
        pedir(admin, "/api/v1/admin/auditoria?tamano=0").andExpect(status().isBadRequest());
    }
}
