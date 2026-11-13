import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { pintar, sesionComo, simularApi } from './apoyo.jsx';
import Fotos from '../Fotos.jsx';

const SOL = { id: 4, estado: 'E01' };
const SUBIDA = 'https://api.cloudinary.com/v1_1/nube/image/upload';
const FIRMA = { cloudName: 'nube', apiKey: '123', timestamp: 1700000000, publicId: 'servilink/solicitudes/4/' + 'a'.repeat(32),
  allowedFormats: 'jpg,png,webp', signature: 'firma-de-subida', uploadUrl: SUBIDA };
const RESPUESTA_NUBE = { public_id: FIRMA.publicId, version: 1700000123, signature: 'firma-respuesta', format: 'jpg' };
const REGISTRADA = { id: 9, tipoMime: 'image/jpeg', tamanoBytes: null, creadaEn: '2026-11-13T10:00:00Z', url: 'https://res.cloudinary.com/nube/image/upload/v1700000123/x' };

const foto = (bytes = 100) => new File([new Uint8Array(bytes)], 'casa.jpg', { type: 'image/jpeg' });
const subir = async (archivo) => userEvent.upload(await screen.findByLabelText(/Agregar foto/), archivo);

beforeEach(() => { sesionComo({ id: 1, rol: 'HOGAR', nombre: 'Ana' }); });

describe('fotos de la solicitud', () => {
  it('sube directo a Cloudinary con la firma, sin mandar el token, y registra la foto', async () => {
    const llamadas = simularApi({
      'GET /service-requests/4/photos': [],
      'POST /uploads/signature': FIRMA,
      [`POST ${SUBIDA}`]: RESPUESTA_NUBE,
      'POST /service-requests/4/photos/cloudinary': [201, REGISTRADA],
    });
    const recargar = vi.fn();
    pintar(<Fotos s={SOL} esHogar recargar={recargar} />);
    await subir(foto());
    expect(await screen.findByText('Foto agregada')).toBeInTheDocument();
    const nube = llamadas.find((l) => l.ruta === SUBIDA);
    expect(nube.cab.Authorization).toBeUndefined();
    expect(nube.cuerpo.get('signature')).toBe('firma-de-subida');
    expect(nube.cuerpo.get('public_id')).toBe(FIRMA.publicId);
    expect(nube.cuerpo.get('api_key')).toBe('123');
    expect(nube.cuerpo.get('timestamp')).toBe('1700000000');
    expect(nube.cuerpo.get('allowed_formats')).toBe('jpg,png,webp');
    expect(nube.cuerpo.get('file')).toBeInstanceOf(File);
    const registro = llamadas.find((l) => l.ruta === '/service-requests/4/photos/cloudinary');
    expect(registro.cuerpo).toEqual({ publicId: FIRMA.publicId, version: '1700000123', signature: 'firma-respuesta', format: 'jpg' });
    expect(llamadas.some((l) => l.metodo === 'POST' && l.ruta === '/service-requests/4/photos')).toBe(false);
    expect(recargar).toHaveBeenCalled();
  });

  it('si la nube no está disponible guarda la foto por el API como antes', async () => {
    const llamadas = simularApi({
      'GET /service-requests/4/photos': [],
      'POST /uploads/signature': [503, { code: 'UPLOAD_UNAVAILABLE', message: 'x', traceId: 't' }],
      'POST /service-requests/4/photos': [201, { ...REGISTRADA, url: null }],
    });
    pintar(<Fotos s={SOL} esHogar recargar={() => {}} />);
    await subir(foto());
    expect(await screen.findByText('Foto agregada')).toBeInTheDocument();
    const respaldo = llamadas.find((l) => l.metodo === 'POST' && l.ruta === '/service-requests/4/photos');
    expect(respaldo.cuerpo.get('foto')).toBeInstanceOf(File);
  });

  it.each([
    ['Cloudinary responde con error', () => [400, { error: { message: 'x' } }]],
    ['Cloudinary no se puede alcanzar', () => { throw new TypeError('red'); }],
  ])('si %s usa el respaldo del API', async (_nombre, respuesta) => {
    const llamadas = simularApi({
      'GET /service-requests/4/photos': [],
      'POST /uploads/signature': FIRMA,
      [`POST ${SUBIDA}`]: respuesta,
      'POST /service-requests/4/photos': [201, { ...REGISTRADA, url: null }],
    });
    pintar(<Fotos s={SOL} esHogar recargar={() => {}} />);
    await subir(foto());
    expect(await screen.findByText('Foto agregada')).toBeInTheDocument();
    expect(llamadas.some((l) => l.ruta === '/service-requests/4/photos/cloudinary')).toBe(false);
  });

  it('un rechazo del servidor (límite alcanzado) se muestra y no se reintenta por otra vía', async () => {
    const llamadas = simularApi({
      'GET /service-requests/4/photos': [],
      'POST /uploads/signature': [409, { code: 'PHOTO_LIMIT_REACHED', message: 'Cada solicitud admite hasta 3 fotos', traceId: 't' }],
    });
    pintar(<Fotos s={SOL} esHogar recargar={() => {}} />);
    await subir(foto());
    expect(await screen.findByText('Cada solicitud admite hasta 3 fotos')).toBeInTheDocument();
    expect(llamadas.filter((l) => l.metodo === 'POST')).toHaveLength(1);
  });

  it('rechaza en el navegador las fotos de más de 2 MB sin llamar a nadie', async () => {
    const llamadas = simularApi({ 'GET /service-requests/4/photos': [] });
    pintar(<Fotos s={SOL} esHogar recargar={() => {}} />);
    await subir(foto(2 * 1024 * 1024 + 1));
    expect(await screen.findByText('Cada foto puede pesar hasta 2 MB')).toBeInTheDocument();
    expect(llamadas.filter((l) => l.metodo === 'POST')).toHaveLength(0);
  });

  it('muestra las fotos de la nube con su dirección y baja las demás por el API', async () => {
    const llamadas = simularApi({
      'GET /service-requests/4/photos': [REGISTRADA, { id: 10, tipoMime: 'image/png', tamanoBytes: 5, creadaEn: '2026-11-13T10:00:00Z', url: null }],
      'GET /service-requests/4/photos/10': () => [200, 'x'],
    });
    URL.createObjectURL = vi.fn(() => 'blob:local'); URL.revokeObjectURL = vi.fn();
    pintar(<Fotos s={SOL} esHogar recargar={() => {}} />);
    const imgs = await screen.findAllByAltText('Foto de la solicitud');
    expect(imgs[0]).toHaveAttribute('src', REGISTRADA.url);
    await waitFor(() => expect(llamadas.some((l) => l.ruta === '/service-requests/4/photos/10')).toBe(true));
    expect(llamadas.some((l) => l.ruta === '/service-requests/4/photos/9')).toBe(false);
  });
});
