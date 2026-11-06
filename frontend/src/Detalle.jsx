import { useEffect, useRef, useState } from 'react';
import { api, nuevaLlave } from './api.js';
import { useSesion } from './sesion.jsx';
import { Cargando, Insignia, useUI } from './ui.jsx';
import { ir, useAhora, usePolling } from './hooks.js';
import { PASOS, cop, cuentaAtras, estrellasTexto, hora, metros } from './util.js';

export default function Detalle({ id }) {
  const { yo } = useSesion();
  const { datos: s, error, cargando, recargar } = usePolling(() => api('/service-requests/' + id), 6000, [id]);

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

function Fotos({ s, esHogar, recargar }) {
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
      for (const f of lista) {
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
    const f = ev.target.files[0];
    if (!f) return;
    if (f.size > 2 * 1024 * 1024) { avisar('Cada foto puede pesar hasta 2 MB', 'error'); ev.target.value = ''; return; }
    const form = new FormData(); form.append('foto', f);
    const r = await intentar(() => api(`/service-requests/${s.id}/photos`, { metodo: 'POST', form }));
    ev.target.value = '';
    if (r) { avisar('Foto agregada', 'ok'); setVersion((v) => v + 1); recargar(); }
  };

  return (
    <div className="tarjeta">
      <h3>Fotos</h3>
      {fotos.length
        ? <div className="fotos">{fotos.map((f) => <img key={f.id} className="foto" alt="Foto de la solicitud" loading="lazy" src={urls[f.id]} />)}</div>
        : <p className="suave">Una foto ayuda a los técnicos a cotizar mejor.</p>}
      {puede && (
        <div className="campo"><label htmlFor="foto-nueva">Agregar foto ({fotos.length}/3, hasta 2 MB)</label>
          <input id="foto-nueva" type="file" accept="image/jpeg,image/png,image/webp" onChange={subir} /></div>
      )}
    </div>
  );
}

function Ofertas({ s, recargar }) {
  const { avisar, intentar } = useUI();
  const { datos, cargando } = usePolling(() => api(`/service-requests/${s.id}/applications`), 6000, [s.id, s.estado]);
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

  const traer = async () => {
    try {
      const nuevos = await api(`/service-requests/${s.id}/messages?despues=${ultimo.current}`);
      if (nuevos.length) { ultimo.current = nuevos[nuevos.length - 1].id; setMensajes((m) => [...m, ...nuevos]); }
    } catch { /* el chat se reintenta solo */ }
  };
  useEffect(() => {
    ultimo.current = 0; setMensajes([]);
    traer();
    const t = setInterval(traer, 4000);
    return () => clearInterval(t);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [s.id]);
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
