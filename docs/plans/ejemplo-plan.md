# Plan: identidad y JWT en identity-service

> Plantilla de ejemplo. Copia este archivo, renómbralo según la guía y reemplaza el contenido.

## Objetivo

`identity-service` emite tokens JWT válidos y el Gateway los valida, de modo que un usuario autenticado pueda llamar a un endpoint protegido y uno anónimo reciba 401.

## Alcance

- Incluido: login con BCrypt, emisión de JWT, validación en Gateway, cierre de sesión.
- Excluido: registro público de usuarios, refresh token, recuperación de contraseña.

## Tareas

1. Definir `auth-service` en el Gateway y proteger `/api/**` con el Resource Server JWT.
2. Implementar `POST /api/auth/login` con verificación BCrypt y emisión del token con expiración.
3. Propagar el `X-User-Id` y los roles como headers hacia los servicios, según el contrato común.
4. Escribir la prueba de aceptación: login correcto devuelve 200; password incorrecto devuelve 401.
5. Verificar el flujo completo levantando Gateway, Eureka e identity-service en Docker.

## Dependencias

- Contrato común y seguridad: [`../especificacion/03-contrato-comun.md`](../especificacion/03-contrato-comun.md).
- API y base de datos: [`../especificacion/microservicios/identity/`](../especificacion/microservicios/identity/).
- Rutas del Gateway: [`../especificacion/infraestructura/gateway-eureka-docker.md`](../especificacion/infraestructura/gateway-eureka-docker.md).

## Riesgos

- El token caduca durante una operación larga y el cliente recibe 401 inesperado. Mitigación: definir la expiración con los tiempos de la prueba de aceptación y documentarla en el contrato común.
- Desalineación de los nombres de claim entre identity y Gateway. Mitigación: declarar los claims en el contrato común, no en el código de cada servicio.

## Criterios de aceptación

- [ ] `POST /api/auth/login` con credenciales válidas devuelve 200 y un JWT.
- [ ] Credenciales inválidas devuelven 401 y ningún token.
- [ ] Un endpoint protegido se consume con el token y sin él responde 401.
- [ ] Los headers de identidad llegan al servicio destino.