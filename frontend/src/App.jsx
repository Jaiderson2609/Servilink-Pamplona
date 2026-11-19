import { useSesion } from './sesion.jsx';
import { useRuta } from './hooks.js';
import Auth from './Auth.jsx';
import Hogar from './Hogar.jsx';
import Tecnico from './Tecnico.jsx';
import Admin from './Admin.jsx';
import Detalle from './Detalle.jsx';
import Lateral from './Lateral.jsx';
import { menuDe } from './menus.js';
import { Cargando } from './ui.jsx';

export default function App() {
  const { yo, restaurando, salir } = useSesion();
  const ruta = useRuta();

  if (!yo) return restaurando ? <main className="contenedor" id="vista"><Cargando /></main> : <Auth />;

  const menu = menuDe(yo.rol, ruta);
  return (
    <div className="app">
      <Lateral yo={yo} base={menu.base} items={menu.items} activa={menu.activa} salir={salir} />
      <main className="contenedor" id="vista">
        {ruta.vista === 's' ? <Detalle id={ruta.id} />
          : yo.rol === 'HOGAR' ? <Hogar tab={ruta.vista === 'h' ? ruta.tab : null} />
          : yo.rol === 'TECNICO' ? <Tecnico tab={ruta.vista === 't' ? ruta.tab : null} />
          : <Admin tab={ruta.vista === 'a' ? ruta.tab : null} />}
      </main>
    </div>
  );
}
