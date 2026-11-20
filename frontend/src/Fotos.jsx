import { useEffect, useState } from 'react';
import { api } from './api.js';
import { useUI } from './ui.jsx';
import { MAX_BYTES, prepararFoto } from './imagenes.js';

/**
 * Sube la foto directo a Cloudinary con la firma del servidor y luego la registra en el API.
 * Si el almacenamiento en la nube no está disponible, o la subida falla, devuelve null para que se use el respaldo.
 */
async function subirALaNube(solicitudId, archivo) {
  let firma;
  try {
    firma = await api('/uploads/signature', { metodo: 'POST', json: { solicitudId } });
  } catch (e) {
    if (e.code === 'UPLOAD_UNAVAILABLE') return null;
    throw e;
  }
  const form = new FormData();
  form.append('file', archivo);
  form.append('api_key', firma.apiKey);
  form.append('timestamp', String(firma.timestamp));
  form.append('public_id', firma.publicId);
  form.append('allowed_formats', firma.allowedFormats);
  form.append('signature', firma.signature);
  let datos;
  try {
    // Sin cabecera Authorization: el token de ServiLink nunca viaja a un tercero.
    const r = await fetch(firma.uploadUrl, { method: 'POST', body: form });
    if (!r.ok) return null;
    datos = await r.json();
  } catch {
    return null;
  }
  return api(`/service-requests/${solicitudId}/photos/cloudinary`, {
    metodo: 'POST',
    json: { publicId: datos.public_id, version: String(datos.version), signature: datos.signature, format: datos.format },
  });
}

/** Sube una foto a la solicitud: a la nube si está disponible y, si no, por el API. Lanza si el servidor la rechaza. */
export async function subirFoto(solicitudId, archivo) {
  const nube = await subirALaNube(solicitudId, archivo);
  if (nube) return nube;
  const form = new FormData(); form.append('foto', archivo);
  return api(`/service-requests/${solicitudId}/photos`, { metodo: 'POST', form });
}

export default function Fotos({ s, esHogar, recargar }) {
  const { avisar, intentar } = useUI();
  const [fotos, setFotos] = useState(null);
  const [urls, setUrls] = useState({});
  const [version, setVersion] = useState(0);

  useEffect(() => {
    let vivo = true;
    const creadas = [];
    (async () => {
      let lista = [];
      try { lista = await api(`/service-requests/${s.id}/photos`); } catch { /* sin permiso: no se muestra */ }
      if (!vivo) return;
      setFotos(lista);
      for (const f of lista.filter((x) => !x.url)) {
        api(`/service-requests/${s.id}/photos/${f.id}`, { blob: true }).then((b) => {
          if (!vivo) return;
          const u = URL.createObjectURL(b); creadas.push(u);
          setUrls((x) => ({ ...x, [f.id]: u }));
        }).catch(() => {});
      }
    })();
    return () => { vivo = false; creadas.forEach((u) => URL.revokeObjectURL(u)); };
  }, [s.id, version]);

  if (fotos === null) return null;
  const puede = esHogar && ['E01', 'E02', 'E03'].includes(s.estado) && fotos.length < 3;
  if (!fotos.length && !puede) return null;

  const subir = async (ev) => {
    const elegida = ev.target.files[0];
    if (!elegida) return;
    const f = await prepararFoto(elegida);
    if (f.size > MAX_BYTES) { avisar('Cada foto puede pesar hasta 2 MB', 'error'); ev.target.value = ''; return; }
    const r = await intentar(() => subirFoto(s.id, f));
    ev.target.value = '';
    if (r) { avisar('Foto agregada', 'ok'); setVersion((v) => v + 1); recargar(); }
  };

  return (
    <div className="tarjeta">
      <h3>Fotos</h3>
      {fotos.length
        ? <div className="fotos">{fotos.map((f) => <img key={f.id} className="foto" alt="Foto de la solicitud" loading="lazy" src={f.url || urls[f.id]} />)}</div>
        : <p className="suave">Una foto ayuda a los técnicos a cotizar mejor.</p>}
      {puede && (
        <div className="campo"><label htmlFor="foto-nueva">Agregar foto ({fotos.length}/3, hasta 2 MB)</label>
          <input id="foto-nueva" type="file" accept="image/jpeg,image/png,image/webp" onChange={subir} /></div>
      )}
    </div>
  );
}
