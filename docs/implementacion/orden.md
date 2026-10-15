# Orden de implementación

Secuencia para construir los 25 planes de [`../plans/`](../plans) con varias personas a la vez.
Las dependencias salen del [`catalogo.md`](../plans/catalogo.md) §1; aquí solo se agrupan en
**tandas** y se marca qué se puede hacer **en paralelo**.

## 1. Precondiciones

- Los 25 planes están escritos y en estado `propuesto`; **la implementación no ha empezado**.
- Un plan solo se ejecuta si está `propuesto` y mergeado en `main`
  ([norma §6.1](../plans/README.md#6-contrato-con-la-ejecutora)).
- **Bloqueado hoy:** `front-admin-web-catalogo` está en el [`catalogo`](../plans/catalogo.md) pero
  su archivo todavía no está en `main`. No se puede ejecutar hasta que su plan entre (al mergearlo,
  enlaza su fila en [`estado.md`](estado.md) y en [`README.md` §3](README.md#3-indice)).
- Los 8 planes `openspec` **ya tienen propuesta escrita** en `openspec/changes/`
  (`back-plataforma-mensajeria`, `back-reception-operacion`, `back-hotel-operaciones`,
  `back-inventory-stock`, `back-sales-ventas`, `back-reception-cierre`,
  `back-notification-worker`, `back-reporting-proyecciones`): su diseño previo está hecho.

## 2. Tandas

Dentro de una tanda, todo lo que aparece en la misma fila puede ir en paralelo. Entre tandas hay
que esperar, salvo los comodines (sección 4).

| Tanda | Fase | En serie dentro de la tanda | Máx. a la vez |
|---|---|---|---|
| **T0** | F0 | `infra-monorepo` | 1 |
| **T1** | F1 | `infra-compose` → (`infra-gateway-eureka` ‖ `back-plataforma-comunes`) → `back-plataforma-mensajeria` | 2 |
| **T2** | F2 | `back-identity-auth` → (`back-hotel-catalogo` ‖ `front-admin-web-base`) → (`back-identity-personas` ‖ `front-public-web-landing`) → `front-admin-web-catalogo` | 3 |
| **T3** | F3 | (`back-reception-operacion` ‖ `back-inventory-stock`) → (`back-hotel-operaciones` ‖ `back-sales-ventas`) → (`front-admin-web-recepcion` ‖ `front-admin-web-ventas`) | 4 |
| **T4** | F4 | `back-reception-cierre` ‖ `back-reporting-proyecciones` → (`front-admin-web-cierre` ‖ `front-admin-web-area-cliente` ‖ `front-admin-web-reportes`) | 4 |
| **T5** | F5 | `qa-runbooks` ‖ `qa-matriz-aceptacion` | 2 |

Cómo leerlo: `‖` significa "en paralelo"; `→` significa "espera a lo anterior".

## 3. Camino crítico

Lo que **no** se puede comprimir, porque es una cadena de dependencias:

```
infra-monorepo → infra-compose → back-plataforma-comunes → back-plataforma-mensajeria
  → back-identity-auth → back-identity-personas → back-reception-operacion
  → back-sales-ventas → back-reception-cierre → back-reporting-proyecciones
  → (front-admin-web-reportes · qa-runbooks) → qa-matriz-aceptacion
```

Son ~12 planes en serie. Todo lo demás se cuelga alrededor y se puede paralelizar. Consecuencia
práctica: **nada del `back` de negocio arranca antes de `back-plataforma-mensajeria`**; meter más
gente en F0/F1 no acelera ese tramo, así que el mejor movimiiento es poner ahí a quien vaya más
rápido y repartir el resto en los carriles de F2 en adelante.

## 4. Comodines (para no quedarse esperando)

Planes que solo dependen de cosas tempranas y sirven para que alguien avance sin esperar a la
tanda que les toca por fase:

- [`back-notification-worker`](../plans/back-notification-worker.md): solo depende de
  `back-plataforma-mensajeria` → puede empezar en **T2** en lugar de T4.
- [`back-reporting-proyecciones`](../plans/back-reporting-proyecciones.md) y
  [`qa-runbooks`](../plans/qa-runbooks.md) comparten casi todas sus dependencias: cuando el back de
  F4 esté cerrado, los dos pueden ir a la vez.
- **Los planes `front` pueden adelantarse con mocks** contra `contracts/openapi/*.yaml`
  ([norma §7](../plans/README.md#7-reglas)): se programa contra el contrato antes de que su `back`
  esté cerrado, pero **no se cierra** el plan sin integración real. Sirve para tener trabajo de
  front en paralelo toda la F3 y la F4.

## 5. Qué NO conviene paralelizar

No es por dependencias, es por **archivos compartidos** (norma §6.4): dos personas en la misma
zona chocan.

| Zona | Quién escribe ahí | Regla |
|---|---|---|
| `apps/admin-web/**` | los 6 planes `front-admin-web-*` | **En serie**, o los lleva una sola persona. |
| `tests/**` y `scripts/**` | los 2 planes `qa-*` | En serie o coordinados. |
| `contracts/openapi/<servicio>.yaml` | el plan `back` de ese servicio | Solo lo toca el dueño de ese servicio. |
| `services/<servicio>-service/**` | el plan `back` de ese servicio | Servicios distintos → paralelo sin riesgo. |
| `docs/plans/<plan>.md` | el plan dueño | Un archivo, una persona. |

## 6. Carriles sugeridos (agnósticos a las personas)

No importa cuántos sean: reparte por carril y asigna personas según cuántos sean.

| Carril | Planes | Notas |
|---|---|---|
| **I · Infra** | `infra-monorepo`, `infra-compose`, `infra-gateway-eureka` | Arranca todo; casi serial. |
| **P · Plataforma e identity** | `back-plataforma-comunes`, `back-plataforma-mensajeria`, `back-identity-auth`, `back-identity-personas`, `back-notification-worker` | Es el camino crítico; el carril más importante. |
| **H · Hotel y operación** | `back-hotel-catalogo`, `back-reception-operacion`, `back-hotel-operaciones`, `back-inventory-stock`, `back-sales-ventas` | Servicios distintos → paraleliza bien con P. |
| **C · Cierre y reporting** | `back-reception-cierre`, `back-reporting-proyecciones` | Depende de H y P. |
| **F · Front** | los 7 planes `front-*` | Todos comparten `apps/admin-web` (salvo landing): **una persona**. |
| **Q · QA** | `qa-runbooks`, `qa-matriz-aceptacion` | Al final. |

- **Si son 2 personas:** una en P (camino crítico) y otra en I → luego H; los fronts con mocks y
  `qa-runbooks` como relleno.
- **Si son 3:** P, H y F(con mocks) desde T2. Es el punto dulce: los tres carriles corren casi
  toda la F3 y la F4 en paralelo.
- **Si son 4 o más:** añade Q (runbooks) y C; el resto de gente entra en el frente de `back` que
  esté abierto, nunca en el mismo servicio ni en `apps/admin-web`.

## 7. Rotación de ramas

Para evitar PRs con conflicto, sobre todo en `docs/plans/catalogo.md` y en las zonas compartidas:
cada persona cierra su PR antes de abrir el siguiente, y actualiza su rama con `main`
(`git pull origin main`) antes de pedir revisión.
