package co.edu.unipamplona.servilink.apoyo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Ayudas para recorrer la API por HTTP, como lo haría un cliente real (sin tocar la base directamente). */
public abstract class PruebaApi extends PruebaIntegracion {

    @org.springframework.beans.factory.annotation.Autowired protected co.edu.unipamplona.servilink.aplicacion.AuthService auth;

    /** Crea un administrador (no se puede registrar por la API pública) e inicia sesión. */
    protected String cuentaAdmin(String correo) throws Exception {
        auth.asegurarAdmin(correo, "clave-admin-segura-1");
        return campo(enviar(null, "/api/v1/auth/login", "{\"correo\":\"" + correo + "\",\"clave\":\"clave-admin-segura-1\"}"), "$.accessToken");
    }

    protected ResultActions enviar(String token, String url, String json) throws Exception {
        return enviar(token, url, json, null);
    }

    /** Las transiciones de estado usan PATCH (DDS 9); crear recursos usa POST. */
    protected static boolean esTransicion(String url) {
        return url.matches(".*/(arrive|complete|confirm|cancel|select|status|withdraw|accept)$");
    }

    protected ResultActions enviar(String token, String url, String json, String claveIdempotencia) throws Exception {
        MockHttpServletRequestBuilder req = (esTransicion(url) ? patch(url) : post(url))
                .contentType(MediaType.APPLICATION_JSON).content(json == null ? "" : json);
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        if (claveIdempotencia != null) {
            req.header("Idempotency-Key", claveIdempotencia);
        }
        return mvc.perform(req);
    }

    protected ResultActions pedir(String token, String url) throws Exception {
        MockHttpServletRequestBuilder req = get(url);
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(req);
    }

    protected <T> T campo(ResultActions r, String ruta) throws Exception {
        return JsonPath.read(r.andReturn().getResponse().getContentAsString(), ruta);
    }

    /** Registra por la API, inicia sesión y devuelve el token. */
    protected String cuenta(String rol, String correo) throws Exception {
        enviar(null, "/api/v1/auth/register", "{\"nombre\":\"Usuario " + correo + "\",\"correo\":\"" + correo
                + "\",\"telefono\":\"3001234567\",\"clave\":\"clave-segura-1\",\"rol\":\"" + rol
                + "\",\"aceptaTerminos\":true}").andReturn();
        if ("TECNICO".equals(rol)) { // salvo en las pruebas de verificación, el técnico de prueba ya está verificado
            jdbc.update("UPDATE usuarios SET verificacion = 'APROBADA' WHERE correo = ?", correo);
        }
        return campo(enviar(null, "/api/v1/auth/login", "{\"correo\":\"" + correo + "\",\"clave\":\"clave-segura-1\"}"),
                "$.accessToken");
    }

    /** Técnico recién registrado: sin verificar, como en producción. */
    protected String cuentaSinVerificar(String correo) throws Exception {
        String t = cuenta("TECNICO", correo);
        jdbc.update("UPDATE usuarios SET verificacion = 'SIN_ENVIAR' WHERE correo = ?", correo);
        return t;
    }

    protected long idDe(String token) throws Exception {
        return Long.parseLong(com.nimbusds.jwt.SignedJWT.parse(token).getJWTClaimsSet().getSubject());
    }

    protected String solicitudJson(String descripcion) {
        return "{\"oficioId\":2,\"descripcion\":\"" + descripcion + "\",\"ubicacion\":{\"lat\":7.3772,\"lng\":-72.6473}}";
    }

    protected String recargar(String tokenTecnico, long valor) throws Exception {
        return enviar(tokenTecnico, "/api/v1/wallet/topups/demo", "{\"valorCop\":" + valor + "}").andReturn()
                .getResponse().getContentAsString();
    }

    /** Crea una solicitud, el técnico se postula y el hogar lo asigna: el servicio queda en E03. */
    protected long asignado(String hogar, String tecnico, String descripcion) throws Exception {
        long sol = ((Number) campo(enviar(hogar, "/api/v1/service-requests", solicitudJson(descripcion)), "$.id")).longValue();
        long pos = ((Number) campo(enviar(tecnico, "/api/v1/service-requests/" + sol + "/applications",
                "{\"precioCop\":20000}"), "$.id")).longValue();
        enviar(hogar, "/api/v1/applications/" + pos + "/select", null).andExpect(status().isOk());
        return sol;
    }

    /** Del servicio asignado (E03) hasta la confirmación del hogar (E05). */
    protected void finalizar(String hogar, String tecnico, long sol) throws Exception {
        enviar(tecnico, "/api/v1/service-requests/" + sol + "/arrive", null).andExpect(status().isOk());
        enviar(tecnico, "/api/v1/service-requests/" + sol + "/complete", null).andExpect(status().isOk());
        enviar(hogar, "/api/v1/service-requests/" + sol + "/confirm", null).andExpect(status().isOk());
    }

    protected org.springframework.test.web.servlet.ResultActions subirFoto(String token, long sol, byte[] bytes, String tipo)
            throws Exception {
        var archivo = new org.springframework.mock.web.MockMultipartFile("foto", "foto", tipo, bytes);
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart("/api/v1/service-requests/" + sol + "/photos").file(archivo)
                .header("Authorization", "Bearer " + token));
    }

    protected static byte[] jpeg(int tamano) {
        byte[] b = new byte[tamano];
        b[0] = (byte) 0xFF; b[1] = (byte) 0xD8; b[2] = (byte) 0xFF; b[3] = (byte) 0xE0;
        return b;
    }

    protected static byte[] png() {
        return new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    }

    protected static byte[] webp() {
        return new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '};
    }
}
