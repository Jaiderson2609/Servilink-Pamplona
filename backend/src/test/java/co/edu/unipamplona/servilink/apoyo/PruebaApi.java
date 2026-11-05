package co.edu.unipamplona.servilink.apoyo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Ayudas para recorrer la API por HTTP, como lo haría un cliente real (sin tocar la base directamente). */
public abstract class PruebaApi extends PruebaIntegracion {

    protected ResultActions enviar(String token, String url, String json) throws Exception {
        MockHttpServletRequestBuilder req = post(url).contentType(MediaType.APPLICATION_JSON).content(json == null ? "" : json);
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
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
        enviar(null, "/api/v1/auth/registro", "{\"nombre\":\"Usuario " + correo + "\",\"correo\":\"" + correo
                + "\",\"telefono\":\"3001234567\",\"clave\":\"clave-segura-1\",\"rol\":\"" + rol
                + "\",\"aceptaTerminos\":true}").andReturn();
        return campo(enviar(null, "/api/v1/auth/login", "{\"correo\":\"" + correo + "\",\"clave\":\"clave-segura-1\"}"),
                "$.accessToken");
    }

    protected long idDe(String token) throws Exception {
        return Long.parseLong(com.nimbusds.jwt.SignedJWT.parse(token).getJWTClaimsSet().getSubject());
    }

    protected String solicitudJson(String descripcion) {
        return "{\"oficioId\":2,\"descripcion\":\"" + descripcion + "\",\"ubicacion\":{\"lat\":7.3772,\"lng\":-72.6473}}";
    }

    protected String recargar(String tokenTecnico, long valor) throws Exception {
        return enviar(tokenTecnico, "/api/v1/billetera/recargas", "{\"valorCop\":" + valor + "}").andReturn()
                .getResponse().getContentAsString();
    }
}
