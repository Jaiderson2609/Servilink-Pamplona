-- Verificación de técnicos (RF-010 a RF-012): cédula por ambos lados y selfie, aprobadas por el administrador.
-- Los documentos son privados: solo se guardan en la base y solo los lee el administrador.
ALTER TABLE usuarios ADD COLUMN verificacion VARCHAR(10) NOT NULL DEFAULT 'SIN_ENVIAR'
    CHECK (verificacion IN ('SIN_ENVIAR','PENDIENTE','APROBADA','RECHAZADA'));
ALTER TABLE usuarios ADD COLUMN motivo_verificacion VARCHAR(200);
ALTER TABLE usuarios ADD COLUMN verificacion_enviada_en TIMESTAMPTZ;

-- Los técnicos que ya operaban antes de esta versión quedan aprobados para no interrumpir su trabajo.
UPDATE usuarios SET verificacion = 'APROBADA' WHERE rol = 'TECNICO';

CREATE TABLE documentos_verificacion (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tecnico_id  BIGINT      NOT NULL REFERENCES usuarios (id),
    tipo        VARCHAR(15) NOT NULL CHECK (tipo IN ('CEDULA_FRENTE','CEDULA_REVERSO','SELFIE')),
    tipo_mime   VARCHAR(20) NOT NULL,
    contenido   BYTEA       NOT NULL,
    creado_en   TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_documento_tipo UNIQUE (tecnico_id, tipo)
);
