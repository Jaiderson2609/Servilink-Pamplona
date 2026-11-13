import { useRef, useState } from 'react';
import { api, nuevaLlave } from './api.js';
import { useSesion } from './sesion.jsx';
import { Cargando, useUI } from './ui.jsx';
import { usePolling, ir } from './hooks.js';
import { ACTIVAS, PAMPLONA, nombrePila } from './util.js';
import { ListaSolicitudes } from './Solicitudes.jsx';
import Ubicacion from './Ubicacion.jsx';

export default function Hogar() {
  const { yo } = useSesion();
  const { datos, error, cargando } = usePolling(async () => {
    const [mias, oficios] = await Promise.all([api('/service-requests/mine'), api('/trades')]);
    return { mias, oficios };
  }, 15000, [], (e) => e.tipo === 'solicitud');

  if (cargando) return <Cargando />;
  if (error || !datos) return <div className="vacio">No pudimos cargar tus solicitudes. {error?.message}</div>;
  const activa = datos.mias.find((s) => ACTIVAS.includes(s.estado));

  return (
    <>
      <h1>Hola, {nombrePila(yo.nombre)}</h1>
      {activa ? (
        <div className="tarjeta">
          <h2>Tu solicitud activa</h2>
          <p className="suave">Puedes tener una solicitud activa a la vez. Termínala o cancélala para crear otra.</p>
          <ListaSolicitudes lista={[activa]} vacio="" />
        </div>
      ) : <NuevaSolicitud oficios={datos.oficios} />}
      <h2>Mis solicitudes</h2>
      <ListaSolicitudes lista={datos.mias.filter((s) => s !== activa)} vacio="Aún no tienes solicitudes anteriores." />
    </>
  );
}

function NuevaSolicitud({ oficios }) {
  const { avisar, intentar } = useUI();
  const [oficioId, setOficioId] = useState(oficios[0]?.id ?? '');
  const [descripcion, setDescripcion] = useState('');
  const [ubicacion, setUbicacion] = useState(PAMPLONA);
  const [enviando, setEnviando] = useState(false);
  const llave = useRef(nuevaLlave()); // el mismo intento reutiliza la clave: un doble toque no crea dos solicitudes

  const enviar = async (ev) => {
    ev.preventDefault();
    if (descripcion.trim().length < 10) { avisar('La descripción debe tener al menos 10 caracteres', 'error'); return; }
    setEnviando(true);
    const s = await intentar(() => api('/service-requests', {
      metodo: 'POST', llave: llave.current,
      json: { oficioId: Number(oficioId), descripcion: descripcion.trim(), ubicacion },
    }));
    setEnviando(false);
    if (s) {
      llave.current = nuevaLlave();
      avisar('Solicitud publicada. Los técnicos cercanos ya pueden verla.', 'ok');
      ir(`#/s/${s.id}`);
    }
  };

  return (
    <form className="tarjeta" onSubmit={enviar}>
      <h2>Nueva solicitud</h2>
      <div className="campo"><label htmlFor="n-oficio">¿Qué necesitas?</label>
        <select id="n-oficio" required value={oficioId} onChange={(e) => setOficioId(e.target.value)}>
          {oficios.map((o) => <option key={o.id} value={o.id}>{o.nombre}</option>)}
        </select></div>
      <div className="campo"><label htmlFor="n-desc">Descripción</label>
        <textarea id="n-desc" minLength={10} maxLength={500} required value={descripcion} onChange={(e) => setDescripcion(e.target.value)}
          placeholder="Cuéntale al técnico qué pasa y qué necesitas" />
        <div className="ayuda"><span className="pequeno suave">{descripcion.length} / 500</span> · mínimo 10 caracteres</div></div>
      <div className="campo"><label>¿Dónde?</label><Ubicacion valor={ubicacion} alCambiar={setUbicacion} /></div>
      <p className="pequeno suave">Los técnicos tienen 10 minutos para ofertar. Después tendrás 5 minutos para elegir.</p>
      <button className="btn ancho" type="submit" disabled={enviando}>Publicar solicitud</button>
    </form>
  );
}
