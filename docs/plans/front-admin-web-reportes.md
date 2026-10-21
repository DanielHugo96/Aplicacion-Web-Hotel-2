# front-admin-web-reportes — dashboard y reportes del panel (F4)

| Campo | Valor |
|---|---|
| Plan | `front-admin-web-reportes` |
| Ámbito | `front` |
| Componente | `admin-web` |
| Fase | `F4` |
| Modo | `directo` |
| Estado | `en curso` |
| Depende de | `front-admin-web-base`, `back-reporting-proyecciones` |
| Spec | [`08-admin-web.md` §1 Reglas transversales](../especificacion/08-admin-web.md#1-reglas-transversales), [§2 Rutas Angular y guards](../especificacion/08-admin-web.md#2-rutas-angular-y-guards), [§3 Formularios (Reportes)](../especificacion/08-admin-web.md#3-formularios-campos-validación-y-mensajes), [§4 Estados por pantalla (Dashboard, Reportes)](../especificacion/08-admin-web.md#4-estados-por-pantalla) y [§5 Menú por rol](../especificacion/08-admin-web.md#5-menú-por-rol); [`reporting/api.md` §REST legacy](../especificacion/microservicios/reporting/api.md#rest-legacy), [§Ejemplo](../especificacion/microservicios/reporting/api.md#ejemplo) y [§Alcance](../especificacion/microservicios/reporting/api.md#alcance); [`03-contrato-comun.md` §Transporte](../especificacion/03-contrato-comun.md#transporte-notación-y-tipos), [§Headers](../especificacion/03-contrato-comun.md#headers), [§Respuestas](../especificacion/03-contrato-comun.md#respuestas) y [§Identidad y sedes](../especificacion/03-contrato-comun.md#identidad-y-sedes); [`contracts/README.md` §1](../contracts/README.md#1-reglas-transversales) y [§3.6 Reportes](../contracts/README.md#36-reportes-reporting--sin-cliente); [`admin-web.yaml` rutas de reportes](../contracts/openapi/admin-web.yaml); [`errors.md` Reporting y Gateway](../contracts/errors.md#reporting-y-gateway) y [§Cómo lo usa el frontend](../contracts/errors.md#cómo-lo-usa-el-frontend); [`02-landing-y-panel.md` §Panel: matriz de acceso](../especificacion/02-landing-y-panel.md#panel-matriz-de-acceso) y [§Formatos de UI](../especificacion/02-landing-y-panel.md#formatos-de-ui-obligatorios); [`06-migracion-y-pruebas.md` §4](../especificacion/06-migracion-y-pruebas.md#4-cambios-frontend-obligatorios); [`../DESIGN.md` §2](../DESIGN.md#2-paleta-y-roles), [§4](../DESIGN.md#4-componentes), [§6](../DESIGN.md#6-formatos-obligatorios-de-ui-es-pe), [§7](../DESIGN.md#7-estados-errores-y-recuperación-interacción), [§10](../DESIGN.md#10-tokens-de-diseño-base-para-implementar) y [§11](../DESIGN.md#11-lenguaje-para-pedir-pantallas-nuevas-stitch--diseño) |
| Casos | `—` |
| Change | `—` |

## 1. Objetivo

Al cerrar el plan, `apps/admin-web` muestra el dashboard (`/app/inicio`) y los reportes (`/app/reportes`: ventas, ocupación, cobros y bajo stock) consumiendo el Gateway (`http://localhost:8080`), con filtros por sede y fechas, exportación PDF/Excel generada en el frontend y avisos de consistencia eventual y de 503 verificables contra mocks del OpenAPI y contra el backend real.

## 2. Alcance

**Entra:**

- Pantallas `/app/inicio` (KPIs de reportes de su alcance) y `/app/reportes` (dashboard, ventas, ocupación, cobros, bajo stock, exportar) según [`08-admin-web.md` §2](../especificacion/08-admin-web.md#2-rutas-angular-y-guards), con guards por rol y menú de [§5](../especificacion/08-admin-web.md#5-menú-por-rol).
- Consumo de los cinco GET de [`reporting/api.md` §REST legacy](../especificacion/microservicios/reporting/api.md#rest-legacy) vía Gateway: `GET /api/reportes/dashboard`, `GET /api/reportes/ventas`, `GET /api/reportes/ocupacion`, `GET /api/reportes/cobros` y `GET /api/reportes/productos-bajo-stock`, con queries `idHotel`, `inicio`, `fin`, `limite` y paginación `L`, y lectura de la meta `{updatedAt, consistency, degraded, bootstrapComplete}`.
- Filtros y validaciones del formulario de reportes de [`08-admin-web.md` §3](../especificacion/08-admin-web.md#3-formularios-campos-validación-y-mensajes) (rango, fechas, sede).
- Estados de [`08-admin-web.md` §4](../especificacion/08-admin-web.md#4-estados-por-pantalla) para Dashboard y Reportes (vacío, skeleton, error, degradado, 503 con reintento).
- Exportación PDF/Excel generada en el frontend sobre los resultados autorizados, sin endpoints de archivos, según el [§Alcance de reporting](../especificacion/microservicios/reporting/api.md#alcance) y [§3.6 de contracts](../contracts/README.md#36-reportes-reporting--sin-cliente).
- Mensajes para UI y acciones esperadas tomados del catálogo [`errors.md`](../contracts/errors.md), con `correlationId` visible en el estado de error.
- Estilo con los tokens de [`../DESIGN.md` §10](../DESIGN.md#10-tokens-de-diseño-base-para-implementar) y cierre por pantalla con el checklist de [§11](../DESIGN.md#11-lenguaje-para-pedir-pantallas-nuevas-stitch--diseño), con formatos es-PE obligatorios.

**Fuera de alcance:**

- Backend de reportes (proyecciones, bootstrap, watermarks, cálculo de `ingresosHoy`) → plan `back-reporting-proyecciones`; aquí solo se consume y se presenta lo que el backend calcula, sin recalcular importes.
- Base del panel (login, registro, guards, layout, menú, interceptor con `Idempotency-Key`/`ETag`/401/202, pipes, perfil y clave) → plan `front-admin-web-base`; este plan la reutiliza sin duplicarla.
- Otros módulos del panel (personas, sedes, recepción, cierre, productos, ventas, área de cliente) → sus planes `front-*`; el rol CLIENTE no ve reportes.
- Mensajería: el [catálogo §2](catalogo.md#2-mensajería) no asigna a este plan ningún topic, comando ni grupo; no se implementa ningún consumidor ni productor.
- Persistencia, migraciones y seeds: este plan no toca ninguna base.
- Endpoints de archivos, reservas, pasarela de pago, facturación, fidelización, app móvil, Kubernetes, Redis, Debezium y Config Server, según el alcance cerrado de la especificación.

## 3. Contexto

- Este plan cubre la parte de reportes del criterio de salida de F4 ([norma §2](README.md#2-fases)) y depende de `front-admin-web-base` y `back-reporting-proyecciones` ([catálogo §1](catalogo.md#1-planes)); para ejecutar, ambas deben estar `cerrado` ([norma §6.2](README.md#6-contrato-con-la-ejecutora)). Los casos `REP01–REP03` los implementa `back-reporting-proyecciones` y los ejecuta `qa-matriz-aceptacion` ([catálogo §4](catalogo.md#4-casos-de-aceptación)); este plan no es dueño de ningún caso.
- La spec fija y este plan no redefine: rutas Angular `/app/inicio` y `/app/reportes`, roles (A todas, E asignada, C sin reportes) y guards de [`08-admin-web.md` §2](../especificacion/08-admin-web.md#2-rutas-angular-y-guards) y [§5](../especificacion/08-admin-web.md#5-menú-por-rol); reglas transversales (Gateway, JWT en memoria, 401 → relogin, 202 → pendiente con polling, formatos, errores con `correlationId`) de [§1](../especificacion/08-admin-web.md#1-reglas-transversales); validaciones de inicio/fin y mensajes de [§3](../especificacion/08-admin-web.md#3-formularios-campos-validación-y-mensajes); estados de [§4](../especificacion/08-admin-web.md#4-estados-por-pantalla); matriz de acceso de [`02`](../especificacion/02-landing-y-panel.md#panel-matriz-de-acceso) y formatos de [§Formatos](../especificacion/02-landing-y-panel.md#formatos-de-ui-obligatorios); los cinco GET, sus queries, el intervalo `[inicio, fin)` de 366 días máximo, la medianoche Lima de ocupación y la meta `EVENTUAL` de [`reporting/api.md`](../especificacion/microservicios/reporting/api.md#rest-legacy); base, headers, envelope y paginación de [`contracts/README.md` §1](../contracts/README.md#1-reglas-transversales) y [§3.6](../contracts/README.md#36-reportes-reporting--sin-cliente); identidad solo por JWT validado y nunca por headers falsificables de [`03` §Headers](../especificacion/03-contrato-comun.md#headers) y [§Identidad y sedes](../especificacion/03-contrato-comun.md#identidad-y-sedes); códigos, mensajes y acciones de [`errors.md`](../contracts/errors.md); cambios frontend de [`06` §4](../especificacion/06-migracion-y-pruebas.md#4-cambios-frontend-obligatorios); y sistema visual, tokens y checklist de [`../DESIGN.md`](../DESIGN.md).
- Decisiones de este plan (la spec no las fija): ninguna regla de negocio ni formato nuevo; la ubicación concreta de componentes y servicios dentro de `apps/admin-web/` y el orden de los pasos, que se fijan al ejecutar y se anotan en el PR; la librería concreta de exportación se elige al ejecutar entre las existentes compatibles con Angular 22.2.2, sin añadir endpoints ni cambiar columnas respecto a lo autorizado.
- Versiones fijadas: Angular 22.2.2 con locale es-PE; consumo siempre contra el Gateway (`http://localhost:8080`); avance con mocks del OpenAPI permitido, nunca contra puertos de servicios. Sin secretos en el bundle ([norma §6.10](README.md#6-contrato-con-la-ejecutora)). Este plan solo toca `apps/admin-web/**` y su propio archivo, sin cambiar `docs/especificacion/**`, `docs/contracts/errors.md`, `docs/plans/README.md` ni `docs/plans/_prompts.md`.

## 4. Pasos

- [ ] 1. Reutilizar la base de `front-admin-web-base` (layout, menú, guards, interceptor, pipes es-PE, catálogo de mensajes) y colgar `/app/inicio` y `/app/reportes` de ese shell sin duplicarla. Verificación: `apps/admin-web` define ambas rutas bajo el layout con `authGuard` y `rolGuard`; `npm run build` del panel termina en verde.
- [ ] 2. Crear la capa de acceso a reportes contra el Gateway (`http://localhost:8080`) con `GET /api/reportes/dashboard`, `GET /api/reportes/ventas`, `GET /api/reportes/ocupacion`, `GET /api/reportes/cobros` y `GET /api/reportes/productos-bajo-stock`, con queries `idHotel`, `inicio`, `fin`, `limite`, `page` y `size`, y tipado de la meta `EVENTUAL`; solo GET, sin `Idempotency-Key` ni `If-Match`. Verificación: la búsqueda de puertos de servicios y de Eureka en `apps/admin-web` devuelve 0 resultados; las pruebas del servicio contra mocks del OpenAPI están en verde.
- [ ] 3. Implementar el dashboard (`/app/inicio`) con los KPIs del backend (`habitacionesOcupadas`, `habitacionesDisponibles`, `productosBajoStock`, `ingresosHoy`, `moneda PEN`), selector de sede (A consolidado sin `idHotel`, E con su sede asignada), skeleton de KPIs y vacío exacto. Verificación: contra mock, los importes se ven con pipes es-PE y el vacío muestra “Sin datos para tu sede todavía”.
- [ ] 4. Implementar los filtros de `/app/reportes` (`inicio`, `fin` requeridos con `inicio < fin`, rango máximo 366 días, ISO-8601 con offset; ocupación solo medianoche America/Lima; `idHotel` requerido salvo dashboard consolidado de A; `limite` por defecto 5). Verificación: las pruebas unitarias cubren rango excedido con el mensaje “El rango no puede superar 366 días” y fecha de ocupación fuera de medianoche rechazada.
- [ ] 5. Implementar las cuatro tablas (ventas agregadas solo PAGADO por `paid_at`, ocupación por fecha de entrada Lima, cobros por `fechaCierre` con desglose por medio de pago, bajo stock) con paginación `L` desde `meta.page/size/totalElements` y orden del backend; sin filas, `data: []` con “Sin resultados” y nunca 404. Verificación: mock con más de una página pagina correctamente; mock vacío muestra “Sin resultados”.
- [ ] 6. Aplicar formatos es-PE y vocabulario controlado: moneda `S/ 1,234.50`, fechas `dd/MM/yyyy`, timestamps `dd/MM/yyyy HH:mm`, cifras tabulares con importes a la derecha, importes mostrados del backend sin recalcular, ocupación como “Libre” / “Ocupada” / “Sin datos” y nunca “Disponible”. Verificación: prueba unitaria del formato exacto `S/ 1,234.50` y revisión visual de fechas y etiquetas.
- [ ] 7. Mostrar la consistencia eventual: banner ámbar con el aviso “datos eventualmente consistentes” cuando la meta trae `degraded: true`, con `updatedAt` y `bootstrapComplete` visibles y sin bloquear la vista; no prometer frescura absoluta. Verificación: mock con `degraded: true` muestra el banner con los tokens de aviso; mock sin degradación lo oculta.
- [ ] 8. Tratar el 503 de reportes con el catálogo: `REPORTING_NOT_READY` muestra “Los reportes aún se están preparando.” y `REPORTING_UNAVAILABLE` muestra “Los reportes no están disponibles en este momento.”, con espera de `Retry-After`, botón de reintento de la misma GET y `correlationId` copiable. Verificación: mocks 503 por código muestran el mensaje exacto, la acción de reintento y el `correlationId` en mono.
- [ ] 9. Tratar los errores transversales con el catálogo: `VALIDATION_ERROR` con `error.fields` resaltados, `UNAUTHENTICATED` con relogin, `FORBIDDEN` sin filtrar sede ajena, `RESOURCE_NOT_FOUND` genérico y `RATE_LIMITED` con espera; sin redactar mensajes propios. Verificación: mocks por código muestran el mensaje para UI y la acción esperada de [`errors.md`](../contracts/errors.md).
- [ ] 10. Cerrar roles y guards: A ve todas las sedes y el consolidado, E solo su sede asignada, C no tiene `/app/inicio` ni `/app/reportes` (redirect a su pantalla inicial y 403 real del backend); rol y sede del JWT solo como UX; identidad nunca por `X-User-Id`, `X-Role` ni `X-Hotel-Id`. Verificación: pruebas de guards por rol en verde y búsqueda de esas cabeceras en el código con 0 usos.
- [ ] 11. Implementar la exportación PDF/Excel en el frontend sobre los resultados autorizados visibles, sin endpoints de archivos, con la tipografía del panel, reglas hairline y encabezados legibles en escala de grises. Verificación: con un mock cargado, cada exportación genera un archivo que se abre y contiene las mismas filas y columnas de la tabla.
- [ ] 12. Aplicar los tokens de [`../DESIGN.md` §10](../DESIGN.md#10-tokens-de-diseño-base-para-implementar) (piedra, carmín, funcionales solo para estado, radios, sombras, tipografías) y pasar el checklist de [§11](../DESIGN.md#11-lenguaje-para-pedir-pantallas-nuevas-stitch--diseño) en Dashboard y en cada reporte. Verificación: checklist marcado por pantalla y `npm run build` en verde; sin colores, tipografías, componentes ni movimientos fuera del sistema.
- [ ] 13. Ejecutar las pruebas del panel y el validador con los comandos canónicos de la sección «Verificación» del `README.md` raíz. Verificación: `npm ci && npm run build && npm test -- --watch=false` en `apps/admin-web` en verde y `python scripts/validar-planes.py` con 0 errores.
- [ ] 14. Cerrar con integración real contra el Gateway (`http://localhost:8080`) con `back-reporting-proyecciones` ya `cerrado`, sin mocks ni puertos de servicios. Verificación: dashboard y los cuatro reportes responden 200 o 503 del catálogo con meta `EVENTUAL`; las respuestas y capturas quedan adjuntas al PR de ejecución.

## 5. Contratos afectados

- OpenAPI: consumo (sin alta ni cambio) de `contracts/openapi/admin-web.yaml`: `GET /api/reportes/dashboard`, `GET /api/reportes/ventas`, `GET /api/reportes/ocupacion`, `GET /api/reportes/cobros` y `GET /api/reportes/productos-bajo-stock`, con sus queries, paginación y meta de reporte. Si al ejecutar aparece una discrepancia con la spec, no se inventa ninguna ruta ni query.
- Eventos y comandos: ninguno; el catálogo no asigna mensajería a este plan y no se consume ni se produce nada.
- Persistencia: ninguna tabla ni migración; el frontend no crea seeds ni checkpoints.
- Errores: `REPORTING_NOT_READY`, `REPORTING_UNAVAILABLE` y `DEPENDENCY_UNAVAILABLE` para el 503 de reportes, más los transversales `VALIDATION_ERROR`, `UNAUTHENTICATED`, `FORBIDDEN`, `RESOURCE_NOT_FOUND` y `RATE_LIMITED`, todos con el mensaje para UI y la acción esperada de [`errors.md`](../contracts/errors.md).

## 6. Pruebas

No hay casos de la [matriz de aceptación](../especificacion/06-migracion-y-pruebas.md#5-matriz-de-aceptación-funcional) asignados a este plan (`Casos: —`). La evidencia es el consumo y la presentación verificados con mocks del OpenAPI y con integración real:

- Dashboard con mock: KPIs del ejemplo de reporting con `ingresosHoy` mostrado tal cual, skeleton y vacío “Sin datos para tu sede todavía”; evidencia en capturas adjuntas al PR.
- Reportes con mocks: filtros válidos e inválidos (rango 366 días), paginación, vacío con “Sin resultados”, banner de consistencia eventual con `degraded: true`, y 503 por código con `correlationId`; evidencia en respuestas mock y capturas adjuntas al PR.
- Roles: A consolidado y por sede, E sede asignada y 404 ante sede ajena, C sin acceso con 403 real; evidencia en respuestas del Gateway adjuntas al PR.
- Exportación: PDF y Excel generados en el frontend con las filas autorizadas; evidencia en archivos de muestra adjuntos al PR.
- Comandos: los de la sección «Verificación» del `README.md` raíz (build y pruebas del panel, Compose e integración contra el Gateway) y `python scripts/validar-planes.py`; resultado esperado 0 errores; evidencia en las salidas adjuntas al PR de ejecución ([norma §6.8](README.md#6-contrato-con-la-ejecutora)).

## 7. Preguntas abiertas

Ninguna.

## 8. Criterio de terminado

- [ ] Todos los pasos marcados con su verificación.
- [ ] Dashboard y los cuatro reportes consumen el Gateway con filtros, paginación, formatos es-PE y meta `EVENTUAL` presentados según la spec.
- [ ] Avisos de consistencia eventual y de 503 con los mensajes del catálogo, reintento y `correlationId` verificados.
- [ ] Exportación PDF/Excel en el frontend verificada sin endpoints de archivos.
- [ ] Checklist de [`../DESIGN.md` §11](../DESIGN.md#11-lenguaje-para-pedir-pantallas-nuevas-stitch--diseño) pasado en cada pantalla y `python scripts/validar-planes.py` sin errores.
- [ ] Evidencia de la sección 6 adjunta al PR de ejecución.
- [ ] Sin preguntas abiertas.
