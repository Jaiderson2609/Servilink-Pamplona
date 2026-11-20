import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { pintar, sesionComo, simularApi } from './apoyo.jsx';
import App from '../App.jsx';
import { menuDe } from '../menus.js';
import { setToken } from '../api.js';

vi.mock('../Mapa.jsx', () => ({ default: () => <div data-testid="mapa" /> })); // Leaflet necesita un navegador real

const HOGAR = { id: 1, rol: 'HOGAR', nombre: 'Ana Pérez' };
const TECNICO = { id: 2, rol: 'TECNICO', nombre: 'Luis Mora' };
const ADMIN = { id: 9, rol: 'ADMIN', nombre: 'Jaider' };
const vacio = { code: 'NOT_FOUND', message: 'x', traceId: 't' };

beforeEach(() => { setToken(null); window.location.hash = ''; delete window.matchMedia; });

/** Simula una pantalla de celular: ahí la navegación es la barra inferior y no el panel lateral. */
const celular = () => { window.matchMedia = (q) => ({ matches: true, media: q, addEventListener() {}, removeEventListener() {} }); };
const botones = () => within(screen.getByRole('navigation', { name: 'Principal' })).getAllByRole('link').concat(within(screen.getByRole('navigation', { name: 'Principal' })).getAllByRole('button')).map((e) => e.textContent);

const enlaces = () => within(screen.getByRole('navigation', { name: 'Principal' })).getAllByRole('link').map((a) => a.textContent);

describe('panel lateral', () => {
  it('el hogar ve sus dos opciones y la activa es Nueva solicitud', async () => {
    sesionComo(HOGAR);
    simularApi({ 'GET /service-requests/mine': [], 'GET /trades': [{ id: 2, nombre: 'Electricista' }] });
    pintar(<App />);
    await screen.findByText('Hola, Ana');
    expect(enlaces()).toEqual(['Nueva solicitud', 'Mis solicitudes']);
    expect(screen.getByRole('link', { name: 'Nueva solicitud' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByText('Ana Pérez')).toBeInTheDocument();
  });

  it('Mis solicitudes lista todas las solicitudes del hogar', async () => {
    sesionComo(HOGAR);
    window.location.hash = '#/h/solicitudes';
    simularApi({
      'GET /service-requests/mine': [{ id: 3, estado: 'CERRADA', descripcion: 'Cambiar guardas de la puerta', oficio: 'Cerrajería', hogarId: 1 }],
      'GET /trades': [],
    });
    pintar(<App />);
    expect(await screen.findByRole('heading', { name: 'Mis solicitudes' })).toBeInTheDocument();
    expect(screen.getByText('Cambiar guardas de la puerta')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Mis solicitudes' })).toHaveAttribute('aria-current', 'page');
  });

  it('el técnico ve sus cinco opciones y puede cambiar de una a otra', async () => {
    sesionComo(TECNICO);
    simularApi({
      'GET /service-requests/open': [], 'GET /technicians/me/location': [404, vacio], 'GET /technicians/me/verification': [404, vacio],
      'GET /technicians/me/availability': { estado: 'AVAILABLE' }, 'GET /wallet': [404, vacio],
    });
    pintar(<App />);
    await screen.findByText('Hola, Luis');
    expect(enlaces()).toEqual(['Solicitudes cercanas', 'Mis servicios', 'Billetera', 'Zona de trabajo', 'Verificación']);
    expect(screen.getByRole('link', { name: 'Solicitudes cercanas' })).toHaveAttribute('aria-current', 'page');
    await userEvent.click(screen.getByRole('link', { name: 'Zona de trabajo' }));
    await waitFor(() => expect(window.location.hash).toBe('#/t/zona'));
    await waitFor(() => expect(screen.getByRole('link', { name: 'Zona de trabajo' })).toHaveAttribute('aria-current', 'page'));
  });

  it('el administrador ve sus seis opciones', async () => {
    sesionComo(ADMIN);
    window.location.hash = '#/a/parametros';
    simularApi({ 'GET /admin/parameters': [] });
    pintar(<App />);
    await waitFor(() => expect(enlaces()).toEqual(['Resumen', 'Usuarios', 'Solicitudes', 'Verificación', 'Parámetros', 'Auditoría']));
    expect(screen.getByRole('link', { name: 'Parámetros' })).toHaveAttribute('aria-current', 'page');
  });

  it('Salir cierra la sesión desde el panel', async () => {
    sesionComo(ADMIN);
    simularApi({ 'GET /admin/summary': [404, vacio], 'POST /auth/logout': [204, undefined] });
    pintar(<App />);
    await userEvent.click(await screen.findByRole('button', { name: 'Salir' }));
    expect(await screen.findByLabelText('Correo')).toBeInTheDocument();
  });
});

describe('menuDe', () => {
  it('lo que necesita la barra del celular: título, botones fijos y lo que va en Más', () => {
    const t = menuDe('TECNICO', { vista: 't', tab: 'zona' });
    expect(t.titulo).toBe('Zona de trabajo');
    expect(t.barra.fijas.map((b) => b.texto)).toEqual(['Cercanas', 'Servicios', 'Billetera']);
    expect(t.barra.mas.map((b) => b.k)).toEqual(['zona', 'verificacion']);
    expect(t.barra.masActiva).toBe(true);
    expect(menuDe('TECNICO', { vista: 't', tab: 'oportunidades' }).titulo).toBe('Cercanas');
    expect(menuDe('HOGAR', { vista: 'inicio' }).barra.mas).toEqual([]);
    expect(menuDe('HOGAR', { vista: 's', id: 4 })).toMatchObject({ detalle: true, titulo: 'Solicitud' });
  });

  it('en el detalle de una solicitud deja marcada la opción que la contiene', () => {
    const detalle = { vista: 's', id: 4 };
    expect(menuDe('HOGAR', detalle)).toMatchObject({ base: 'h', activa: 'solicitudes' });
    expect(menuDe('TECNICO', detalle)).toMatchObject({ base: 't', activa: 'servicios' });
    expect(menuDe('ADMIN', detalle)).toMatchObject({ base: 'a', activa: 'solicitudes' });
  });

  it('una opción desconocida vuelve a la primera', () => {
    expect(menuDe('HOGAR', { vista: 'h', tab: 'nada' }).activa).toBe('nueva');
    expect(menuDe('ADMIN', { vista: 'a', tab: 'nada' }).activa).toBe('resumen');
  });
});

describe('barra inferior en el celular', () => {
  it('el hogar tiene Solicitar, Solicitudes y Cuenta, y no el panel lateral', async () => {
    celular();
    sesionComo(HOGAR);
    simularApi({ 'GET /service-requests/mine': [], 'GET /trades': [{ id: 2, nombre: 'Electricista' }] });
    const { container } = pintar(<App />);
    await screen.findByRole('heading', { name: 'Nueva solicitud', level: 1 });
    expect(botones()).toEqual(['Solicitar', 'Solicitudes', 'Cuenta']);
    expect(container.querySelector('.lateral')).toBeNull();
    expect(screen.getByRole('link', { name: 'Solicitar' })).toHaveAttribute('aria-current', 'page');
  });

  it('Cuenta abre la hoja con el nombre y Salir; Escape y navegar la cierran', async () => {
    celular();
    sesionComo(HOGAR);
    simularApi({ 'GET /service-requests/mine': [], 'GET /trades': [], 'POST /auth/logout': [204, undefined] });
    pintar(<App />);
    const cuenta = await screen.findByRole('button', { name: 'Cuenta' });
    expect(cuenta).toHaveAttribute('aria-expanded', 'false');
    await userEvent.click(cuenta);
    const hoja = screen.getByRole('dialog', { name: 'Cuenta' });
    expect(within(hoja).getByText('Ana Pérez')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Cuenta' })).toHaveAttribute('aria-expanded', 'true');
    await userEvent.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Cuenta' }));
    window.location.hash = '#/h/solicitudes';
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('Salir desde la hoja cierra la sesión', async () => {
    celular();
    sesionComo(HOGAR);
    simularApi({ 'GET /service-requests/mine': [], 'GET /trades': [], 'POST /auth/logout': [204, undefined] });
    pintar(<App />);
    await userEvent.click(await screen.findByRole('button', { name: 'Cuenta' }));
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Salir' }));
    expect(await screen.findByLabelText('Correo')).toBeInTheDocument();
  });

  it('en Solicitudes aparece el número de solicitudes que ya tienen ofertas', async () => {
    celular();
    sesionComo(HOGAR);
    window.location.hash = '#/h/solicitudes';
    simularApi({
      'GET /service-requests/mine': [
        { id: 3, estado: 'E02', descripcion: 'Fuga bajo el lavamanos', oficio: 'Plomería', hogarId: 1 },
        { id: 4, estado: 'E05', descripcion: 'Cambio de guardas', oficio: 'Cerrajería', hogarId: 1 },
      ],
      'GET /trades': [],
    });
    pintar(<App />);
    expect(await screen.findByLabelText('1 con ofertas')).toBeInTheDocument();
  });

  it('el técnico tiene cuatro botones, la disponibilidad arriba y Zona y Verificación dentro de Más', async () => {
    celular();
    sesionComo(TECNICO);
    simularApi({
      'GET /service-requests/open': [], 'GET /technicians/me/location': [404, vacio], 'GET /technicians/me/verification': [404, vacio],
      'GET /technicians/me/availability': { estado: 'AVAILABLE' }, 'GET /wallet': [404, vacio],
    });
    pintar(<App />);
    const interruptor = await screen.findByRole('switch', { name: 'Mi disponibilidad' });
    expect(interruptor).toHaveTextContent('Disponible');
    expect(screen.getAllByRole('switch')).toHaveLength(1); // solo el de la barra: la tarjeta de pantallas anchas no se monta
    expect(botones()).toEqual(['Cercanas', 'Servicios', 'Billetera', 'Más']);
    await userEvent.click(screen.getByRole('button', { name: 'Más' }));
    const hoja = screen.getByRole('dialog', { name: 'Más' });
    expect(within(hoja).getByRole('link', { name: /Zona de trabajo/ })).toHaveAttribute('href', '#/t/zona');
    expect(within(hoja).getByRole('link', { name: /Verificación/ })).toHaveAttribute('href', '#/t/verificacion');
  });

  it('el interruptor de la barra alterna Disponible y No disponible', async () => {
    celular();
    sesionComo(TECNICO);
    let estado = 'AVAILABLE';
    const llamadas = simularApi({
      'GET /service-requests/open': [], 'GET /technicians/me/location': [404, vacio], 'GET /technicians/me/verification': [404, vacio],
      'GET /technicians/me/availability': () => ({ estado }), 'PATCH /technicians/me/availability': (init) => { estado = JSON.parse(init.body).estado; return { estado }; },
    });
    pintar(<App />);
    await userEvent.click(await screen.findByRole('switch', { name: 'Mi disponibilidad' }));
    await waitFor(() => expect(llamadas.find((l) => l.metodo === 'PATCH')?.cuerpo).toEqual({ estado: 'OFFLINE' }));
    expect(await screen.findByRole('switch', { name: 'Mi disponibilidad' })).toHaveTextContent('No disponible');
  });

  it('el administrador tiene cuatro botones fijos y Más', async () => {
    celular();
    sesionComo(ADMIN);
    window.location.hash = '#/a/parametros';
    simularApi({ 'GET /admin/parameters': [] });
    pintar(<App />);
    await waitFor(() => expect(botones()).toEqual(['Resumen', 'Usuarios', 'Solicitudes', 'Verificar', 'Más']));
    expect(screen.getByRole('button', { name: 'Más' })).toHaveAttribute('aria-current', 'page'); // Parámetros vive dentro de Más
  });
});

describe('Mis solicitudes: filtros', () => {
  it('Con ofertas deja solo las solicitudes con ofertas', async () => {
    sesionComo(HOGAR);
    window.location.hash = '#/h/solicitudes';
    simularApi({
      'GET /service-requests/mine': [
        { id: 3, estado: 'E02', descripcion: 'Fuga bajo el lavamanos', oficio: 'Plomería', hogarId: 1 },
        { id: 4, estado: 'E05', descripcion: 'Cambio de guardas', oficio: 'Cerrajería', hogarId: 1 },
      ],
      'GET /trades': [],
    });
    pintar(<App />);
    expect(await screen.findByText('Cambio de guardas')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Con ofertas' }));
    expect(screen.getByRole('button', { name: 'Con ofertas' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByText('Fuga bajo el lavamanos')).toBeInTheDocument();
    expect(screen.queryByText('Cambio de guardas')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'En curso' }));
    expect(screen.getByText('No tienes solicitudes en esta categoría.')).toBeInTheDocument();
  });
});

describe('Nueva solicitud: oficio', () => {
  it('el oficio se elige con botones y el primero viene marcado', async () => {
    sesionComo(HOGAR);
    simularApi({ 'GET /service-requests/mine': [], 'GET /trades': [{ id: 2, nombre: 'Electricista' }, { id: 3, nombre: 'Cerrajería' }] });
    pintar(<App />);
    const grupo = await screen.findByRole('radiogroup', { name: '¿Qué oficio necesitas?' });
    expect(within(grupo).getByRole('radio', { name: 'Electricista' })).toHaveAttribute('aria-checked', 'true');
    await userEvent.click(within(grupo).getByRole('radio', { name: 'Cerrajería' }));
    expect(within(grupo).getByRole('radio', { name: 'Cerrajería' })).toHaveAttribute('aria-checked', 'true');
    expect(within(grupo).getByRole('radio', { name: 'Electricista' })).toHaveAttribute('aria-checked', 'false');
  });
});
