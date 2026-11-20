/* Service worker de los avisos push (Firebase Cloud Messaging).
   No guarda nada en caché ni toca la API: solo muestra el aviso que manda el servidor y, al tocarlo, abre la plataforma.
   Vive en su propio alcance para no interferir con el de la PWA (sw.js). */
self.addEventListener('push', (evento) => {
  let datos = {};
  try {
    const p = evento.data ? evento.data.json() : {};
    datos = { ...(p.notification || {}), ...(p.data || {}) };
  } catch { /* aviso sin datos legibles: se muestra el texto por defecto */ }
  const titulo = datos.titulo || datos.title || 'ServiLink Pamplona';
  const cuerpo = datos.cuerpo || datos.body || 'Tienes una novedad en ServiLink';
  evento.waitUntil(self.registration.showNotification(titulo, { body: cuerpo, icon: '/icons/icon-192.png' }));
});

self.addEventListener('notificationclick', (evento) => {
  evento.notification.close();
  evento.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((ventanas) => {
      const abierta = ventanas.find((v) => new URL(v.url).origin === self.location.origin);
      return abierta ? abierta.focus() : self.clients.openWindow('/');
    }));
});
