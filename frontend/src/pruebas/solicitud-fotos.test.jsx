import { beforeEach, describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { pintar, sesionComo, simularApi } from './apoyo.jsx';
import Hogar from '../Hogar.jsx';

const HOGAR = { id: 1, rol: 'HOGAR', nombre: 'Ana' };
const SOL = { id: 11, estado: 'E01', oficioId: 2, oficio: 'Electricista', descripcion: 'x', expiraEn: '2099-01-01T00:00:00Z' };
const foto = (nombre, bytes = 100) => new File([new Uint8Array(bytes)], nombre, { type: 'image/jpeg' });
const REGISTRADA = { id: 9, tipoMime: 'image/jpeg', tamanoBytes: 100, creadaEn: '2026-11-13T10:00:00Z', url: null };

const base = () => ({
  'GET /service-requests/mine': [], 'GET /trades': [{ id: 2, nombre: 'Electricista' }],
  'POST /service-requests': [201, SOL],
  'POST /uploads/signature': [503, { code: 'UPLOAD_UNAVAILABLE', message: 'x', traceId: 't' }], // sin nube: se sube por el API
});

async function llenar() {
  await userEvent.type(await screen.findByLabelText('Cuéntanos el problema'), 'Cambiar un tomacorriente roto');
}

beforeEach(() => { sesionComo(HOGAR); });

describe('fotos al publicar la solicitud', () => {
  it('las fotos elegidas se suben a la solicitud recién creada, en orden, antes de abrir el detalle', async () => {
    const llamadas = simularApi({ ...base(), 'POST /service-requests/11/photos': [201, REGISTRADA] });
    pintar(<Hogar />);
    await llenar();
    await userEvent.upload(screen.getByLabelText(/Fotos del problema/), [foto('a.jpg'), foto('b.jpg')]);
    expect(await screen.findAllByRole('button', { name: /^Quitar / })).toHaveLength(2);
    await userEvent.click(screen.getByRole('button', { name: 'Publicar solicitud' }));
    await waitFor(() => expect(window.location.hash).toBe('#/s/11'));
    const secuencia = llamadas.filter((l) => l.metodo === 'POST' && !l.ruta.endsWith('/signature')).map((l) => l.ruta);
    expect(secuencia).toEqual(['/service-requests', '/service-requests/11/photos', '/service-requests/11/photos']);
    expect(await screen.findByText('Solicitud publicada. Los técnicos cercanos ya pueden verla.')).toBeInTheDocument();
  });

  it('sin fotos publica igual que antes', async () => {
    const llamadas = simularApi(base());
    pintar(<Hogar />);
    await llenar();
    await userEvent.click(screen.getByRole('button', { name: 'Publicar solicitud' }));
    await waitFor(() => expect(window.location.hash).toBe('#/s/11'));
    expect(llamadas.some((l) => l.ruta.includes('/photos'))).toBe(false);
  });

  it('permite quitar una foto antes de publicar', async () => {
    const llamadas = simularApi({ ...base(), 'POST /service-requests/11/photos': [201, REGISTRADA] });
    pintar(<Hogar />);
    await llenar();
    await userEvent.upload(screen.getByLabelText(/Fotos del problema/), [foto('a.jpg'), foto('b.jpg')]);
    await userEvent.click(await screen.findByRole('button', { name: 'Quitar a.jpg' }));
    await userEvent.click(screen.getByRole('button', { name: 'Publicar solicitud' }));
    await waitFor(() => expect(window.location.hash).toBe('#/s/11'));
    expect(llamadas.filter((l) => l.ruta === '/service-requests/11/photos')).toHaveLength(1);
  });

  it('solo admite 3 fotos', async () => {
    simularApi(base());
    pintar(<Hogar />);
    await llenar();
    await userEvent.upload(screen.getByLabelText(/Fotos del problema/), [foto('1.jpg'), foto('2.jpg'), foto('3.jpg'), foto('4.jpg')]);
    expect(await screen.findByText('Puedes agregar hasta 3 fotos')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: /^Quitar / })).toHaveLength(3);
    expect(screen.queryByLabelText(/Fotos del problema/)).not.toBeInTheDocument(); // con 3 ya no hay dónde agregar más
  });

  it('si una foto falla la solicitud queda publicada y se avisa que la agregue desde el detalle', async () => {
    simularApi({ ...base(), 'POST /service-requests/11/photos': [413, { code: 'PAYLOAD_TOO_LARGE', message: 'grande', traceId: 't' }] });
    pintar(<Hogar />);
    await llenar();
    await userEvent.upload(screen.getByLabelText(/Fotos del problema/), foto('a.jpg'));
    await userEvent.click(screen.getByRole('button', { name: 'Publicar solicitud' }));
    expect(await screen.findByText(/Solicitud publicada, pero una foto no se pudo subir/)).toBeInTheDocument();
    await waitFor(() => expect(window.location.hash).toBe('#/s/11'));
  });

  it('una foto de más de 2 MB que no se puede reducir se rechaza al elegirla', async () => {
    simularApi(base());
    pintar(<Hogar />);
    await llenar();
    await userEvent.upload(screen.getByLabelText(/Fotos del problema/), foto('enorme.jpg', 2 * 1024 * 1024 + 1));
    expect(await screen.findByText(/enorme\.jpg» pesa más de 2 MB/)).toBeInTheDocument();
    expect(screen.queryAllByRole('button', { name: /^Quitar / })).toHaveLength(0);
  });
});
