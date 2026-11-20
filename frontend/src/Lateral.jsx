import { Icono } from './iconos.jsx';
import { ROLES } from './util.js';

export function Marca({ claro }) {
  return (
    <div className={'lat-marca' + (claro ? ' claro' : '')}>
      <span className="lat-logo"><img src="/favicon.svg" alt="" width="26" height="26" /></span>
      <span>ServiLink<br />Pamplona</span>
    </div>
  );
}

/** Panel lateral de navegación (pantallas anchas): opciones del rol, tarjeta de la persona y salida. En el celular se usa BarraMovil. */
export default function Lateral({ yo, base, items, activa, salir }) {
  return (
    <aside id="lat-panel" className="lateral">
      <div className="lat-arriba">
        <Marca claro />
        <nav className="lat-nav" aria-label="Principal">
          {items.map(([k, t]) => (
            <a key={k} href={`#/${base}/${k}`} className="lat-item" aria-current={k === activa ? 'page' : undefined}>
              <Icono k={k} />{t}
            </a>
          ))}
        </nav>
      </div>
      <div className="lat-yo">
        <div className="lat-persona">
          <span className="lat-avatar" aria-hidden="true">{(yo.nombre || '?').trim().charAt(0).toUpperCase()}</span>
          <span className="lat-datos">
            <span className="lat-nombre">{yo.nombre}</span>
            <span className="lat-rol">{ROLES[yo.rol] || yo.rol}</span>
          </span>
        </div>
        <button type="button" className="lat-salir" onClick={() => salir()}>Salir</button>
      </div>
    </aside>
  );
}
