import { useEffect, useState } from 'react';
import { api } from './api.js';
import { Cargando, Insignia, Pestanas, useUI } from './ui.jsx';
import { ir, usePolling } from './hooks.js';
import { ACCIONES, ACTIVAS, ESTADOS, cop, fecha, recortar } from './util.js';

const TABS = [['resumen', 'Resumen'], ['usuarios', 'Usuarios'], ['solicitudes', 'Solicitudes'], ['tecnicos', 'Verificación'], ['auditoria', 'Auditoría']];

export default function Admin({ tab }) {
  const activa = TABS.some(([k]) => k === tab) ? tab : 'resumen';
  return (
    <>
      <h1>Panel de administración</h1>
      <Pestanas items={TABS} activa={activa} alCambiar={(k) => ir(`#/a/${k}`)} />
      {activa === 'resumen' && <Resumen />}
      {activa === 'usuarios' && <Usuarios />}
      {activa === 'solicitudes' && <Solicitudes />}
      {activa === 'tecnicos' && <Verificaciones />}
      {activa === 'auditoria' && <Auditoria />}
    </>
  );
}

const Tarjeta = ({ n, t }) => <div className="tarjeta estadistica"><div className="n">{n}</div><div className="suave">{t}</div></div>;

function Resumen() {
  const { datos: r, error, cargando } = usePolling(() => api('/admin/summary'), 30000, []);
  if (cargando) return <Cargando />;
  if (error || !r) return <div className="vacio">No pudimos cargar el resumen. {error?.message}</div>;
  return (
    <>
      <h2>Solicitudes por estado</h2>
      <div className="rejilla">{Object.entries(r.solicitudesPorEstado).map(([e, n]) => <Tarjeta key={e} n={n} t={ESTADOS[e][0]} />)}</div>
      <h2>Plataforma</h2>
      <div className="rejilla">
        <Tarjeta n={r.usuariosPorRol.HOGAR} t="Hogares" /><Tarjeta n={r.usuariosPorRol.TECNICO} t="Técnicos" />
        <Tarjeta n={r.cuentasSuspendidas} t="Cuentas suspendidas" /><Tarjeta n={cop(r.comisionesCobradasCop)} t="Comisiones cobradas" />
        <Tarjeta n={cop(r.comisionesRetenidasCop)} t="Comisiones retenidas" />
      </div>
    </>
  );
}

function Paginador({ pagina, total, tamano, ir: irA }) {
  const ult = Math.max(0, Math.ceil(total / tamano) - 1);
  return (
    <div className="fila entre">
      <span className="pequeno suave">{total} en total · página {pagina + 1} de {ult + 1}</span>
      <div className="fila">
        <button className="btn sec chico" disabled={pagina <= 0} onClick={() => irA(pagina - 1)}>Anterior</button>
        <button className="btn sec chico" disabled={pagina >= ult} onClick={() => irA(pagina + 1)}>Siguiente</button>
      </div>
    </div>
  );
}

function Usuarios() {
  const { avisar, intentar, pedir } = useUI();
  const TAM = 10;
  const [filtro, setFiltro] = useState({ rol: '', q: '', pagina: 0 });
  const [borrador, setBorrador] = useState({ rol: '', q: '' });
  const { datos: r, error, cargando, recargar } = usePolling(
    () => api(`/admin/users?pagina=${filtro.pagina}&tamano=${TAM}` + (filtro.rol ? `&rol=${filtro.rol}` : '') + (filtro.q ? `&q=${encodeURIComponent(filtro.q)}` : '')),
    0, [filtro]);

  const suspender = async (u) => {
    const motivo = await pedir({ titulo: `Suspender a ${u.nombre}`, texto: 'La persona pierde el acceso de inmediato. Queda registrado en la auditoría.', etiqueta: 'Motivo', confirmar: 'Suspender', peligro: true });
    if (!motivo) return;
    const ok = await intentar(() => api(`/admin/users/${u.id}/status`, { metodo: 'PATCH', json: { status: 'SUSPENDED', reason: motivo } }));
    if (ok) { avisar('Cuenta suspendida', 'ok'); recargar(); }
  };
  const reactivar = async (u) => {
    const ok = await intentar(() => api(`/admin/users/${u.id}/status`, { metodo: 'PATCH', json: { status: 'ACTIVE' } }));
    if (ok) { avisar('Cuenta reactivada', 'ok'); recargar(); }
  };

  return (
    <>
      <form className="fila" onSubmit={(ev) => { ev.preventDefault(); setFiltro({ ...borrador, pagina: 0 }); }}>
        <div className="campo"><input type="search" placeholder="Buscar por nombre o correo" aria-label="Buscar usuarios" value={borrador.q} onChange={(e) => setBorrador({ ...borrador, q: e.target.value })} /></div>
        <div className="campo"><select aria-label="Rol" value={borrador.rol} onChange={(e) => setBorrador({ ...borrador, rol: e.target.value })}>
          <option value="">Todos</option><option value="HOGAR">Hogares</option><option value="TECNICO">Técnicos</option><option value="ADMIN">Administradores</option></select></div>
        <button className="btn" type="submit">Buscar</button>
      </form>
      {cargando ? <Cargando /> : error || !r ? <div className="vacio">No pudimos cargar los usuarios. {error?.message}</div> : (
        <>
          <div className="tarjeta tabla-scroll">
            {r.items.length ? (
              <table>
                <thead><tr><th>Nombre</th><th>Correo</th><th>Rol</th><th>Cuenta</th><th></th></tr></thead>
                <tbody>{r.items.map((u) => (
                  <tr key={u.id}>
                    <td>{u.nombre}</td><td>{u.correo}</td><td>{u.rol.toLowerCase()}</td>
                    <td>{u.suspendido ? <span className="insignia cancelada" title={u.motivoSuspension || ''}>Suspendida</span> : <span className="insignia">Activa</span>}</td>
                    <td>{u.rol === 'ADMIN' ? null : u.suspendido
                      ? <button className="btn sec chico" onClick={() => reactivar(u)}>Reactivar</button>
                      : <button className="btn peligro chico" onClick={() => suspender(u)}>Suspender</button>}</td>
                  </tr>))}</tbody>
              </table>
            ) : <div className="vacio">Sin resultados.</div>}
          </div>
          <Paginador pagina={filtro.pagina} total={r.total} tamano={TAM} ir={(p) => setFiltro({ ...filtro, pagina: p })} />
        </>
      )}
    </>
  );
}

function Solicitudes() {
  const { avisar, intentar, pedir } = useUI();
  const TAM = 10;
  const [filtro, setFiltro] = useState({ estado: '', pagina: 0 });
  const { datos: r, error, cargando, recargar } = usePolling(
    () => api(`/admin/service-requests?pagina=${filtro.pagina}&tamano=${TAM}` + (filtro.estado ? `&estado=${filtro.estado}` : '')), 0, [filtro]);

  const cancelar = async (s) => {
    const motivo = await pedir({ titulo: `Cancelar la solicitud #${s.id}`, texto: 'Si estaba asignada, se libera la comisión del técnico y se avisa a ambas partes.', etiqueta: 'Motivo', confirmar: 'Cancelar solicitud', peligro: true });
    if (!motivo) return;
    const ok = await intentar(() => api(`/admin/service-requests/${s.id}/cancel`, { metodo: 'PATCH', json: { motivo } }));
    if (ok) { avisar('Solicitud cancelada', 'ok'); recargar(); }
  };

  return (
    <>
      <div className="campo"><select aria-label="Estado" value={filtro.estado} onChange={(e) => setFiltro({ estado: e.target.value, pagina: 0 })}>
        <option value="">Todos los estados</option>
        {Object.entries(ESTADOS).map(([k, v]) => <option key={k} value={k}>{k} · {v[0]}</option>)}</select></div>
      {cargando ? <Cargando /> : error || !r ? <div className="vacio">No pudimos cargar las solicitudes. {error?.message}</div> : (
        <>
          <div className="tarjeta tabla-scroll">
            {r.items.length ? (
              <table>
                <thead><tr><th>#</th><th>Estado</th><th>Descripción</th><th>Técnico</th><th></th></tr></thead>
                <tbody>{r.items.map((s) => (
                  <tr key={s.id}>
                    <td>{s.id}</td><td><Insignia estado={s.estado} /></td><td>{recortar(s.descripcion, 70)}</td><td>{s.tecnicoId ?? '—'}</td>
                    <td className="fila"><button className="btn sec chico" onClick={() => ir(`#/s/${s.id}`)}>Ver</button>
                      {ACTIVAS.includes(s.estado) && <button className="btn peligro chico" onClick={() => cancelar(s)}>Cancelar</button>}</td>
                  </tr>))}</tbody>
              </table>
            ) : <div className="vacio">Sin solicitudes en este estado.</div>}
          </div>
          <Paginador pagina={filtro.pagina} total={r.total} tamano={TAM} ir={(p) => setFiltro({ ...filtro, pagina: p })} />
        </>
      )}
    </>
  );
}

function Auditoria() {
  const TAM = 15;
  const [pagina, setPagina] = useState(0);
  const { datos: r, error, cargando } = usePolling(() => api(`/admin/audit-events?pagina=${pagina}&tamano=${TAM}`), 0, [pagina]);
  if (cargando) return <Cargando />;
  if (error || !r) return <div className="vacio">No pudimos cargar la auditoría. {error?.message}</div>;
  return (
    <>
      <div className="tarjeta tabla-scroll">
        {r.items.length ? (
          <table>
            <thead><tr><th>Fecha</th><th>Acción</th><th>Objetivo</th><th>Detalle</th></tr></thead>
            <tbody>{r.items.map((a) => (
              <tr key={a.id}><td>{fecha(a.creadaEn)}</td><td>{ACCIONES[a.accion] || a.accion}</td><td>{`${a.objetivoTipo.toLowerCase()} #${a.objetivoId}`}</td><td>{a.detalle || ''}</td></tr>
            ))}</tbody>
          </table>
        ) : <div className="vacio">Aún no hay acciones registradas.</div>}
      </div>
      <Paginador pagina={pagina} total={r.total} tamano={TAM} ir={setPagina} />
    </>
  );
}

const TIPOS_DOC = [['CEDULA_FRENTE', 'Cédula (frente)'], ['CEDULA_REVERSO', 'Cédula (reverso)'], ['SELFIE', 'Selfie con cédula']];

/** Imágenes privadas: se piden con el token y se muestran desde la memoria del navegador, sin guardarlas. */
function Documentos({ id }) {
  const [urls, setUrls] = useState({});
  useEffect(() => {
    let vivo = true;
    const creadas = [];
    Promise.all(TIPOS_DOC.map(([t]) => api(`/admin/technicians/${id}/documents/${t}`, { blob: true }).then((b) => [t, URL.createObjectURL(b)]).catch(() => [t, null])))
      .then((pares) => { pares.forEach(([, u]) => u && creadas.push(u)); if (vivo) setUrls(Object.fromEntries(pares)); });
    return () => { vivo = false; creadas.forEach((u) => URL.revokeObjectURL(u)); };
  }, [id]);
  return (
    <div className="fila">{TIPOS_DOC.map(([t, nombre]) => (
      <figure key={t}>{urls[t] ? <img src={urls[t]} alt={nombre} style={{ maxWidth: '220px', maxHeight: '220px' }} /> : <div className="vacio">Cargando…</div>}<figcaption className="pequeno suave">{nombre}</figcaption></figure>
    ))}</div>
  );
}

function Verificaciones() {
  const { avisar, intentar, pedir } = useUI();
  const [abierto, setAbierto] = useState(null);
  const { datos: r, error, cargando, recargar } = usePolling(() => api('/admin/technicians?estado=PENDIENTE&tamano=50'), 0, []);

  const decidir = async (t, aprobar) => {
    let motivo = null;
    if (!aprobar) {
      motivo = await pedir({ titulo: `Rechazar a ${t.nombre}`, texto: 'El técnico verá este motivo y podrá reenviar sus documentos.', etiqueta: 'Motivo', confirmar: 'Rechazar', peligro: true });
      if (!motivo) return;
    }
    const ok = await intentar(() => api(`/admin/technicians/${t.id}/verification`, { metodo: 'PATCH', json: { decision: aprobar ? 'APPROVED' : 'REJECTED', motivo } }));
    if (ok) { avisar(aprobar ? 'Técnico aprobado' : 'Verificación rechazada', 'ok'); setAbierto(null); recargar(); }
  };

  if (cargando) return <Cargando />;
  if (error || !r) return <div className="vacio">No pudimos cargar las verificaciones. {error?.message}</div>;
  return (
    <div className="pila">
      <h2>Técnicos por verificar ({r.total})</h2>
      {r.items.length ? r.items.map((t) => (
        <div className="tarjeta" key={t.id}>
          <div className="fila entre"><strong>{t.nombre}</strong><span className="pequeno suave">{t.correo} · enviado {fecha(t.enviadaEn)}</span></div>
          {abierto === t.id ? <Documentos id={t.id} /> : null}
          <div className="fila">
            <button className="btn sec chico" onClick={() => setAbierto(abierto === t.id ? null : t.id)}>{abierto === t.id ? 'Ocultar documentos' : 'Ver documentos'}</button>
            <button className="btn chico" onClick={() => decidir(t, true)}>Aprobar</button>
            <button className="btn peligro chico" onClick={() => decidir(t, false)}>Rechazar</button>
          </div>
        </div>)) : <div className="vacio">No hay técnicos esperando verificación.</div>}
    </div>
  );
}
