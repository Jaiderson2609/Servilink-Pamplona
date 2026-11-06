import { useState } from 'react';
import { useSesion } from './sesion.jsx';
import { useUI } from './ui.jsx';
import { traducir } from './api.js';

export default function Auth() {
  const [modo, setModo] = useState('ingresar');
  return (
    <div className="estrecho">
      <div className="hero">
        <h1>Técnicos de confianza, cerca de ti</h1>
        <p>Publica lo que necesitas, recibe ofertas de técnicos de Pamplona y elige tú.</p>
      </div>
      <div className="tarjeta">
        <div className="pestanas" role="tablist">
          <button className="pestana" role="tab" aria-selected={modo === 'ingresar'} onClick={() => setModo('ingresar')}>Ingresar</button>
          <button className="pestana" role="tab" aria-selected={modo === 'registro'} onClick={() => setModo('registro')}>Crear cuenta</button>
        </div>
        {modo === 'ingresar' ? <Login /> : <Registro />}
      </div>
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
      <button className="btn ancho" type="submit" disabled={enviando}>Ingresar</button>
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
      <button className="btn ancho" type="submit" disabled={enviando}>Crear cuenta</button>
    </form>
  );
}
