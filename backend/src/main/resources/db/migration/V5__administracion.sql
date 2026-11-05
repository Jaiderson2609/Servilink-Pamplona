-- Etapa 5: administración (RF-045 a RF-050): suspensión de cuentas, cancelación administrativa y auditoría.
ALTER TABLE usuarios ADD COLUMN suspendido BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE usuarios ADD COLUMN motivo_suspension VARCHAR(200);
ALTER TABLE usuarios ADD COLUMN suspendido_en TIMESTAMPTZ;

-- Un administrador también puede cancelar un servicio (disputas); la columna debe admitirlo.
ALTER TABLE solicitudes DROP CONSTRAINT solicitudes_cancelada_por_check;
ALTER TABLE solicitudes ADD CONSTRAINT solicitudes_cancelada_por_check
    CHECK (cancelada_por IN ('HOGAR', 'TECNICO', 'ADMIN'));

-- Toda acción administrativa deja huella: quién, qué, sobre qué y por qué.
CREATE TABLE auditoria_admin (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    admin_id      BIGINT       NOT NULL REFERENCES usuarios (id),
    accion        VARCHAR(30)  NOT NULL,
    objetivo_tipo VARCHAR(20)  NOT NULL,
    objetivo_id   BIGINT       NOT NULL,
    detalle       VARCHAR(200),
    creada_en     TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_auditoria_creada ON auditoria_admin (id DESC);
