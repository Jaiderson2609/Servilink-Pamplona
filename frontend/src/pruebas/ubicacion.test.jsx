import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { pintar, sesionComo, simularApi } from './apoyo.jsx';
import Ubicacion from '../Ubicacion.jsx';

vi.mock('../Mapa.jsx', () => ({ default: () => <div data-testid="mapa" /> })); // Leaflet necesita un navegador real

const LUGARES = [
  { nombre: 'Carrera 5, Centro, Pamplona, Norte de Santander, Colombia', lat: 7.377234, lng: -72.647312 },
  { nombre: 'Carrera 5, Cúcuta, Norte de Santander, Colombia', lat: 7.8939, lng: -72.5078 },
];
const llamadasBusqueda = (llamadas) => llamadas.filter((l) => l.ruta.startsWith('/geocoding/search'));

/** Dentro de un formulario, como en "Nueva solicitud": Enter en el buscador no debe enviarlo. */
function Contenedor({ alEnviar = () => {} }) {
  const [punto, setPunto] = useState({ lat: 7.3772, lng: -72.6473 });
  return (
    <form onSubmit={(e) => { e.preventDefault(); alEnviar(); }}>
      <Ubicacion valor={punto} alCambiar={setPunto} />
      <output data-testid="punto">{punto.lat},{punto.lng}</output>
      <button type="submit">Enviar solicitud</button>
    </form>
  );
}

const preparar = (tabla = { 'GET /geocoding/search': LUGARES }) => {
  sesionComo({ id: 1, rol: 'HOGAR', nombre: 'Ana Pérez' });
  return simularApi(tabla);
};

describe('búsqueda de direcciones', () => {
  it('no busca mientras se escribe: solo al tocar "Buscar"', async () => {
    const llamadas = preparar();
    pintar(<Contenedor />);
    await userEvent.type(screen.getByLabelText('Buscar dirección'), 'Carrera 5 Pamplona');
    expect(llamadasBusqueda(llamadas)).toHaveLength(0);
    await userEvent.click(screen.getByRole('button', { name: 'Buscar' }));
    await screen.findByText(LUGARES[0].nombre);
    expect(llamadasBusqueda(llamadas)).toHaveLength(1);
    expect(llamadasBusqueda(llamadas)[0].ruta).toBe('/geocoding/search?q=Carrera%205%20Pamplona');
  });

  it('muestra los resultados, y al elegir uno fija el punto y recuerda el nombre', async () => {
    preparar();
    pintar(<Contenedor />);
    await userEvent.type(screen.getByLabelText('Buscar dirección'), 'Carrera 5');
    await userEvent.click(screen.getByRole('button', { name: 'Buscar' }));
    expect(await screen.findByText(/2 resultados/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: LUGARES[1].nombre }));
    expect(screen.getByTestId('punto')).toHaveTextContent('7.8939,-72.5078');
    expect(screen.getByText('Punto fijado en: ' + LUGARES[1].nombre)).toBeInTheDocument();
    expect(screen.queryByRole('list', { name: 'Resultados de la búsqueda' })).not.toBeInTheDocument();
  });

  it('Enter en el campo busca y no envía el formulario', async () => {
    const llamadas = preparar();
    const alEnviar = vi.fn();
    pintar(<Contenedor alEnviar={alEnviar} />);
    await userEvent.type(screen.getByLabelText('Buscar dirección'), 'Carrera 5 Pamplona{Enter}');
    await screen.findByText(LUGARES[0].nombre);
    expect(llamadasBusqueda(llamadas)).toHaveLength(1);
    expect(alEnviar).not.toHaveBeenCalled();
  });

  it('con menos de 3 caracteres no consulta y lo explica', async () => {
    const llamadas = preparar();
    pintar(<Contenedor />);
    await userEvent.type(screen.getByLabelText('Buscar dirección'), 'ab');
    await userEvent.click(screen.getByRole('button', { name: 'Buscar' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('al menos 3 caracteres');
    expect(llamadasBusqueda(llamadas)).toHaveLength(0);
  });

  it('sin coincidencias sugiere otra forma de ubicarse', async () => {
    preparar({ 'GET /geocoding/search': [] });
    pintar(<Contenedor />);
    await userEvent.type(screen.getByLabelText('Buscar dirección'), 'zzzz inexistente');
    await userEvent.click(screen.getByRole('button', { name: 'Buscar' }));
    expect(await screen.findByText(/No encontramos esa dirección/)).toBeInTheDocument();
  });

  it('si el buscador no está disponible avisa en español y el mapa sigue sirviendo', async () => {
    preparar({ 'GET /geocoding/search': [503, { code: 'GEOCODING_UNAVAILABLE', message: 'x', traceId: 't' }] });
    pintar(<Contenedor />);
    await userEvent.type(screen.getByLabelText('Buscar dirección'), 'Pamplona');
    await userEvent.click(screen.getByRole('button', { name: 'Buscar' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('El buscador de direcciones no está disponible');
    expect(screen.getByTestId('mapa')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Buscar' })).toBeEnabled(); // se puede volver a intentar
  });

  it('con demasiadas búsquedas en espera lo dice con claridad', async () => {
    preparar({ 'GET /geocoding/search': [429, { code: 'RATE_LIMITED', message: 'x', traceId: 't' }] });
    pintar(<Contenedor />);
    await userEvent.type(screen.getByLabelText('Buscar dirección'), 'Pamplona');
    await userEvent.click(screen.getByRole('button', { name: 'Buscar' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Intenta de nuevo en unos segundos');
  });

  it('mientras busca el botón queda deshabilitado, para no repetir la consulta', async () => {
    let liberar;
    const espera = new Promise((r) => { liberar = r; });
    preparar();
    const fetchSimulado = globalThis.fetch;
    globalThis.fetch = (...args) => espera.then(() => fetchSimulado(...args)); // la respuesta tarda hasta que la prueba la libere
    pintar(<Contenedor />);
    await userEvent.type(screen.getByLabelText('Buscar dirección'), 'Pamplona');
    await userEvent.click(screen.getByRole('button', { name: 'Buscar' }));
    expect(await screen.findByRole('button', { name: 'Buscando…' })).toBeDisabled();
    liberar();
    expect(await screen.findByRole('button', { name: 'Buscar' })).toBeEnabled();
  });

  it('muestra la atribución de OpenStreetMap', () => {
    preparar();
    pintar(<Contenedor />);
    expect(screen.getByText('Búsqueda de direcciones © colaboradores de OpenStreetMap')).toBeInTheDocument();
  });

  it('si luego se mueve el punto, el nombre fijado deja de mostrarse', async () => {
    preparar();
    pintar(<Contenedor />);
    await userEvent.type(screen.getByLabelText('Buscar dirección'), 'Carrera 5');
    await userEvent.click(screen.getByRole('button', { name: 'Buscar' }));
    await userEvent.click(await screen.findByRole('button', { name: LUGARES[0].nombre }));
    expect(screen.getByText(/Punto fijado en:/)).toBeInTheDocument();
    const lat = screen.getByLabelText('Latitud');
    await userEvent.clear(lat);
    await userEvent.type(lat, '7.5');
    await waitFor(() => expect(screen.queryByText(/Punto fijado en:/)).not.toBeInTheDocument());
  });
});
