import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import 'leaflet/dist/leaflet.css';
import './estilos.css';
import App from './App.jsx';
import { SesionProvider } from './sesion.jsx';
import { UIProvider, useUI } from './ui.jsx';

function Raiz() {
  const { avisar } = useUI();
  return <SesionProvider alVencer={(m) => avisar(m)}><App /></SesionProvider>;
}

createRoot(document.getElementById('raiz')).render(
  <StrictMode><UIProvider><Raiz /></UIProvider></StrictMode>);

// PWA: el service worker solo se registra en producción (en desarrollo estorba al recargar).
if (import.meta.env.PROD && 'serviceWorker' in navigator) {
  window.addEventListener('load', () => { navigator.serviceWorker.register('/sw.js').catch(() => {}); });
}
