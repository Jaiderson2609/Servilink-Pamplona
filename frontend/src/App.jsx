import { useSesion } from './sesion.jsx';
import { useRuta, ir } from './hooks.js';
import Auth from './Auth.jsx';
import Hogar from './Hogar.jsx';
import Tecnico from './Tecnico.jsx';
import Admin from './Admin.jsx';
import Detalle from './Detalle.jsx';
import { Cargando } from './ui.jsx';

export default function App() {
  const { yo, restaurando, salir } = useSesion();
  const ruta = useRuta();

  return (
    <>
      <header className="cab">
        <div className="cab-dentro">
          <button className="marca" onClick={() => ir('#/')} aria-label="Ir al inicio">
            <img src="/favicon.svg" alt="" width="30" height="30" />ServiLink Pamplona
          </button>
          <span className="espacio" />
          {yo && (
            <div className="yo">
              <span className="nombre">{yo.nombre}</span>
              <span className="insignia rol">{yo.rol.toLowerCase()}</span>
              <button className="btn sec chico" onClick={() => salir()}>Salir</button>
            </div>
          )}
        </div>
      </header>
      <main className="contenedor" id="vista">
        {!yo ? (restaurando ? <Cargando /> : <Auth />) : ruta.vista === 's' ? <Detalle id={ruta.id} />
          : yo.rol === 'HOGAR' ? <Hogar />
          : yo.rol === 'TECNICO' ? <Tecnico tab={ruta.vista === 't' ? ruta.tab : null} />
          : <Admin tab={ruta.vista === 'a' ? ruta.tab : null} />}
      </main>
    </>
  );
}
