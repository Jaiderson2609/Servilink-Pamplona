import { useEffect, useMemo, useRef, useState } from 'react';
import { api, nuevaLlave } from './api.js';
import { useSesion } from './sesion.jsx';
import { Cargando, useUI } from './ui.jsx';
import { usePolling, ir } from './hooks.js';
import { ACTIVAS, PAMPLONA, nombrePila } from './util.js';
import { ListaFiltrada, ListaSolicitudes } from './Solicitudes.jsx';
import Ubicacion from './Ubicacion.jsx';
import { subirFoto } from './Fotos.jsx';
import { MAX_BYTES, prepararFoto } from './imagenes.js';
import { pestanaHogar } from './menus.js';

export default function Hogar({ tab }) {
  const { yo } = useSesion();
  const { datos, error, cargando } = usePolling(async () => {
    const [mias, oficios] = await Promise.all([api('/service-requests/mine'), api('/trades')]);
    return { mias, oficios };
  }, 15000, [], (e) => e.tipo === 'solicitud');

  if (cargando) return <Cargando />;
  if (error || !datos) return <div className="vacio">No pudimos cargar tus solicitudes. {error?.message}</div>;
  const activa = datos.mias.find((s) => ACTIVAS.includes(s.estado));

  if (pestanaHogar(tab) === 'solicitudes') {
    return (
      <>
        <div className="solo-ancho">
          <h1>Mis solicitudes</h1>
          <p className="suave">Sigue el estado de lo que has publicado.</p>
        </div>
        <ListaFiltrada lista={datos.mias} vacio="Aún no tienes solicitudes." />
      </>
    );
  }

  return (
    <>
      <div className="solo-ancho">
        <h1>Hola, {nombrePila(yo.nombre)}</h1>
        <p className="suave">Cuéntanos qué necesitas y los técnicos de Pamplona te enviarán ofertas.</p>
      </div>
      {activa ? (
        <div className="tarjeta">
          <h2>Tu solicitud activa</h2>
          <p className="suave">Puedes tener una solicitud activa a la vez. Termínala o cancélala para crear otra.</p>
          <ListaSolicitudes lista={[activa]} vacio="" />
        </div>
      ) : <NuevaSolicitud oficios={datos.oficios} />}
    </>
  );
}

const MAX_FOTOS = 3;

/** Vista previa de una foto elegida, con el botón para quitarla. */
function FotoElegida({ archivo, alQuitar }) {
  const url = useMemo(() => URL.createObjectURL?.(archivo), [archivo]);
  useEffect(() => () => { if (url) URL.revokeObjectURL?.(url); }, [url]);
  return (
    <div className="foto-elegida">
      {url ? <img className="foto" alt="Foto elegida" src={url} /> : <span className="foto">{archivo.name}</span>}
      <button type="button" className="foto-quitar" aria-label={`Quitar ${archivo.name}`} onClick={alQuitar}>×</button>
    </div>
  );
}

function NuevaSolicitud({ oficios }) {
  const { avisar, intentar } = useUI();
  const [oficioId, setOficioId] = useState(oficios[0]?.id ?? '');
  const [descripcion, setDescripcion] = useState('');
  const [ubicacion, setUbicacion] = useState(PAMPLONA);
  const [enviando, setEnviando] = useState(false);
  const [fotos, setFotos] = useState([]);
  const llave = useRef(nuevaLlave()); // el mismo intento reutiliza la clave: un doble toque no crea dos solicitudes

  const elegirFotos = async (ev) => {
    const elegidas = Array.from(ev.target.files);
    ev.target.value = '';
    const listas = [];
    for (const e of elegidas.slice(0, Math.max(0, MAX_FOTOS - fotos.length))) {
      const f = await prepararFoto(e);
      if (f.size > MAX_BYTES) avisar(`«${e.name}» pesa más de 2 MB y no se pudo reducir`, 'error'); else listas.push(f);
    }
    if (elegidas.length > MAX_FOTOS - fotos.length) avisar('Puedes agregar hasta 3 fotos', 'error');
    setFotos((actuales) => [...actuales, ...listas].slice(0, MAX_FOTOS));
  };

  const enviar = async (ev) => {
    ev.preventDefault();
    if (descripcion.trim().length < 10) { avisar('La descripción debe tener al menos 10 caracteres', 'error'); return; }
    setEnviando(true);
    const s = await intentar(() => api('/service-requests', {
      metodo: 'POST', llave: llave.current,
      json: { oficioId: Number(oficioId), descripcion: descripcion.trim(), ubicacion },
    }));
    if (s) {
      llave.current = nuevaLlave();
      // Las fotos se suben una por una a la solicitud recién creada; si alguna falla, la solicitud ya está publicada.
      let fallidas = 0;
      for (const f of fotos) {
        try { await subirFoto(s.id, f); } catch { fallidas += 1; }
      }
      if (fallidas) avisar(`Solicitud publicada, pero ${fallidas === 1 ? 'una foto no se pudo subir' : `${fallidas} fotos no se pudieron subir`}. Puedes agregarla desde el detalle.`, 'error');
      else avisar('Solicitud publicada. Los técnicos cercanos ya pueden verla.', 'ok');
      ir(`#/s/${s.id}`);
    }
    setEnviando(false);
  };

  return (
    <form className="tarjeta formulario" onSubmit={enviar}>
      <h2 className="solo-ancho">Nueva solicitud</h2>
      <div className="campo"><span id="n-oficio-etq" className="etiqueta">¿Qué oficio necesitas?</span>
        <div className="chips-oficio" role="radiogroup" aria-labelledby="n-oficio-etq">
          {oficios.map((o) => (
            <button key={o.id} type="button" role="radio" aria-checked={Number(oficioId) === o.id} className="chip-oficio" onClick={() => setOficioId(o.id)}>{o.nombre}</button>
          ))}
        </div></div>
      <div className="campo"><label htmlFor="n-desc">Cuéntanos el problema</label>
        <textarea id="n-desc" minLength={10} maxLength={500} required value={descripcion} onChange={(e) => setDescripcion(e.target.value)}
          placeholder="Cuéntale al técnico qué pasa y qué necesitas" />
        <div className="ayuda"><span className="pequeno suave">{descripcion.length} / 500</span> · mínimo 10 caracteres</div></div>
      <div className="campo"><label htmlFor="n-fotos">Fotos del problema (opcional, hasta 3)</label>
        {fotos.length > 0 && <div className="fotos">{fotos.map((f, i) => <FotoElegida key={`${f.name}-${f.size}-${i}`} archivo={f} alQuitar={() => setFotos(fotos.filter((_, j) => j !== i))} />)}</div>}
        {fotos.length < MAX_FOTOS && <input id="n-fotos" type="file" multiple accept="image/jpeg,image/png,image/webp" onChange={elegirFotos} />}
        <div className="ayuda">Una foto ayuda a los técnicos a cotizar mejor. Se reducen solas si pesan más de 2 MB.</div></div>
      <div className="campo"><Ubicacion valor={ubicacion} alCambiar={setUbicacion} compacto /></div>
      <p className="pequeno suave">Los técnicos tienen 10 minutos para ofertar. Después tendrás 5 minutos para elegir.</p>
      <button className="btn acento ancho" type="submit" disabled={enviando}>{enviando ? 'Publicando…' : 'Publicar solicitud'}</button>
    </form>
  );
}
