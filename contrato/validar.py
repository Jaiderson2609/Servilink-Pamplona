import sys

import yaml
from openapi_spec_validator import validate

RUTA = sys.argv[1] if len(sys.argv) > 1 else "contrato/openapi.yaml"
with open(RUTA, encoding="utf8") as f:
    spec = yaml.safe_load(f)
validate(spec)
print("1) Esquema OpenAPI 3.0 válido")
glob = "security" in spec
fallos = []
n = 0
for ruta, ops in spec["paths"].items():
    for verbo, op in ops.items():
        if verbo not in ("get", "post", "put", "patch", "delete"):
            continue
        n += 1
        explicita = "security" in op
        if not (glob or explicita):
            fallos.append(f"{verbo.upper()} {ruta}: sin seguridad declarada")
        if verbo == "get" and "requestBody" in op:
            fallos.append(f"GET {ruta}: tiene cuerpo")
        if "401" not in op["responses"] and op.get("security", [1]) != []:
            fallos.append(f"{verbo.upper()} {ruta}: protegido sin respuesta 401")
print(f"2) {n} operaciones revisadas; seguridad global declarada: {glob}")
publicas = [
    f"{v.upper()} {r}"
    for r, o in spec["paths"].items()
    for v, op in o.items()
    if v in ("get", "post", "put", "patch", "delete") and op.get("security") == []
]
print("   Públicas (security: [] explícito):", publicas)
sens = {"cedula", "telefono", "direccion"}


def campos(sch, ruta=""):
    out = []
    for k, v in sch.get("properties", {}).items():
        if k.lower() in sens:
            out.append(ruta + k)
        out += campos(v, ruta + k + ".")
        if "items" in v:
            out += campos(v["items"], ruta + k + "[].")
    return out


for nombre, s in spec["components"]["schemas"].items():
    c = campos(s)
    if c:
        print(f"   Esquema {nombre} contiene datos sensibles:", c)
    # Datos propios (perfil, registro) o de uso exclusivo del administrador (UsuarioAdmin); nunca en listados públicos.
    if c and nombre not in ("ContactoAsignado", "PerfilResponse", "RegistroRequest", "UsuarioAdmin"):
        fallos.append(f"{nombre} expone {c}")
print("3) Datos personales solo en ContactoAsignado (visible desde E03), el perfil propio y la vista del administrador")
print("FALLOS:", fallos if fallos else "ninguno")
sys.exit(1 if fallos else 0)
