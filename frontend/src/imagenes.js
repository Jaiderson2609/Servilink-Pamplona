/** Las fotos de un celular suelen pesar más de 2 MB: se reducen en el navegador antes de subirlas. */
export const MAX_BYTES = 2 * 1024 * 1024;
const LADO_MAX = 1600; // píxeles del lado más largo: sobra para que un técnico vea el problema
const CALIDADES = [0.85, 0.75, 0.65, 0.5];

/**
 * Devuelve la misma foto si ya cabe en 2 MB; si no, una copia JPEG más pequeña. Si el navegador no puede
 * reducirla (formato raro, sin soporte), devuelve la original y quien llama muestra el límite de 2 MB.
 */
export async function prepararFoto(archivo) {
  if (archivo.size <= MAX_BYTES) return archivo;
  if (typeof createImageBitmap !== 'function' || typeof document === 'undefined') return archivo;
  try {
    const img = await createImageBitmap(archivo);
    const escala = Math.min(1, LADO_MAX / Math.max(img.width, img.height));
    const canvas = document.createElement('canvas');
    canvas.width = Math.max(1, Math.round(img.width * escala));
    canvas.height = Math.max(1, Math.round(img.height * escala));
    const ctx = canvas.getContext('2d');
    ctx.fillStyle = '#fff'; // el JPEG no tiene transparencia
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
    img.close?.();
    for (const calidad of CALIDADES) {
      const blob = await new Promise((resolver) => canvas.toBlob(resolver, 'image/jpeg', calidad));
      if (blob && blob.size <= MAX_BYTES) {
        return new File([blob], (archivo.name || 'foto').replace(/\.[^.]+$/, '') + '.jpg', { type: 'image/jpeg' });
      }
    }
  } catch { /* se usa la original */ }
  return archivo;
}
