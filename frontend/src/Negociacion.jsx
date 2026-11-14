import { useRef, useState } from 'react';
import { api, nuevaLlave } from './api.js';
import { Cargando, useUI } from './ui.jsx';
import { usePolling } from './hooks.js';
import { cop, estrellasTexto } from './util.js';

const delTecnico = (o) => (o.ofertas ?? []).filter((x) => x.autor === 'TECNICO').length;
const contraoferta = (o) => (o.ofertas ?? []).find((x) => x.autor === 'HOGAR');
const pendiente = (o) => (o.ofertas ?? []).find((x) => x.autor === 'HOGAR' && x.estado === 'PENDIENTE');

function Historial({ ofertas, yoAutor }) {
  if (!ofertas || ofertas.length < 2) return null;
  const quien = (a) => (a === yoAutor ? 'Tú' : a === 'HOGAR' ? 'El hogar' : 'El técnico');
  const estado = { PENDIENTE: '', ACEPTADA: ' (aceptada)', RECHAZADA: ' (reemplazada)' };
  return (
    <ul className="pequeno suave" aria-label="Historial de ofertas">
      {ofertas.map((x) => <li key={x.id}>{quien(x.autor)}: {cop(x.montoCop)}{estado[x.estado]}</li>)}
    </ul>
  );
}

function FormMonto({ etiqueta, boton, alEnviar }) {
  const [monto, setMonto] = useState('');
  const id = useRef('m' + nuevaLlave()).current;
  const enviar = async (ev) => {
    ev.preventDefault();
    if (await alEnviar(Number(monto))) setMonto('');
  };
  return (
    <form className="fila" onSubmit={enviar}>
      <div className="campo"><label htmlFor={id}>{etiqueta}</label>
        <input id={id} type="number" min={1000} max={5000000} step={500} required value={monto} onChange={(e) => setMonto(e.target.value)} /></div>
      <button className="btn sec chico" type="submit">{boton}</button>
    </form>
  );
}

/** Ofertas que ve el hogar: contraoferta (una por técnico) y elección con el precio vigente. */
export function OfertasHogar({ s, recargar }) {
  const { avisar, intentar } = useUI();
  const { datos, cargando, recargar: recargarOfertas } = usePolling(() => api(`/service-requests/${s.id}/applications`), 6000, [s.id, s.estado], (e) => e.tipo === 'solicitud' && e.id === s.id);
  const llaves = useRef({});
  if (cargando) return <Cargando />;
  const activas = (datos || []).filter((o) => o.estado === 'ACTIVA');

  const elegir = async (o) => {
    llaves.current[o.id] ??= nuevaLlave(); // un doble toque sobre "Elegir" usa la misma clave y no cobra dos veces
    const r = await intentar(() => api(`/applications/${o.id}/select`, { metodo: 'PATCH', llave: llaves.current[o.id] }));
    if (r) { avisar('Técnico asignado', 'ok'); recargar(); }
  };
  const contraofertar = (o) => async (monto) => {
    const r = await intentar(() => api(`/applications/${o.id}/offers`, { metodo: 'POST', llave: nuevaLlave(), json: { montoCop: monto } }));
    if (r) { avisar('Contraoferta enviada', 'ok'); recargarOfertas(); }
    return Boolean(r);
  };

  return (
    <div className="tarjeta">
      <h2>Ofertas ({activas.length})</h2>
      {activas.length ? (
        <ul className="lista-sol">{activas.map((o) => (
          <li key={o.id} className="tarjeta">
            <div className="fila entre"><strong>{o.tecnico}</strong><span className="dinero">{cop(o.precioCop)}</span></div>
            <div className="pequeno suave">{estrellasTexto(o.calificacionPromedio, o.calificaciones)}</div>
            {o.mensaje && <p>“{o.mensaje}”</p>}
            <Historial ofertas={o.ofertas} yoAutor="HOGAR" />
            {pendiente(o) && <p className="suave">Enviaste una contraoferta de {cop(pendiente(o).montoCop)}. Esperando la respuesta del técnico.</p>}
            {!contraoferta(o) && <FormMonto etiqueta="Tu contraoferta (COP)" boton="Contraofertar" alEnviar={contraofertar(o)} />}
            <button className="btn" disabled={Boolean(pendiente(o))} onClick={() => elegir(o)}>Elegir a este técnico</button>
          </li>))}</ul>
      ) : <div className="vacio">Todavía no hay ofertas. Esta página se actualiza sola.</div>}
    </div>
  );
}

/** Lo que ve el técnico en una solicitud abierta: postularse o, si ya se postuló, responder y retirar. */
export function PanelTecnico({ s, recargar }) {
  const { datos, cargando, recargar: refrescar } = usePolling(
    () => api(`/service-requests/${s.id}/applications/mine`).catch((e) => { if (e.status === 404) return null; throw e; }),
    6000, [s.id, s.estado], (e) => e.tipo === 'solicitud' && e.id === s.id);
  if (cargando) return <Cargando />;
  const alCambiar = () => { refrescar(); recargar(); };
  if (!datos) return <FormPostular s={s} alCambiar={alCambiar} />;
  return <MiPostulacion p={datos} alCambiar={alCambiar} />;
}

function MiPostulacion({ p, alCambiar }) {
  const { avisar, intentar, pedir } = useUI();
  if (p.estado === 'RETIRADA') return <div className="tarjeta"><p className="suave">Retiraste tu postulación.</p></div>;
  if (p.estado !== 'ACTIVA') return <div className="tarjeta"><p className="suave">Tu postulación ya no está activa.</p></div>;
  const pend = pendiente(p);

  const aceptar = async () => {
    const r = await intentar(() => api(`/offers/${pend.id}/accept`, { metodo: 'PATCH', llave: nuevaLlave() }));
    if (r) { avisar('Aceptaste la contraoferta', 'ok'); alCambiar(); }
  };
  const final = async (monto) => {
    const r = await intentar(() => api(`/applications/${p.id}/offers`, { metodo: 'POST', llave: nuevaLlave(), json: { montoCop: monto } }));
    if (r) { avisar('Oferta final enviada', 'ok'); alCambiar(); }
    return Boolean(r);
  };
  const retirar = async () => {
    const ok = await pedir({ titulo: 'Retirar postulación', texto: 'El hogar ya no verá tu oferta y no podrás postularte otra vez a esta solicitud.', confirmar: 'Retirar', peligro: true });
    if (ok === null) return;
    const r = await intentar(() => api(`/applications/${p.id}/withdraw`, { metodo: 'PATCH', llave: nuevaLlave() }));
    if (r) { avisar('Postulación retirada', 'ok'); alCambiar(); }
  };

  return (
    <div className="tarjeta">
      <h2>Tu postulación</h2>
      <div className="fila entre"><span>Precio vigente</span><span className="dinero">{cop(p.precioCop)}</span></div>
      <Historial ofertas={p.ofertas} yoAutor="TECNICO" />
      {pend ? (
        <>
          <p><strong>El hogar propone {cop(pend.montoCop)}.</strong> Acéptalo o responde con tu oferta final.</p>
          <button className="btn" onClick={aceptar}>Aceptar {cop(pend.montoCop)}</button>
          {delTecnico(p) < 2 && <FormMonto etiqueta="Mi oferta final (COP)" boton="Enviar oferta final" alEnviar={final} />}
        </>
      ) : <p className="suave">Esperando la decisión del hogar. Esta página se actualiza sola.</p>}
      <button className="btn peligro chico" onClick={retirar}>Retirar postulación</button>
    </div>
  );
}

function FormPostular({ s, alCambiar }) {
  const { avisar, intentar } = useUI();
  const [precio, setPrecio] = useState('');
  const [mensaje, setMensaje] = useState('');
  const llave = useRef(nuevaLlave());

  const enviar = async (ev) => {
    ev.preventDefault();
    const r = await intentar(() => api(`/service-requests/${s.id}/applications`, {
      metodo: 'POST', llave: llave.current, json: { precioCop: Number(precio), mensaje: mensaje.trim() || null } }));
    if (r) { llave.current = nuevaLlave(); avisar('Tu oferta fue enviada', 'ok'); alCambiar(); }
  };

  return (
    <form className="tarjeta" onSubmit={enviar}>
      <h2>Enviar mi oferta</h2>
      <div className="campo"><label htmlFor="p-precio">Tu precio (COP)</label>
        <input id="p-precio" type="number" min={1000} max={5000000} step={500} required value={precio} onChange={(e) => setPrecio(e.target.value)} />
        <div className="ayuda">Si te eligen se retiene el 10 % como comisión de tu saldo</div></div>
      <div className="campo"><label htmlFor="p-msg">Mensaje (opcional)</label>
        <textarea id="p-msg" maxLength={200} placeholder="Ej.: Llego en 20 minutos con las herramientas" value={mensaje} onChange={(e) => setMensaje(e.target.value)} /></div>
      <button className="btn ancho" type="submit">Postularme</button>
    </form>
  );
}
