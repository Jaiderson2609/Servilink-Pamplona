-- Etapa 2: categorías de oficios (RF-056), ubicación, cierre y cancelación del servicio (RF-027 a RF-031).
CREATE TABLE oficios (
    id     INTEGER PRIMARY KEY,
    nombre VARCHAR(60) NOT NULL UNIQUE
);
INSERT INTO oficios (id, nombre) VALUES
    (1, 'Plomería'),
    (2, 'Electricidad'),
    (3, 'Cerrajería'),
    (4, 'Aire acondicionado y refrigeración'),
    (5, 'Reparación de electrodomésticos');

-- El oficio pasa de texto libre a categoría; las filas previas (solo de pruebas) quedan en Electricidad.
ALTER TABLE solicitudes ADD COLUMN oficio_id INTEGER NOT NULL DEFAULT 2 REFERENCES oficios (id);
ALTER TABLE solicitudes ALTER COLUMN oficio_id DROP DEFAULT;
ALTER TABLE solicitudes DROP COLUMN oficio;
ALTER TABLE solicitudes ADD COLUMN lat DOUBLE PRECISION CHECK (lat BETWEEN -90 AND 90);
ALTER TABLE solicitudes ADD COLUMN lng DOUBLE PRECISION CHECK (lng BETWEEN -180 AND 180);
ALTER TABLE solicitudes ADD COLUMN terminado_en TIMESTAMPTZ;
ALTER TABLE solicitudes ADD COLUMN cancelada_por VARCHAR(10) CHECK (cancelada_por IN ('HOGAR', 'TECNICO'));
ALTER TABLE solicitudes ADD COLUMN motivo_cancelacion VARCHAR(200);
