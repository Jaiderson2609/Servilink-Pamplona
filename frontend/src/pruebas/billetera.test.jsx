import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { pintar, sesionComo, simularApi } from './apoyo.jsx';
import Billetera, { referenciaDeRetorno, referenciaPendiente } from '../Billetera.jsx';
import Tecnico from '../Tecnico.jsx';

vi.mock('../Mapa.jsx', () => ({ default: () => <div data-testid="mapa" /> })); // Leaflet necesita un navegador real
const irA = vi.hoisted(() => vi.fn());
vi.mock('../util.js', async (original) => ({ ...(await original()), irA }));

const BASE = { saldoDisponibleCop: 50000, saldoRetenidoCop: 0, movimientos: [], pagoDisponible: true, recargaDemoDisponible: false };
const URL_PAGO = 'https://checkout.wompi.co/p/?public-key=pub_test_x&reference=SL-abc';
const INICIADA = { referencia: 'SL-abc', valorCop: 50000, estado: 'PENDING', urlPago: URL_PAGO };

const conRetorno = (referencia) => window.history.replaceState(null, '', referencia ? `/?recarga=${referencia}#/t/billetera` : '/#/t/billetera');

const CLAVE = 'servilink-recarga-pendiente';
const guardado = (referencia, hace = 0) => window.localStorage.setItem(CLAVE, JSON.stringify({ referencia, en: Date.now() - hace }));

beforeEach(() => { window.localStorage.clear(); sesionComo({ id: 7, rol: 'TECNICO', nombre: 'Luis Gómez' }); irA.mockClear(); conRetorno(null); });
afterEach(() => { vi.useRealTimers(); conRetorno(null); window.localStorage.clear(); });

describe('recarga con Wompi', () => {
  it('"Pagar con Wompi" crea la recarga y lleva al técnico al pago, con Idempotency-Key', async () => {
    const llamadas = simularApi({ 'GET /wallet': BASE, 'POST /wallet/topups': [201, INICIADA] });
    pintar(<Billetera />);
    await userEvent.click(await screen.findByRole('button', { name: 'Pagar con Wompi' }));
    await waitFor(() => expect(irA).toHaveBeenCalledWith(URL_PAGO));
    const crear = llamadas.find((l) => l.metodo === 'POST' && l.ruta === '/wallet/topups');
    expect(crear.cuerpo).toEqual({ valorCop: 50000 });
    expect(crear.cab['Idempotency-Key']).toBeTruthy();
    expect(llamadas.some((l) => l.ruta === '/wallet/topups/demo')).toBe(false);
    expect(referenciaPendiente()).toBe('SL-abc'); // por si Wompi no devuelve ?recarga=
  });

  it('si Wompi no está disponible muestra el error y no lleva a ninguna parte', async () => {
    simularApi({ 'GET /wallet': BASE, 'POST /wallet/topups': [503, { code: 'PAYMENT_UNAVAILABLE', message: 'x', traceId: 't' }] });
    pintar(<Billetera />);
    await userEvent.click(await screen.findByRole('button', { name: 'Pagar con Wompi' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Los pagos con Wompi no están disponibles');
    expect(irA).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: 'Pagar con Wompi' })).toBeEnabled();
  });

  it('sin pago ni demostración habilitados lo dice y no ofrece botones de recarga', async () => {
    simularApi({ 'GET /wallet': { ...BASE, pagoDisponible: false } });
    pintar(<Billetera />);
    expect(await screen.findByText(/recargas no están disponibles/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Pagar con Wompi|demostración/ })).not.toBeInTheDocument();
  });

  it('la recarga de demostración solo aparece si el servidor la habilita y usa su propia ruta', async () => {
    const llamadas = simularApi({ 'GET /wallet': { ...BASE, pagoDisponible: false, recargaDemoDisponible: true },
      'POST /wallet/topups/demo': { ...BASE } });
    pintar(<Billetera />);
    await userEvent.click(await screen.findByRole('button', { name: 'Recarga de demostración' }));
    await waitFor(() => expect(llamadas.some((l) => l.metodo === 'POST' && l.ruta === '/wallet/topups/demo')).toBe(true));
    expect(screen.queryByRole('button', { name: 'Pagar con Wompi' })).not.toBeInTheDocument();
    expect(irA).not.toHaveBeenCalled();
  });
});

describe('regreso desde Wompi', () => {
  it('con ?recarga= consulta la recarga y, aprobada, lo informa y limpia la dirección', async () => {
    conRetorno('SL-abc');
    const llamadas = simularApi({ 'GET /wallet': BASE, 'GET /wallet/topups/SL-abc': { referencia: 'SL-abc', valorCop: 50000, estado: 'APPROVED' } });
    pintar(<Billetera />);
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Recarga acreditada'));
    expect(llamadas.some((l) => l.ruta === '/wallet/topups/SL-abc')).toBe(true);
    expect(window.location.search).toBe('');
    expect(window.location.hash).toBe('#/t/billetera');
  });

  it('mientras está pendiente sigue consultando hasta que el estado cambia', async () => {
    conRetorno('SL-abc');
    let n = 0;
    simularApi({ 'GET /wallet': BASE, 'GET /wallet/topups/SL-abc': () => ({ referencia: 'SL-abc', valorCop: 50000, estado: ++n < 3 ? 'PENDING' : 'APPROVED' }) });
    vi.useFakeTimers({ shouldAdvanceTime: true });
    pintar(<Billetera />);
    expect(await screen.findByText(/confirmando tu pago/)).toBeInTheDocument();
    await act(async () => { await vi.advanceTimersByTimeAsync(3000); });
    await act(async () => { await vi.advanceTimersByTimeAsync(3000); });
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Recarga acreditada'));
    expect(n).toBe(3);
  });

  it('un pago rechazado lo dice y recuerda que el saldo no cambió', async () => {
    conRetorno('SL-abc');
    simularApi({ 'GET /wallet': BASE, 'GET /wallet/topups/SL-abc': { referencia: 'SL-abc', valorCop: 50000, estado: 'FAILED' } });
    pintar(<Billetera />);
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('no fue aprobado'));
  });

  it('un pago en revisión no se presenta como acreditado', async () => {
    conRetorno('SL-abc');
    simularApi({ 'GET /wallet': BASE, 'GET /wallet/topups/SL-abc': { referencia: 'SL-abc', valorCop: 50000, estado: 'REVIEW' } });
    pintar(<Billetera />);
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('hay que revisarlo'));
    expect(screen.getByRole('status')).not.toHaveTextContent('Recarga acreditada');
  });

  it('si tras un minuto sigue pendiente deja de insistir y explica qué pasará', async () => {
    conRetorno('SL-abc');
    const llamadas = simularApi({ 'GET /wallet': BASE, 'GET /wallet/topups/SL-abc': { referencia: 'SL-abc', valorCop: 50000, estado: 'PENDING' } });
    vi.useFakeTimers({ shouldAdvanceTime: true });
    pintar(<Billetera />);
    await screen.findByText(/confirmando tu pago/);
    await act(async () => { await vi.advanceTimersByTimeAsync(3000 * 25); });
    expect(screen.getByRole('status')).toHaveTextContent('todavía no confirma tu pago');
    const consultas = llamadas.filter((l) => l.ruta === '/wallet/topups/SL-abc').length;
    await act(async () => { await vi.advanceTimersByTimeAsync(30000); });
    expect(llamadas.filter((l) => l.ruta === '/wallet/topups/SL-abc').length).toBe(consultas);
    expect(consultas).toBe(20);
  });

  it('ignora una referencia con formato raro y no consulta nada', async () => {
    conRetorno('../admin');
    const llamadas = simularApi({ 'GET /wallet': BASE });
    pintar(<Billetera />);
    await screen.findByText('Recargar saldo');
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
    expect(llamadas.some((l) => l.ruta.startsWith('/wallet/topups/'))).toBe(false);
  });
});

describe('regreso sin ?recarga= (referencia guardada antes de salir)', () => {
  it('muestra cómo terminó el pago y olvida la referencia', async () => {
    guardado('SL-abc');
    simularApi({ 'GET /wallet': BASE, 'GET /wallet/topups/SL-abc': { referencia: 'SL-abc', valorCop: 50000, estado: 'FAILED' } });
    pintar(<Billetera />);
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('no fue aprobado'));
    expect(referenciaPendiente()).toBeNull();
  });

  it('si el pago sigue pendiente no muestra "confirmando": quizá nunca pagó', async () => {
    guardado('SL-abc');
    const llamadas = simularApi({ 'GET /wallet': BASE, 'GET /wallet/topups/SL-abc': { referencia: 'SL-abc', valorCop: 50000, estado: 'PENDING' } });
    pintar(<Billetera />);
    await screen.findByText('Recargar saldo');
    await waitFor(() => expect(llamadas.some((l) => l.ruta === '/wallet/topups/SL-abc')).toBe(true));
    expect(screen.queryByText(/confirmando tu pago/)).not.toBeInTheDocument();
    expect(referenciaPendiente()).toBe('SL-abc');
  });

  it('una referencia vieja (más de 24 h) o con formato raro se ignora', async () => {
    guardado('SL-abc', 25 * 60 * 60 * 1000);
    expect(referenciaPendiente()).toBeNull();
    guardado('../admin');
    expect(referenciaPendiente()).toBeNull();
    window.localStorage.setItem(CLAVE, 'no es json');
    expect(referenciaPendiente()).toBeNull();
    const llamadas = simularApi({ 'GET /wallet': BASE });
    pintar(<Billetera />);
    await screen.findByText('Recargar saldo');
    expect(llamadas.some((l) => l.ruta.startsWith('/wallet/topups/'))).toBe(false);
  });

  it('el parámetro de la dirección manda sobre la referencia guardada', async () => {
    guardado('SL-vieja');
    conRetorno('SL-nueva');
    const llamadas = simularApi({ 'GET /wallet': BASE, 'GET /wallet/topups/SL-nueva': { referencia: 'SL-nueva', valorCop: 50000, estado: 'APPROVED' } });
    pintar(<Billetera />);
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Recarga acreditada'));
    expect(llamadas.some((l) => l.ruta === '/wallet/topups/SL-vieja')).toBe(false);
  });
});

describe('pantalla del técnico', () => {
  it('al volver de Wompi (?recarga=) abre la billetera aunque la pestaña pedida sea otra', async () => {
    conRetorno('SL-abc');
    window.history.replaceState(null, '', '/?recarga=SL-abc#/t/oportunidades');
    simularApi({ 'GET /wallet': BASE, 'GET /wallet/topups/SL-abc': { referencia: 'SL-abc', valorCop: 50000, estado: 'APPROVED' },
      'GET /service-requests/open': [], 'GET /technicians/me/location': [404, { code: 'NOT_FOUND', message: 'x', traceId: 't' }] });
    pintar(<Tecnico tab="oportunidades" />);
    expect(await screen.findByText('Recargar saldo')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Recarga acreditada'));
    expect(window.location.hash).toBe('#/t/billetera');
  });

  it('sin ?recarga= respeta la pestaña pedida', async () => {
    simularApi({ 'GET /service-requests/open': [], 'GET /technicians/me/location': [404, { code: 'NOT_FOUND', message: 'x', traceId: 't' }] });
    pintar(<Tecnico tab="oportunidades" />);
    expect(await screen.findByText(/No hay solicitudes abiertas/)).toBeInTheDocument();
    expect(screen.queryByText('Recargar saldo')).not.toBeInTheDocument();
  });
});

describe('referenciaDeRetorno', () => {
  it.each([[null, null], ['SL-abc_1', 'SL-abc_1'], ['a b', null], ['x'.repeat(41), null], ['<script>', null]])('%s → %s', (entrada, esperado) => {
    conRetorno(entrada === null ? null : encodeURIComponent(entrada));
    expect(referenciaDeRetorno()).toBe(esperado);
  });
});
