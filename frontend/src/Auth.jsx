import { useEffect, useRef, useState } from 'react';
import { useSesion } from './sesion.jsx';
import { useUI } from './ui.jsx';
import { api, traducir } from './api.js';
import { Marca } from './Lateral.jsx';

const OFICIOS = ['Plomería', 'Electricidad', 'Cerrajería', 'Gas', 'Pintura'];
const PUNTOS = [
  ['M4 5h16v11H8l-4 4z', 'Publica en un minuto'],
  ['M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z', 'Recibe ofertas y negocia el precio'],
  ['M12 3l8 3v6c0 4.5-3.2 7.9-8 9-4.8-1.1-8-4.5-8-9V6zM8.5 12l2.5 2.5 4.5-5', 'Elige entre técnicos verificados'],
];

export default function Auth() {
  const [modo, setModo] = useState('ingresar');
  return (
    <div className="ingreso">
      <section className="ingreso-izq">
        <Marca claro />
        <div className="ingreso-texto">
          <h1>Técnicos de confianza, cerca de ti</h1>
          <p>Publica lo que necesitas, recibe ofertas de técnicos de Pamplona y elige tú.</p>
          <ul className="chips">
            {OFICIOS.map((o) => <li key={o}>{o}</li>)}
            <li className="mas">y más</li>
          </ul>
          <ul className="puntos">
            {PUNTOS.map(([d, t]) => (
              <li key={t}>
                <span className="punto-icono"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d={d} /></svg></span>
                {t}
              </li>
            ))}
          </ul>
        </div>
        <p className="ingreso-pie">Hecho en Pamplona, Norte de Santander</p>
      </section>
      <section className="ingreso-der">
        <div className="ingreso-tarjeta">
          <h2>{modo === 'ingresar' ? 'Bienvenido de nuevo' : 'Crea tu cuenta'}</h2>
          <p className="suave">{modo === 'ingresar' ? 'Ingresa a tu cuenta para continuar.' : 'Es gratis y toma un minuto.'}</p>
          <div className="pestanas" role="tablist">
            <button className="pestana" role="tab" aria-selected={modo === 'ingresar'} onClick={() => setModo('ingresar')}>Ingresar</button>
            <button className="pestana" role="tab" aria-selected={modo === 'registro'} onClick={() => setModo('registro')}>Crear cuenta</button>
          </div>
          {modo === 'ingresar' ? <Login /> : <Registro />}
          <Google />
        </div>
      </section>
    </div>
  );
}

function Login() {
  const { entrar } = useSesion();
  const [correo, setCorreo] = useState('');
  const [clave, setClave] = useState('');
  const [error, setError] = useState('');
  const [enviando, setEnviando] = useState(false);

  const enviar = async (ev) => {
    ev.preventDefault();
    setError(''); setEnviando(true);
    try { await entrar(correo, clave); } catch (e) { setError(e.code === 'UNAUTHENTICATED' ? 'Correo o clave incorrectos' : traducir(e)); setEnviando(false); }
  };

  return (
    <form onSubmit={enviar}>
      <div className="campo"><label htmlFor="l-correo">Correo</label>
        <input id="l-correo" type="email" autoComplete="username" required value={correo} onChange={(e) => setCorreo(e.target.value)} /></div>
      <div className="campo"><label htmlFor="l-clave">Clave</label>
        <input id="l-clave" type="password" autoComplete="current-password" required value={clave} onChange={(e) => setClave(e.target.value)} /></div>
      <div className="error-campo" role="alert">{error}</div>
      <button className="btn acento ancho" type="submit" disabled={enviando}>Ingresar</button>
    </form>
  );
}

function Registro() {
  const { registrar } = useSesion();
  const { avisar } = useUI();
  const [f, setF] = useState({ rol: 'HOGAR', nombre: '', correo: '', telefono: '', clave: '', aceptaTerminos: false });
  const [error, setError] = useState('');
  const [enviando, setEnviando] = useState(false);
  const poner = (k) => (e) => setF({ ...f, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value });

  const enviar = async (ev) => {
    ev.preventDefault();
    setError(''); setEnviando(true);
    try { await registrar(f); avisar('Cuenta creada. ¡Bienvenido!', 'ok'); } catch (e) { setError(traducir(e)); setEnviando(false); }
  };

  return (
    <form onSubmit={enviar}>
      <div className="campo"><label htmlFor="r-rol">Quiero</label>
        <select id="r-rol" value={f.rol} onChange={poner('rol')}>
          <option value="HOGAR">Necesito un técnico (hogar)</option><option value="TECNICO">Soy técnico</option>
        </select></div>
      <div className="campo"><label htmlFor="r-nombre">Nombre</label>
        <input id="r-nombre" maxLength={80} autoComplete="name" required value={f.nombre} onChange={poner('nombre')} /></div>
      <div className="campo"><label htmlFor="r-correo">Correo</label>
        <input id="r-correo" type="email" autoComplete="email" required value={f.correo} onChange={poner('correo')} /></div>
      <div className="campo"><label htmlFor="r-tel">Teléfono</label>
        <input id="r-tel" type="tel" autoComplete="tel" required placeholder="3001234567" value={f.telefono} onChange={poner('telefono')} /></div>
      <div className="campo"><label htmlFor="r-clave">Clave</label>
        <input id="r-clave" type="password" autoComplete="new-password" minLength={8} maxLength={72} required value={f.clave} onChange={poner('clave')} />
        <div className="ayuda">Mínimo 8 caracteres</div></div>
      <div className="campo"><label className="chequeo" htmlFor="r-acepta">
        <input id="r-acepta" type="checkbox" required checked={f.aceptaTerminos} onChange={poner('aceptaTerminos')} />
        Acepto la política de tratamiento de datos y los términos de uso</label></div>
      <div className="error-campo" role="alert">{error}</div>
      <button className="btn acento ancho" type="submit" disabled={enviando}>Crear cuenta</button>
    </form>
  );
}

const URL_GOOGLE = 'https://accounts.google.com/gsi/client';

function cargarGoogle() {
  if (window.google?.accounts?.id) return Promise.resolve();
  return new Promise((ok, falla) => {
    let s = document.querySelector(`script[src="${URL_GOOGLE}"]`);
    if (!s) { s = document.createElement('script'); s.src = URL_GOOGLE; s.async = true; document.head.appendChild(s); }
    s.addEventListener('load', ok, { once: true });
    s.addEventListener('error', falla, { once: true });
  });
}

/** Botón "Continuar con Google". Solo aparece si el servidor tiene un ID de cliente configurado. */
function Google() {
  const { entrarConGoogle } = useSesion();
  const [clienteId, setClienteId] = useState('');
  const [credencial, setCredencial] = useState(null); // cuenta nueva: falta pedir rol, teléfono y términos
  const [error, setError] = useState('');
  const caja = useRef(null);
  const alEntrar = useRef(null);

  alEntrar.current = async (datos) => {
    setError('');
    try { await entrarConGoogle(datos); } catch (e) {
      if (e.code === 'GOOGLE_REGISTRATION_REQUIRED' && !datos.rol) setCredencial(datos.credencial);
      else setError(traducir(e));
    }
  };

  useEffect(() => {
    let vivo = true;
    api('/auth/google/config').then((c) => { if (vivo) setClienteId(c?.clientId || ''); }).catch(() => {});
    return () => { vivo = false; };
  }, []);

  useEffect(() => {
    if (!clienteId || credencial) return undefined;
    let vivo = true;
    cargarGoogle().then(() => {
      if (!vivo || !caja.current) return;
      window.google.accounts.id.initialize({ client_id: clienteId, callback: (r) => alEntrar.current({ credencial: r.credential }) });
      window.google.accounts.id.renderButton(caja.current, { theme: 'outline', size: 'large', text: 'continue_with', shape: 'rectangular', locale: 'es', width: 300 });
    }).catch(() => { if (vivo) setError('No se pudo cargar el ingreso con Google.'); });
    return () => { vivo = false; };
  }, [clienteId, credencial]);

  if (!clienteId) return null;
  if (credencial) return <CompletarGoogle credencial={credencial} enviar={(d) => alEntrar.current(d)} cancelar={() => setCredencial(null)} />;
  return (
    <div className="google">
      <div className="ayuda centrado">o</div>
      {/* El botón que se ve es de la plataforma; el de Google va encima, invisible y recortado al mismo tamaño. */}
      <div className="google-boton">
        <span className="btn sec ancho" aria-hidden="true">Continuar con Google</span>
        <div ref={caja} className="google-capa" />
      </div>
      <div className="error-campo" role="alert">{error}</div>
    </div>
  );
}

function CompletarGoogle({ credencial, enviar, cancelar }) {
  const [f, setF] = useState({ rol: 'HOGAR', telefono: '', aceptaTerminos: false });
  const [enviando, setEnviando] = useState(false);
  const poner = (k) => (e) => setF({ ...f, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value });
  const mandar = async (ev) => {
    ev.preventDefault();
    setEnviando(true);
    await enviar({ credencial, ...f });
    setEnviando(false);
  };
  return (
    <form onSubmit={mandar} className="google">
      <p className="ayuda">Casi listo. Para crear tu cuenta con Google completa estos datos.</p>
      <div className="campo"><label htmlFor="g-rol">Quiero</label>
        <select id="g-rol" value={f.rol} onChange={poner('rol')}>
          <option value="HOGAR">Necesito un técnico (hogar)</option><option value="TECNICO">Soy técnico</option>
        </select></div>
      <div className="campo"><label htmlFor="g-tel">Teléfono</label>
        <input id="g-tel" type="tel" autoComplete="tel" required placeholder="3001234567" value={f.telefono} onChange={poner('telefono')} /></div>
      <div className="campo"><label className="chequeo" htmlFor="g-acepta">
        <input id="g-acepta" type="checkbox" required checked={f.aceptaTerminos} onChange={poner('aceptaTerminos')} />
        Acepto la política de tratamiento de datos y los términos de uso</label></div>
      <button className="btn ancho" type="submit" disabled={enviando}>Crear cuenta con Google</button>
      <button className="btn sec ancho" type="button" onClick={cancelar}>Cancelar</button>
    </form>
  );
}
