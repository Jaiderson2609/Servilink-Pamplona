import { useEffect, useRef, useState } from 'react';
import { api, nuevaLlave } from './api.js';
import { useSesion } from './sesion.jsx';
import { Cargando, Insignia, useUI } from './ui.jsx';
import { ir, useAhora, useEventos, usePolling } from './hooks.js';
import { RESPALDO_MS, eventosConectados } from './eventos.js';
import Fotos from './Fotos.jsx';
import { PASOS, cop, cuentaAtras, estrellasTexto, hora, metros } from './util.js';

export default function Detalle({ id }) {
  const { yo } = useSesion();
  const { datos: s, error, cargando, recargar } = usePolling(() => api('/service-requests/' + id), 6000, [id], (e) => e.tipo === 'solicitud' && e.id === id);

  return (
    <>
      <button className="btn sec chico" onClick={() => ir('#/')}>← Volver</button>
      <div className="pila">
        {cargando ? <Cargando /> : error || !s ? (
          <div className="vacio">{error?.status === 404 || error?.status === 403 ? 'No encontramos esta solicitud o no tienes acceso a ella.' : `No pudimos cargar la solicitud. ${error?.message ?? ''}`}</div>
        ) : <Contenido s={s} yo={yo} recargar={recargar} />}
      </div>
    </>
  );
}

function Contenido({ s, yo, recargar }) {
  const { avisar, intentar, pedir } = useUI();
  const esHogar = yo.rol === 'HOGAR' && s.hogarId === yo.id;
  const esTecnicoAsignado = yo.rol === 'TECNICO' && s.tecnicoId === yo.id;
  const activa = s.estado === 'E01' || s.estado === 'E02';
  const idx = PASOS.findIndex(([e]) => e === s.estado);
  const ruta = (r) => `/service-requests/${s.id}/${r}`;

  const accion = (fn, ok) => async () => {
    const r = await intentar(fn);
    if (r !== undefined) { if (ok) avisar(ok, 'ok'); recargar(); }
  };
  const patch = (r, json) => () => api(ruta(r), { metodo: 'PATCH', json }).then((x) => x ?? true);

  const cancelar = async () => {
    const gratis = activa;
    const motivo = await pedir({
      titulo: 'Cancelar el servicio',
      texto: gratis ? 'Cancelar ahora no tiene costo.' : 'Cuéntale a la otra parte por qué cancelas. Se libera la comisión retenida del técnico.',
      etiqueta: gratis ? 'Motivo (opcional)' : 'Motivo', opcional: gratis, confirmar: 'Cancelar servicio', peligro: true,
    });
    if (motivo === null) return;
    accion(patch('cancel', motivo ? { motivo } : {}), 'Servicio cancelado')();
  };

  const puedeCancelar = (esHogar && (activa || s.estado === 'E03' || s.estado === 'E04')) || (esTecnicoAsignado && (s.estado === 'E03' || s.estado === 'E04'));
  const hayAcciones = puedeCancelar || (esTecnicoAsignado && (s.estado === 'E03' || s.estado === 'E04')) || (esHogar && s.estado === 'E04') || yo.rol === 'ADMIN';

  return (
    <>
      <div className="tarjeta">
        <div className="fila entre"><h1>{s.oficio || 'Servicio'}</h1><Insignia estado={s.estado} /></div>
        <p>{s.descripcion}</p>
        {idx >= 0 && (
          <ol className="pasos" aria-label="Avance del servicio">
            {PASOS.map(([e, t], i) => (
              <li key={e} className={'paso' + (i < idx ? ' hecho' : i === idx ? ' hecho actual' : '')} aria-current={i === idx ? 'step' : undefined}>{t}</li>
            ))}
          </ol>
        )}
        {s.estado === 'E06' && <p className="suave">Nadie fue elegido a tiempo y la solicitud expiró.</p>}
        {s.estado === 'E07' && <p className="suave">Cancelada{s.motivoCancelacion ? ': ' + s.motivoCancelacion : ''}.</p>}
        {s.precioAcordadoCop ? <p><strong>Precio acordado: </strong>{cop(s.precioAcordadoCop)}</p> : null}
        {s.distanciaM != null && <p className="suave">A {metros(s.distanciaM)} de tu zona de trabajo</p>}
        {activa && <Cuenta expiraEn={s.expiraEn} />}
        {s.contacto && (
          <div className="contacto"><strong>{esHogar ? 'Tu técnico: ' : 'Tu cliente: '}</strong>{s.contacto.nombre} · <a href={'tel:' + s.contacto.telefono}>{s.contacto.telefono}</a></div>
        )}
      </div>

      <Fotos s={s} esHogar={esHogar} recargar={recargar} />
      {esHogar && activa && <Ofertas s={s} recargar={recargar} />}
      {yo.rol === 'TECNICO' && !esTecnicoAsignado && activa && <FormPostular s={s} recargar={recargar} />}

      {hayAcciones && (
        <div className="tarjeta fila">
          {esTecnicoAsignado && s.estado === 'E03' && <button className="btn" onClick={accion(patch('arrive'), 'Llegada registrada')}>Ya llegué al domicilio</button>}
          {esTecnicoAsignado && s.estado === 'E04' && !s.terminadoEn && <button className="btn" onClick={accion(patch('complete'), 'Avisamos al hogar que terminaste')}>Terminé el trabajo</button>}
          {esTecnicoAsignado && s.estado === 'E04' && s.terminadoEn && <p className="suave">Esperando que el hogar confirme el trabajo.</p>}
          {esHogar && s.estado === 'E04' && s.terminadoEn && <button className="btn" onClick={accion(patch('confirm'), 'Servicio confirmado')}>Confirmar trabajo terminado</button>}
          {esHogar && s.estado === 'E04' && !s.terminadoEn && <p className="suave">Cuando el técnico termine, podrás confirmarlo aquí.</p>}
          {puedeCancelar && <button className="btn peligro" onClick={cancelar}>Cancelar</button>}
          {yo.rol === 'ADMIN' && <p className="suave">Vista de administrador. Para cancelar una disputa usa el panel de solicitudes.</p>}
        </div>
      )}

      {s.tecnicoId != null && (esHogar || esTecnicoAsignado) && <Chat s={s} />}
      {s.estado === 'E05' && (esHogar || esTecnicoAsignado) && <Calificar s={s} yo={yo} />}
    </>
  );
}

function Cuenta({ expiraEn }) {
  const ahora = useAhora(1000);
  const t = cuentaAtras(expiraEn, ahora);
  return <div><span className="pequeno suave">{t ? `Tiempo restante: ${t}` : 'El plazo terminó'}</span></div>;
}

function Ofertas({ s, recargar }) {
  const { avisar, intentar } = useUI();
  const { datos, cargando } = usePolling(() => api(`/service-requests/${s.id}/applications`), 6000, [s.id, s.estado], (e) => e.tipo === 'solicitud' && e.id === s.id);
  const llaves = useRef({});
  if (cargando) return <Cargando />;
  const activas = (datos || []).filter((o) => o.estado === 'ACTIVA');

  const elegir = async (o) => {
    llaves.current[o.id] ??= nuevaLlave(); // un doble toque sobre "Elegir" usa la misma clave y no cobra dos veces
    const r = await intentar(() => api(`/applications/${o.id}/select`, { metodo: 'PATCH', llave: llaves.current[o.id] }));
    if (r) { avisar('Técnico asignado', 'ok'); recargar(); }
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
            <button className="btn" onClick={() => elegir(o)}>Elegir a este técnico</button>
          </li>))}</ul>
      ) : <div className="vacio">Todavía no hay ofertas. Esta página se actualiza sola.</div>}
    </div>
  );
}

function FormPostular({ s, recargar }) {
  const { avisar, intentar } = useUI();
  const [precio, setPrecio] = useState('');
  const [mensaje, setMensaje] = useState('');
  const llave = useRef(nuevaLlave());

  const enviar = async (ev) => {
    ev.preventDefault();
    const r = await intentar(() => api(`/service-requests/${s.id}/applications`, {
      metodo: 'POST', llave: llave.current, json: { precioCop: Number(precio), mensaje: mensaje.trim() || null } }));
    if (r) { llave.current = nuevaLlave(); avisar('Tu oferta fue enviada', 'ok'); recargar(); }
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

function Chat({ s }) {
  const { intentar } = useUI();
  const [mensajes, setMensajes] = useState([]);
  const [texto, setTexto] = useState('');
  const ultimo = useRef(0);
  const caja = useRef(null);
  const abierto = s.estado === 'E03' || s.estado === 'E04';

  const ultimaTraida = useRef(0);
  const traer = async () => {
    ultimaTraida.current = Date.now();
    try {
      const nuevos = await api(`/service-requests/${s.id}/messages?despues=${ultimo.current}`);
      if (nuevos.length) { ultimo.current = nuevos[nuevos.length - 1].id; setMensajes((m) => [...m, ...nuevos]); }
    } catch { /* el chat se reintenta solo */ }
  };
  useEffect(() => {
    ultimo.current = 0; setMensajes([]);
    traer();
    // El aviso del servidor trae los mensajes al instante; el sondeo queda de respaldo si el canal está caído.
    const t = setInterval(() => { if (!eventosConectados() || Date.now() - ultimaTraida.current > RESPALDO_MS) traer(); }, 4000);
    return () => clearInterval(t);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [s.id]);
  useEventos((e) => e.tipo === 'chat' && e.id === s.id, traer);
  useEffect(() => { if (caja.current) caja.current.scrollTop = caja.current.scrollHeight; }, [mensajes]);

  const enviar = async (ev) => {
    ev.preventDefault();
    const t = texto.trim();
    if (!t) return;
    const r = await intentar(() => api(`/service-requests/${s.id}/messages`, { metodo: 'POST', json: { texto: t } }));
    if (r) { setTexto(''); traer(); }
  };

  return (
    <div className="tarjeta">
      <h2>Chat</h2>
      <div className="chat" role="log" aria-label="Mensajes" ref={caja}>
        {mensajes.map((m) => <div key={m.id} className={'burbuja' + (m.mio ? ' mia' : '')}>{m.texto}<span className="hora">{hora(m.creadoEn)}</span></div>)}
      </div>
      {abierto ? (
        <form className="fila" onSubmit={enviar}>
          <div className="campo"><input maxLength={500} autoComplete="off" placeholder="Escribe un mensaje" aria-label="Mensaje" value={texto} onChange={(e) => setTexto(e.target.value)} /></div>
          <button className="btn" type="submit">Enviar</button>
        </form>
      ) : <p className="pequeno suave">El chat se cerró porque el servicio terminó.</p>}
    </div>
  );
}

function Calificar({ s, yo }) {
  const { avisar, intentar } = useUI();
  const [puntos, setPuntos] = useState(0);
  const [comentario, setComentario] = useState('');
  const [listo, setListo] = useState(false);
  const destino = yo.rol === 'HOGAR' ? 'al técnico' : 'al cliente';

  if (listo) return <div className="tarjeta"><h2>Calificación enviada</h2><p className="suave">Gracias por ayudar a que ServiLink sea más confiable.</p></div>;

  const enviar = async (ev) => {
    ev.preventDefault();
    if (!puntos) return avisar('Elige de 1 a 5 estrellas', 'error');
    try {
      await api(`/service-requests/${s.id}/ratings`, { metodo: 'POST', json: { estrellas: puntos, comentario: comentario.trim() || null } });
      avisar('¡Gracias por calificar!', 'ok'); setListo(true);
    } catch (e) {
      if (e.status === 409) { avisar('Ya calificaste este servicio'); setListo(true); } else await intentar(() => { throw e; });
    }
  };

  return (
    <form className="tarjeta" onSubmit={enviar}>
      <h2>Califica {destino}</h2>
      <div className="estrellas" role="radiogroup" aria-label="Calificación">
        {[1, 2, 3, 4, 5].map((n) => (
          <button key={n} type="button" className={'estrella' + (n <= puntos ? ' on' : '')} role="radio" aria-checked={n === puntos} aria-label={`${n} estrellas`} onClick={() => setPuntos(n)}>★</button>
        ))}
      </div>
      <div className="campo"><textarea maxLength={300} placeholder="Comentario (opcional)" aria-label="Comentario" value={comentario} onChange={(e) => setComentario(e.target.value)} /></div>
      <button className="btn" type="submit">Enviar calificación</button>
    </form>
  );
}
