# IA-back-arreglo-audiencia-maquina-02 — la ruta interna de clientes está montada bajo `/api`, fuera de este arreglo

- **Plan:** `back-arreglo-audiencia-maquina`
- **Paso:** 8 de la sección 4. Pasos (reproducción después del arreglo)
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-19

## Qué se encontró

El paso 8 pide comprobar `reception → identity` llamando a `GET /internal/clientes/{id}` con un
token máquina de `reception-service` y `audience=identity`. Con el validador ya corregido, esa
llamada responde **404 `RESOURCE_NOT_FOUND`**, no 200.

La causa no es el validador: el JWT se acepta (antes del arreglo la misma llamada daba 401
`UNAUTHENTICATED`; ahora llega al controlador). El controlador
[`PersonaController`](../../../services/identity-service/src/main/java/pe/hotel/identity/persona/PersonaController.java)
declara `@RequestMapping("/api")` a nivel de clase y `@GetMapping("/internal/clientes/{id}")` en
el método, así que la ruta efectiva es `GET /api/internal/clientes/{id}`. La especificación
([`identity/api.md` § REST](../../especificacion/microservicios/identity/api.md#rest)), el
contrato ([`identity.yaml`](../../contracts/openapi/identity.yaml), ruta
`/internal/clientes/{id}`) y el plan reparado usan la ruta sin el prefijo `/api`.

Comprobación literal:

```text
$ curl -s -w '\nHTTP %{http_code}\n' -H "Authorization: Bearer $TOKEN" \
    http://localhost:8081/internal/clientes/100
{"success":false,"message":"No encontramos lo que buscas.","data":null,"error":{"code":"RESOURCE_NOT_FOUND","fields":{}},"meta":{"correlationId":"..."}}
HTTP 404

$ curl -s -w '\nHTTP %{http_code}\n' -H "Authorization: Bearer $TOKEN" \
    http://localhost:8081/api/internal/clientes/100
{"success":true,"message":"OK","data":{"idPersona":100,...},"meta":{"correlationId":"..."}}
HTTP 200
```

El defecto es del plan `back-identity-personas` (PR #70), que cerró el controlador con el prefijo
`/api`; su zona es `services/identity-service/**` y su corrección no es la convención de audiencia
que repara este plan.

## Qué se decidió

1. **No se toca el controlador en este plan.** Cambiar `PersonaController` sería corregir un
   endpoint de un plan cerrado desde un arreglo que repara la convención de seguridad; su zona y su
   verificación no son las de este plan y el alcance de §2 lo excluye expresamente.
2. **La comprobación de `reception → identity` se hace contra la ruta realmente montada**
   (`/api/internal/clientes/{id}`), que devuelve 200 con el token de `reception` y `aud=identity`.
   El 404 de `/internal/clientes/{id}` se pega en la evidencia tal cual, con su causa, para no
   presentar como verde una ruta que no lo está.
3. **El defecto de la ruta queda registrado aquí** para que la campaña lo convierta en un plan de
   arreglo de `back-identity-personas` cuando corresponda. No se silencia ni se corrige de paso.

## Por qué

La jerarquía de [`../README.md`](../README.md) §1 da prioridad a la especificación y al contrato:
la ruta correcta es `/internal/clientes/{id}` y el controlador se aparta de ella. Pero la regla
6.11.5 limita un plan de arreglo a la zona del plan que repara: este repara
`back-plataforma-comunes` (los validadores de `services/*-service/platform`), no
`back-identity-personas` (los controladores de identity). Corregir aquí el controlador mezclaría
dos defectos, dos verificaciones y dos planes cerrados en un mismo PR.

La opción elegida es la menos invasiva: no cambia ningún endpoint, no inventa una ruta nueva y no
deja el arreglo de audiencia sin evidencia. El objetivo del plan —que el validador acepte el
destino corto— queda demostrado con el 200 de la ruta montada y con el 401 que ya no aparece.

## Alternativas descartadas

- **Cambiar `@RequestMapping("/api")` por una ruta sin prefijo en `PersonaController`.** Corrige el
  contrato, pero es código de otro plan cerrado y amplía el alcance de este arreglo; además
  obligaría a revisar todas las rutas humanas de personas, que sí viven bajo `/api`.
- **Añadir un `@GetMapping("/internal/clientes/{id}")` en un controlador nuevo.** Crea un endpoint
  duplicado, prohibido por el alcance y por la regla de no inventar endpoints.
- **Dar el paso 8 por bueno con el 404.** Sería fabricar evidencia: la ruta que pide el plan no
  responde 200.
- **Dejar el plan `BLOQUEADO`.** El bloqueo real no es de negocio ni de contrato: la audiencia
  está corregida y verificada; lo que falta es un defecto de otro plan, que se registra para su
  propio arreglo. La campaña sigue sin atascarse.

## A quién afecta

- Este plan: la evidencia del paso 8 se apoya en `/api/internal/clientes/{id}` y deja constancia
  del 404 de `/internal/clientes/{id}`.
- `back-identity-personas` (plan cerrado, PR #70): su controlador no cumple la ruta publicada en
  `docs/contracts/openapi/identity.yaml`. Requiere un plan de arreglo propio.
- `back-reception-operacion`, `back-sales-ventas` y cualquier consumidor de la lectura máquina de
  clientes: hasta que se corrija la ruta, deben usar `/api/internal/clientes/{id}` o esperar el
  arreglo. No cambia ningún contrato de `docs/contracts/**` ni la especificación.
