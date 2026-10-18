# IA-back-identity-auth-07 — El rate limit se cuenta por par remoto real, no por `X-Forwarded-For`

- **Plan:** `back-identity-auth`
- **Paso:** 6 de la sección 4. Pasos
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-18

## Qué se encontró

`docs/contracts/errors.md` define `RATE_LIMITED` como «Límite por IP/cuenta (login 5/min, registro
3/min)» y `docs/especificacion/03-contrato-comun.md` § Identidad y sedes pide «Login público y
registro público con rate limit y validación», pero **no dice** de dónde se saca la IP.

Detrás del Gateway, `request.getRemoteAddr()` es la IP del Gateway y no la del cliente. La
alternativa habitual es leer `X-Forwarded-For`, que el cliente puede mandar él mismo.

## Qué se decidió

El contador se lleva por `getRemoteAddr()` (el par remoto real). En la corrida de aceptación, con el
Gateway delante, el límite aplica a todo lo que llega por esa IP: es más restrictivo que el
diseño ideal, pero es el único contador que un cliente no puede esquivar.

## Por qué

Un límite de antiabuso que se puede eludir con una cabecera no protege nada, y el propioGateway
borra los headers de identidad falsificables pero deja pasar `X-Forwarded-For`. El plan fija los
límites (5 y 3 por minuto) y su objetivo declarado es frenar la elevación de cuentas; el coste
aceptado es que en un despliegue con varias instancias detrás de un balanceador habría que migrar a
un contador compartido, algo que el alcance cerrado (sin Redis) deja fuera.

## Alternativas descartadas

- **`X-Forwarded-For` (el primero de la cadena)**: falsificable; el cliente podría rotarlo en cada
  petición para tener un presupuesto ilimitado.
- **`X-Real-IP`**: mismo problema.
- **Contador distribuido en Redis**: está expresamente fuera del alcance cerrado del plan.

## A quién afecta

`services/identity-service/src/main/java/pe/hotel/identity/ratelimit/RateLimitFilter.java`. Con el
Gateway delante, el límite de login (5/min) es efectivo para el despliegue entero: cuando un
cliente consume su cuota, los demás reciben 429 hasta la ventana siguiente. El Gateway ya tiene su
propio límite público de 60/min por IP (`infra-gateway-eureka`), que es donde está la separación
real por IP.