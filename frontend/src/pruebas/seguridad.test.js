import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';

function archivos(dir) {
  return readdirSync(dir).flatMap((n) => {
    const r = join(dir, n);
    return statSync(r).isDirectory() ? (n === 'pruebas' ? [] : archivos(r)) : [r];
  });
}

describe('la interfaz no abre puertas de inyección (CSP sin unsafe-inline ni unsafe-eval)', () => {
  const fuentes = archivos('src').filter((f) => /\.(jsx?|html)$/.test(f));
  it('hay fuentes que revisar', () => expect(fuentes.length).toBeGreaterThan(10));
  it.each(['dangerouslySetInnerHTML', 'innerHTML', 'outerHTML', 'eval(', 'new Function', 'document.write'])('no usa %s', (patron) => {
    const culpables = fuentes.filter((f) => readFileSync(f, 'utf8').includes(patron));
    expect(culpables).toEqual([]);
  });
  it('index.html no tiene scripts ni estilos en línea', () => {
    const html = readFileSync('index.html', 'utf8');
    expect(html).not.toMatch(/<script(?![^>]*\bsrc=)[^>]*>/);
    expect(html).not.toMatch(/\son[a-z]+=/);
    expect(html).not.toMatch(/<style|style=/);
  });
});
