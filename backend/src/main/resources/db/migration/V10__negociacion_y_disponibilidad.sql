-- Negociación de precio (RF-023) y disponibilidad del técnico (RF-019).
-- postulaciones.precio pasa a ser el precio vigente; cada propuesta queda en el historial de ofertas.
CREATE TABLE ofertas (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    postulacion_id BIGINT      NOT NULL REFERENCES postulaciones (id),
    autor          VARCHAR(7)  NOT NULL CHECK (autor IN ('TECNICO','HOGAR')),
    monto          INTEGER     NOT NULL CHECK (monto > 0),
    secuencia      INTEGER     NOT NULL CHECK (secuencia > 0),
    estado         VARCHAR(10) NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN ('PENDIENTE','ACEPTADA','RECHAZADA')),
    creada_en      TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_oferta_secuencia UNIQUE (postulacion_id, secuencia)
);

-- Cada postulación existente ya tiene su oferta inicial.
INSERT INTO ofertas (postulacion_id, autor, monto, secuencia, estado, creada_en)
SELECT id, 'TECNICO', precio, 1, 'PENDIENTE', creada_en FROM postulaciones;

ALTER TABLE usuarios ADD COLUMN disponibilidad VARCHAR(9) NOT NULL DEFAULT 'AVAILABLE'
    CHECK (disponibilidad IN ('AVAILABLE','BUSY','OFFLINE'));
