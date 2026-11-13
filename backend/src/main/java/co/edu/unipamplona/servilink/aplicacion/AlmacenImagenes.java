package co.edu.unipamplona.servilink.aplicacion;

/** Puerto del almacén de imágenes (DAD 3.2): el navegador sube directo con una firma que emite el servidor. */
public interface AlmacenImagenes {

    /** Todo lo que el navegador necesita para subir una imagen; el secreto nunca sale del servidor. */
    record Firma(String cloudName, String apiKey, long timestamp, String publicId, String allowedFormats,
                 String signature, String uploadUrl) {}

    /** Falso si faltan las credenciales: las fotos se guardan entonces en la base de datos. */
    boolean disponible();

    /** Firma una subida de la imagen con ese identificador (público y fijado por el servidor). */
    Firma firmar(String publicId);

    /** Comprueba la firma que el almacén devuelve al terminar la subida (cubre identificador y versión). */
    boolean respuestaValida(String publicId, String version, String firma);

    /** Dirección desde la que se ve la imagen subida. */
    String urlDe(String publicId, String version);

    /** Origen (esquema y servidor) al que el navegador sube, para la política de seguridad de contenido. */
    String origenSubida();

    /** Origen desde el que el navegador muestra las imágenes. */
    String origenEntrega();
}
