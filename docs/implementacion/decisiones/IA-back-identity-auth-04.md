# IA-back-identity-auth-04 — Actor y hash de idempotencia por ruta en identity

- **Plan:** `back-identity-auth`
- **Paso:** 8 y 13 de la sección 4. Pasos
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-18

## Qué se encontró

La convención común (`back-plataforma-comunes`, ya cerrada) calcula la clave única de idempotencia
como `(actor, método, plantillaRuta, key)` con `actor` = `sub` del JWT o `anonymous`, y el
`request_hash` como SHA-256 del canónico. Para este servicio eso no basta en dos sitios:

- `docs/especificacion/microservicios/identity/base-de-datos.md` § Tablas fija para
  `api_idempotency`: «registro público actor = hash del correo normalizado, no IP». El registro es
  público: no hay `sub`.
- `docs/especificacion/03-contrato-comun.md` § Headers: «El request_hash usa HMAC si el request
  contiene credenciales; nunca se guarda clave/token en payload de idempotencia». El cuerpo del
  registro y el del cambio de clave llevan la clave en claro.

Además la convención tenía `/api/auth/registro` en su lista de rutas exentas de idempotencia,
mientras que `docs/contracts/openapi/identity.yaml` § Registro declara `Idempotency-Key` como
parámetro **requerido**.

## Qué se decidió

1. `/api/auth/registro` sale de la lista de exentas: exige `Idempotency-Key` como manda el contrato.
2. Se añade `IdempotencyCustomizer`, un gancho en la convención con dos métodos (`actorFor` y
   `hashFor`) que devuelven `null` para dejar el comportamiento por defecto. identity lo implementa
   con sus reglas y el resto de servicios no se enteran.
3. Actor de `/api/auth/registro` = SHA-256 hexadecimal del correo normalizado (64 caracteres, cabe
   en `actor varchar(100)` y no deja el correo en claro en la tabla).
4. Hash por HMAC-SHA256 del canónico en `/api/auth/registro` y `/api/auth/password`. Los hash
   predeterminados (SHA-256) siguen igual en el resto de rutas.

## Por qué

La regla de la spec es literal en los dos casos, y el gancho es la forma menos invasiva de cumplirla:
no cambia el comportamiento por defecto de una convención cerrada ni obliga a tocar los otros
ocho módulos. `python scripts/validar-contratos.py --base origin/main` sigue en verde porque no se
toca `docs/contracts/**`.

## Alternativas descartadas

- **Reescribir `IdempotencyFilter` para que sepa de correos y HMAC**: mezcla dos dominios en una
  clase compartida por nueve servicios.
- **Dejar el registro exento y hacer la idempotencia dentro del controlador**: el contrato exige la
  clave obligatoria y el replay con el mismo `status/body`, que es exactamente lo que da el filtro.
- **Guardar el correo en claro como actor**: el contrato de la BD dice *hash*.
- **SHA-256 del cuerpo con clave**: permite recuperar la clave por fuerza bruta desde
  `api_idempotency`, que la spec prohíbe explícitamente.

## A quién afecta

`services/identity-service/src/main/java/pe/hotel/identity/platform/IdempotencyFilter.java`,
`PlatformConfig.java`, `IdempotencyCustomizer.java` (nuevo), `IdempotencyHashes.java` (nuevo) y
`auth/IdentityIdempotencyCustomizer.java` (nuevo). Other services mantienen el comportamiento por
defecto sin tocar nada.