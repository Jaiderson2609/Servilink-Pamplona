// Generador de tráfico para la demostración local: recorre el flujo real de la API y ejercita la asignación.
// Cada 5.ª iteración el precio ofrecido es tan alto que la comisión (10 %) supera el saldo del técnico:
// el hogar lo elige y la asignación se rechaza con INSUFFICIENT_BALANCE, para ver también los rechazos de negocio.
const BASE = process.env.BASE_URL ?? "http://localhost:8080";
const N = Number(process.env.ITERACIONES ?? 60);
const CLAVE = "clave-segura-1"; // cuentas ficticias de la base local de demostración

async function api(metodo, ruta, token, cuerpo) {
  const r = await fetch(BASE + ruta, {
    method: metodo,
    headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: cuerpo ? JSON.stringify(cuerpo) : undefined,
  });
  const texto = await r.text();
  return { estado: r.status, json: texto ? JSON.parse(texto) : null };
}

async function cuenta(rol, correo) {
  await api("POST", "/api/v1/auth/register", null,
    { nombre: `Demo ${rol} ${correo}`, correo, telefono: "3001234567", clave: CLAVE, rol, aceptaTerminos: true });
  return (await api("POST", "/api/v1/auth/login", null, { correo, clave: CLAVE })).json.accessToken;
}

const marca = Date.now();
const resultados = {};
for (let i = 0; i < N; i++) {
  const rechazo = i % 5 === 4;
  const hogar = await cuenta("HOGAR", `h${marca}-${i}@demo.local`);
  const tecnico = await cuenta("TECNICO", `t${marca}-${i}@demo.local`);
  await api("POST", "/api/v1/wallet/topups", tecnico, { valorCop: rechazo ? 45000 : 50000 });
  const sol = await api("POST", "/api/v1/service-requests", hogar,
    { oficioId: 2, descripcion: "Cambiar un tomacorriente", ubicacion: { lat: 7.3772, lng: -72.6473 } });
  const pos = await api("POST", `/api/v1/service-requests/${sol.json.id}/applications`, tecnico, { precioCop: rechazo ? 500000 : 20000 });
  const sel = await api("PATCH", `/api/v1/applications/${pos.json.id}/select`, hogar);
  resultados[sel.estado] = (resultados[sel.estado] ?? 0) + 1;
  if (i % 10 === 9) console.log(`${i + 1}/${N}`, resultados);
}
console.log("Listo. Respuestas de la asignación por código HTTP:", resultados);
