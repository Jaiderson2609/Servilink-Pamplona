import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { pintar, sesionComo, simularApi } from './apoyo.jsx';
import App from '../App.jsx';
import Detalle from '../Detalle.jsx';
import Hogar from '../Hogar.jsx';
import Admin from '../Admin.jsx';
import { setToken } from '../api.js';
import { WebSocketFalso } from './setup.js';
import { act } from '@testing-library/react';

vi.mock('../Mapa.jsx', () => ({ default: () => <div data-testid="mapa" /> })); // Leaflet necesita un navegador real

const HOGAR = { id: 1, rol: 'HOGAR', nombre: 'Ana Pérez' };
const TECNICO = { id: 2, rol: 'TECNICO', nombre: 'Tec Uno' };
const ADMIN = { id: 9, rol: 'ADMIN', nombre: 'Admin' };
const base = { descripcion: 'Cambiar un tomacorriente roto', oficio: 'Electricista', oficioId: 2, expiraEn: new Date(Date.now() + 300000).toISOString(), hogarId: 1, tecnicoId: null, contacto: null };

beforeEach(() => setToken(null));

describe('ingreso', () => {
  it('inicia sesión y entra al inicio del hogar', async () => {
    simularApi({
      'POST /auth/login': { accessToken: 'tok', tokenType: 'Bearer', expiresIn: 900, usuarioId: 1, rol: 'HOGAR', nombre: 'Ana Pérez' },
      'GET /service-requests/mine': [], 'GET /trades': [{ id: 2, nombre: 'Electricista' }],
    });
    pintar(<App />);
    await screen.findByLabelText('Correo');
    await userEvent.type(screen.getByLabelText('Correo'), 'ana@correo.co');
    await userEvent.type(screen.getByLabelText('Clave'), 'clave-segura-1');
    await userEvent.click(screen.getByRole('button', { name: 'Ingresar', hidden: false, description: '' }).closest('form').querySelector('button[type=submit]'));
    expect(await screen.findByText('Hola, Ana')).toBeInTheDocument();
    expect(sessionStorage.getItem('servilink')).toContain('"rol":"HOGAR"');
  });

  it('muestra el error cuando la clave es incorrecta y no guarda sesión', async () => {
    simularApi({ 'POST /auth/login': [401, { code: 'UNAUTHENTICATED', message: 'Correo o clave incorrectos', traceId: 't' }] });
    pintar(<App />);
    await screen.findByLabelText('Correo');
    await userEvent.type(screen.getByLabelText('Correo'), 'ana@correo.co');
    await userEvent.type(screen.getByLabelText('Clave'), 'mala-clave-123');
    await userEvent.click(document.querySelector('form button[type=submit]'));
    expect(await screen.findByText('Correo o clave incorrectos')).toBeInTheDocument();
    expect(sessionStorage.getItem('servilink')).toBeNull();
  });

  it('el registro exige aceptar los términos y manda el cuerpo del DDS', async () => {
    const llamadas = simularApi({
      'POST /auth/register': [201, { id: 5, rol: 'TECNICO' }],
      'POST /auth/login': { accessToken: 'tok', tokenType: 'Bearer', expiresIn: 900, usuarioId: 5, rol: 'TECNICO', nombre: 'Nuevo Tec' },
      'GET /service-requests/open': [], 'GET /technicians/me/location': [404, { code: 'NOT_FOUND', message: 'x', traceId: 't' }],
    });
    pintar(<App />);
    await screen.findByLabelText('Correo');
    await userEvent.click(screen.getByRole('tab', { name: 'Crear cuenta' }));
    await userEvent.selectOptions(screen.getByLabelText('Quiero'), 'TECNICO');
    await userEvent.type(screen.getByLabelText('Nombre'), 'Nuevo Tec');
    await userEvent.type(screen.getByLabelText('Correo'), 'nuevo@correo.co');
    await userEvent.type(screen.getByLabelText('Teléfono'), '3001234567');
    await userEvent.type(screen.getByLabelText('Clave'), 'clave-segura-1');
    await userEvent.click(screen.getByLabelText(/Acepto la política/));
    await userEvent.click(document.querySelector('form button[type=submit]'));
    await screen.findByText('Hola, Nuevo');
    const reg = llamadas.find((l) => l.ruta === '/auth/register');
    expect(reg.cuerpo).toMatchObject({ rol: 'TECNICO', nombre: 'Nuevo Tec', aceptaTerminos: true });
  });
});

describe('hogar', () => {
  it('con una solicitud activa no ofrece crear otra', async () => {
    sesionComo(HOGAR);
    simularApi({ 'GET /service-requests/mine': [{ ...base, id: 3, estado: 'E01' }], 'GET /trades': [{ id: 2, nombre: 'Electricista' }] });
    pintar(<Hogar />);
    expect(await screen.findByText('Tu solicitud activa')).toBeInTheDocument();
    expect(screen.queryByText('Nueva solicitud')).not.toBeInTheDocument();
  });

  it('publica una solicitud con Idempotency-Key y la ubicación elegida', async () => {
    sesionComo(HOGAR);
    const llamadas = simularApi({
      'GET /service-requests/mine': [], 'GET /trades': [{ id: 2, nombre: 'Electricista' }],
      'POST /service-requests': [201, { ...base, id: 11, estado: 'E01' }],
    });
    pintar(<Hogar />);
    await userEvent.type(await screen.findByLabelText('Descripción'), 'Cambiar un tomacorriente roto');
    await userEvent.click(screen.getByRole('button', { name: 'Publicar solicitud' }));
    await waitFor(() => expect(llamadas.some((l) => l.metodo === 'POST' && l.ruta === '/service-requests')).toBe(true));
    const l = llamadas.find((x) => x.metodo === 'POST' && x.ruta === '/service-requests');
    expect(l.cab['Idempotency-Key']).toMatch(/^[A-Za-z0-9_-]{8,80}$/);
    expect(l.cuerpo).toEqual({ oficioId: 2, descripcion: 'Cambiar un tomacorriente roto', ubicacion: { lat: 7.3772, lng: -72.6473 } });
    await waitFor(() => expect(window.location.hash).toBe('#/s/11'));
  });

  it('el botón de publicar exige descripción de al menos 10 caracteres', async () => {
    sesionComo(HOGAR);
    const llamadas = simularApi({ 'GET /service-requests/mine': [], 'GET /trades': [{ id: 2, nombre: 'Electricista' }] });
    pintar(<Hogar />);
    await userEvent.type(await screen.findByLabelText('Descripción'), 'corto');
    await userEvent.click(screen.getByRole('button', { name: 'Publicar solicitud' }));
    expect(llamadas.some((l) => l.metodo === 'POST')).toBe(false); // la validación del navegador lo detiene
  });
});

describe('detalle de la solicitud', () => {
  it('el hogar ve las ofertas y elige con PATCH /applications/{id}/select', async () => {
    sesionComo(HOGAR);
    const llamadas = simularApi({
      'GET /service-requests/4': { ...base, id: 4, estado: 'E02' },
      'GET /service-requests/4/photos': [],
      'GET /service-requests/4/applications': [{ id: 21, tecnicoId: 2, tecnico: 'Tec Uno', precioCop: 20000, mensaje: 'Llego en 20 min', estado: 'ACTIVA', calificacionPromedio: 4.5, calificaciones: 2 }],
      'PATCH /applications/21/select': { solicitudId: 4, estado: 'E03', comisionRetenidaCop: 2000, contacto: { nombre: 'Tec Uno', telefono: '3001234567' } },
    });
    pintar(<Detalle id={4} />);
    expect(await screen.findByText('Ofertas (1)')).toBeInTheDocument();
    expect(screen.getByText('★ 4.5 (2)')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Elegir a este técnico' }));
    await waitFor(() => expect(llamadas.some((l) => l.metodo === 'PATCH' && l.ruta === '/applications/21/select')).toBe(true));
    expect(llamadas.find((l) => l.ruta === '/applications/21/select').cab['Idempotency-Key']).toMatch(/^[A-Za-z0-9_-]{8,80}$/);
  });

  it('un error de ventana vencida se muestra en español', async () => {
    sesionComo(HOGAR);
    simularApi({
      'GET /service-requests/4': { ...base, id: 4, estado: 'E02' }, 'GET /service-requests/4/photos': [],
      'GET /service-requests/4/applications': [{ id: 21, tecnicoId: 2, tecnico: 'Tec Uno', precioCop: 20000, estado: 'ACTIVA', calificaciones: 0 }],
      'PATCH /applications/21/select': [410, { code: 'SERVICE_EXPIRED', message: 'La ventana de selección terminó', traceId: 't' }],
    });
    pintar(<Detalle id={4} />);
    await userEvent.click(await screen.findByRole('button', { name: 'Elegir a este técnico' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('El plazo de esta solicitud ya terminó.');
  });

  it('el técnico asignado marca la llegada con PATCH …/arrive y ve el contacto del cliente', async () => {
    sesionComo(TECNICO);
    const llamadas = simularApi({
      'GET /service-requests/5': { ...base, id: 5, estado: 'E03', tecnicoId: 2, precioAcordadoCop: 20000, contacto: { nombre: 'Ana Pérez', telefono: '3110000001' } },
      'GET /service-requests/5/photos': [], 'GET /service-requests/5/messages': [],
      'PATCH /service-requests/5/arrive': { ...base, id: 5, estado: 'E04', tecnicoId: 2 },
    });
    pintar(<Detalle id={5} />);
    expect(await screen.findByText('Tu cliente:')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '3110000001' })).toHaveAttribute('href', 'tel:3110000001');
    await userEvent.click(screen.getByRole('button', { name: 'Ya llegué al domicilio' }));
    await waitFor(() => expect(llamadas.some((l) => l.metodo === 'PATCH' && l.ruta === '/service-requests/5/arrive')).toBe(true));
  });

  it('cancelar un servicio asignado exige el motivo en un diálogo', async () => {
    sesionComo(HOGAR);
    const llamadas = simularApi({
      'GET /service-requests/6': { ...base, id: 6, estado: 'E03', tecnicoId: 2, contacto: { nombre: 'Tec Uno', telefono: '3001234567' } },
      'GET /service-requests/6/photos': [], 'GET /service-requests/6/messages': [],
      'PATCH /service-requests/6/cancel': { ...base, id: 6, estado: 'E07', motivoCancelacion: 'Ya lo resolví yo mismo' },
    });
    pintar(<Detalle id={6} />);
    await userEvent.click(await screen.findByRole('button', { name: 'Cancelar' }));
    const dlg = await screen.findByRole('dialog', { hidden: true });
    await userEvent.type(within(dlg).getByLabelText('Motivo'), 'no');
    await userEvent.click(within(dlg).getByRole('button', { name: 'Cancelar servicio' }));
    expect(within(dlg).getByText('Escribe al menos 5 caracteres')).toBeInTheDocument();
    expect(llamadas.some((l) => l.ruta === '/service-requests/6/cancel')).toBe(false);
    await userEvent.type(within(dlg).getByLabelText('Motivo'), ' hay otro motivo');
    await userEvent.click(within(dlg).getByRole('button', { name: 'Cancelar servicio' }));
    await waitFor(() => expect(llamadas.find((l) => l.ruta === '/service-requests/6/cancel')?.cuerpo).toEqual({ motivo: 'no hay otro motivo' }));
  });

  it('el chat se cierra cuando el servicio terminó y se puede calificar', async () => {
    sesionComo(HOGAR);
    const llamadas = simularApi({
      'GET /service-requests/7': { ...base, id: 7, estado: 'E05', tecnicoId: 2 },
      'GET /service-requests/7/photos': [],
      'GET /service-requests/7/messages': [{ id: 1, autorId: 1, texto: 'Hola', creadoEn: '2026-11-01T10:00:00Z', mio: true }],
      'POST /service-requests/7/ratings': [201, { id: 1, solicitudId: 7, destinatarioId: 2, estrellas: 5, creadaEn: '2026-11-01T11:00:00Z' }],
    });
    pintar(<Detalle id={7} />);
    expect(await screen.findByText('El chat se cerró porque el servicio terminó.')).toBeInTheDocument();
    expect(await screen.findByText('Hola')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Enviar calificación' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Elige de 1 a 5 estrellas');
    await userEvent.click(screen.getByRole('radio', { name: '5 estrellas' }));
    await userEvent.click(screen.getByRole('button', { name: 'Enviar calificación' }));
    expect(await screen.findByText('Calificación enviada')).toBeInTheDocument();
    expect(llamadas.find((l) => l.ruta === '/service-requests/7/ratings').cuerpo).toEqual({ estrellas: 5, comentario: null });
  });

  it('una solicitud ajena muestra un mensaje claro y no los datos', async () => {
    sesionComo(HOGAR);
    simularApi({ 'GET /service-requests/8': [403, { code: 'FORBIDDEN', message: 'No participas en esta solicitud', traceId: 't' }] });
    pintar(<Detalle id={8} />);
    expect(await screen.findByText(/no tienes acceso/)).toBeInTheDocument();
  });
});

describe('tiempo real', () => {
  it('al iniciar sesión abre el canal con el token y al salir lo cierra', async () => {
    simularApi({
      'POST /auth/login': { accessToken: 'tok-123', tokenType: 'Bearer', expiresIn: 900, usuarioId: 1, rol: 'HOGAR', nombre: 'Ana Pérez' },
      'GET /service-requests/mine': [], 'GET /trades': [{ id: 2, nombre: 'Electricista' }],
    });
    pintar(<App />);
    await screen.findByLabelText('Correo');
    expect(WebSocketFalso.instancias).toHaveLength(0); // sin sesión no hay canal
    await userEvent.type(screen.getByLabelText('Correo'), 'ana@correo.co');
    await userEvent.type(screen.getByLabelText('Clave'), 'clave-segura-1');
    await userEvent.click(screen.getByRole('button', { name: 'Ingresar', hidden: false, description: '' }).closest('form').querySelector('button[type=submit]'));
    await screen.findByText('Hola, Ana');
    expect(WebSocketFalso.instancias).toHaveLength(1);
    const canal = WebSocketFalso.instancias[0];
    act(() => canal.abrir());
    expect(JSON.parse(canal.enviados[0])).toEqual({ tipo: 'auth', token: 'tok-123' });
    await userEvent.click(screen.getByRole('button', { name: 'Salir' }));
    expect(canal.readyState).toBe(3);
  });

  it('un aviso de chat trae el mensaje nuevo sin esperar al sondeo', async () => {
    sesionComo(HOGAR);
    let mensajes = [];
    const llamadas = simularApi({
      'GET /service-requests/9': { ...base, id: 9, estado: 'E03', tecnicoId: 2, contacto: { nombre: 'Tec Uno', telefono: '3001234567' } },
      'GET /service-requests/9/photos': [],
      'GET /service-requests/9/messages': () => mensajes,
    });
    pintar(<Detalle id={9} />);
    await screen.findByText(/Cambiar un tomacorriente roto/);
    // El canal lo abre SesionProvider al haber sesión.
    await waitFor(() => expect(WebSocketFalso.instancias).toHaveLength(1));
    const canal = WebSocketFalso.instancias[0];
    act(() => { canal.abrir(); canal.recibir({ tipo: 'listo' }); });
    const consultas = () => llamadas.filter((l) => l.ruta.startsWith('/service-requests/9/messages')).length;
    const antes = consultas();
    mensajes = [{ id: 1, autorId: 2, texto: 'Ya voy para allá', creadoEn: '2026-11-01T10:00:00Z', mio: false }];
    act(() => canal.recibir({ tipo: 'chat', id: 9 }));
    expect(await screen.findByText('Ya voy para allá')).toBeInTheDocument();
    expect(consultas()).toBe(antes + 1);
    act(() => canal.recibir({ tipo: 'chat', id: 55 })); // chat de otra solicitud: no consulta
    await new Promise((r) => setTimeout(r, 100));
    expect(consultas()).toBe(antes + 1);
  });
});

describe('administración', () => {
  it('suspender una cuenta pide el motivo y usa PATCH /admin/users/{id}/status', async () => {
    sesionComo(ADMIN);
    const llamadas = simularApi({
      'GET /admin/users': { items: [{ id: 3, rol: 'HOGAR', nombre: 'Hogar Tres', correo: 'h3@correo.co', telefono: '3', suspendido: false }], pagina: 0, tamano: 10, total: 1 },
      'PATCH /admin/users/3/status': { id: 3, rol: 'HOGAR', nombre: 'Hogar Tres', correo: 'h3@correo.co', suspendido: true },
    });
    pintar(<Admin tab="usuarios" />);
    await userEvent.click(await screen.findByRole('button', { name: 'Suspender' }));
    const dlg = await screen.findByRole('dialog', { hidden: true });
    await userEvent.type(within(dlg).getByLabelText('Motivo'), 'Comportamiento abusivo');
    await userEvent.click(within(dlg).getByRole('button', { name: 'Suspender' }));
    await waitFor(() => expect(llamadas.find((l) => l.metodo === 'PATCH')?.cuerpo).toEqual({ status: 'SUSPENDED', reason: 'Comportamiento abusivo' }));
  });

  it('el resumen muestra los conteos por estado', async () => {
    sesionComo(ADMIN);
    simularApi({
      'GET /admin/summary': { solicitudesPorEstado: { E01: 2, E02: 0, E03: 1, E04: 0, E05: 5, E06: 0, E07: 1 }, usuariosPorRol: { HOGAR: 4, TECNICO: 3, ADMIN: 1 }, cuentasSuspendidas: 1, comisionesCobradasCop: 12000, comisionesRetenidasCop: 2000 },
    });
    pintar(<Admin tab="resumen" />);
    expect(await screen.findByText('Finalizado')).toBeInTheDocument();
    expect(screen.getByText('$ 12.000')).toBeInTheDocument();
    expect(screen.getByText('Cuentas suspendidas')).toBeInTheDocument();
  });
});
