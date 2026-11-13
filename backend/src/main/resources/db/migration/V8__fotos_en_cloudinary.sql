-- Fotos en Cloudinary (DAD 10.2): la imagen se sube directo desde el navegador con una firma del servidor.
-- Si Cloudinary no está disponible, la foto sigue guardándose en la base como hasta ahora.
ALTER TABLE fotos_solicitud ALTER COLUMN contenido DROP NOT NULL;
ALTER TABLE fotos_solicitud ALTER COLUMN tipo_mime DROP NOT NULL;
ALTER TABLE fotos_solicitud ALTER COLUMN tamano_bytes DROP NOT NULL;
ALTER TABLE fotos_solicitud ADD COLUMN public_id VARCHAR(200) UNIQUE;
ALTER TABLE fotos_solicitud ADD COLUMN url VARCHAR(400);
-- Cada foto vive en un solo lugar: o sus bytes están en la base, o está en Cloudinary (identificador y dirección).
ALTER TABLE fotos_solicitud ADD CONSTRAINT fotos_origen_check CHECK (
    (contenido IS NOT NULL AND public_id IS NULL AND url IS NULL)
    OR (contenido IS NULL AND public_id IS NOT NULL AND url IS NOT NULL));
