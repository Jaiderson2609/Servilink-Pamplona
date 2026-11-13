import { useEffect, useState } from 'react';
import { api } from './api.js';
import { useSesion } from './sesion.jsx';
import { Cargando, Pestanas, useUI } from './ui.jsx';
import { ir, usePolling } from './hooks.js';
import { PAMPLONA, metros, nombrePila } from './util.js';
import { ListaSolicitudes } from './Solicitudes.jsx';
import Ubicacion from './Ubicacion.jsx';
import Billetera, { referenciaDeRetorno } from './Billetera.jsx';

const TABS = [['oportunidades', 'Oportunidades'], ['servicios', 'Mis servicios'], ['billetera', 'Billetera'], ['zona', 'Zona de trabajo']];

export default function Tecnico({ tab }) {
  const { yo } = useSesion();
  const retorno = referenciaDeRetorno();
  // Wompi devuelve al técnico a la raíz del sitio con ?recarga=…: se lo lleva a la billetera a ver el resultado.
  const activa = retorno ? 'billetera' : TABS.some(([k]) => k === tab) ? tab : 'oportunidades';
  useEffect(() => { if (retorno && tab !== 'billetera') ir('#/t/billetera'); }, [retorno, tab]);
  return (
    <>
      <h1>Hola, {nombrePila(yo.nombre)}</h1>
      <Pestanas items={TABS} activa={activa} alCambiar={(k) => ir(`#/t/${k}`)} />
      {activa === 'oportunidades' && <Oportunidades />}
      {activa === 'servicios' && <Servicios />}
      {activa === 'billetera' && <Billetera />}
      {activa === 'zona' && <Zona />}
    </>
  );
}

function Oportunidades() {
  const { datos, error, cargando } = usePolling(async () => {
    const [abiertas, zona] = await Promise.all([api('/service-requests/open'), api('/technicians/me/location').catch(() => null)]);
    return { abiertas, zona };
  }, 8000, [], (e) => e.tipo === 'abiertas' || e.tipo === 'solicitud');
  if (cargando) return <Cargando />;
  if (error || !datos) return <div className="vacio">No pudimos cargar las oportunidades. {error?.message}</div>;
  return (
    <>
      {datos.zona
        ? <p className="suave">Mostrando solicitudes a menos de {datos.zona.radioKm} km de tu zona de trabajo, de la más cercana a la más lejana.</p>
        : <div className="tarjeta"><strong>Aún no defines tu zona de trabajo. </strong>Mientras tanto ves todas las solicitudes abiertas. <button className="btn chico" onClick={() => ir('#/t/zona')}>Definir zona</button></div>}
      <ListaSolicitudes lista={datos.abiertas} vacio="No hay solicitudes abiertas dentro de tu zona por ahora. Esta lista se actualiza sola." extra={(s) => metros(s.distanciaM)} />
    </>
  );
}

function Servicios() {
  const { datos, error, cargando } = usePolling(() => api('/service-requests/mine'), 15000, [], (e) => e.tipo === 'solicitud');
  if (cargando) return <Cargando />;
  if (error || !datos) return <div className="vacio">No pudimos cargar tus servicios. {error?.message}</div>;
  return <ListaSolicitudes lista={datos} vacio="Aún no tienes servicios. Postúlate a una solicitud abierta." />;
}

function Zona() {
  const { avisar, intentar } = useUI();
  const { datos, cargando } = usePolling(() => api('/technicians/me/location').catch(() => null), 0, []);
  if (cargando) return <Cargando />;
  return <FormZona inicial={datos} avisar={avisar} intentar={intentar} />;
}

function FormZona({ inicial, avisar, intentar }) {
  const [punto, setPunto] = useState(inicial ? { lat: inicial.lat, lng: inicial.lng } : PAMPLONA);
  const [radio, setRadio] = useState(inicial ? inicial.radioKm : 5);

  const guardar = async (ev) => {
    ev.preventDefault();
    const r = await intentar(() => api('/technicians/me/location', { metodo: 'PUT', json: { lat: punto.lat, lng: punto.lng, radioKm: Number(radio) } }));
    if (r) avisar('Zona de trabajo guardada', 'ok');
  };

  return (
    <form className="tarjeta estrecho" onSubmit={guardar}>
      <h2>Zona de trabajo</h2>
      <p className="suave">Dinos desde dónde sales y hasta dónde te desplazas. Solo verás solicitudes dentro de ese radio.</p>
      <div className="campo"><label>Punto base</label><Ubicacion valor={punto} alCambiar={setPunto} radioKm={Number(radio) || undefined} /></div>
      <div className="campo"><label htmlFor="z-radio">Radio (km)</label>
        <input id="z-radio" type="number" min={1} max={50} required value={radio} onChange={(e) => setRadio(e.target.value)} />
        <div className="ayuda">De 1 a 50 km</div></div>
      <button className="btn ancho" type="submit">Guardar zona</button>
    </form>
  );
}
