# IA-back-hotel-catalogo-01 — Evidencia declarada sin ejecutar en el plan cerrado

- **Plan:** `back-hotel-catalogo`
- **Paso:** 19 de la sección 4. Pasos, y sección 6. Pruebas
- **Nivel:** hallazgo de evidencia (no es una decisión de negocio)
- **Fecha:** 2026-10-19

## Qué se encontró

El plan `back-hotel-catalogo` (cerrado, PR #66, merge `a2a1f3c`) declara en su §6 los casos
`LP01`, `SEC03`, `CRUD01` y `OCC04` con «evidencia: respuestas». Sin embargo, su sección
«Evidencia obtenida» solo muestra:

- `bash scripts/mvn.sh -f services/pom.xml verify` → `BUILD SUCCESS`.
- `python scripts/validar-planes.py` → 0 errores.
- `python scripts/validar-contratos.py --base origin/main` → contratos solo ampliados.
- Las clases `SecurityConventionTests`, `JwtValidatorTests` e `IdempotencyFilterTests`.

No hay ninguna respuesta HTTP de `LP01`, `SEC03`, `CRUD01` ni `OCC04`. Y no pudo haberla: el
servicio no arranca desde `main`. Flyway aborta en V3 con `ERROR: column hab.publica does not
exist`; corregida esa función en un entorno de diagnóstico, Hibernate falla con
`org.hibernate.MappingException: No column with logical name 'id_hotel' in table 'piso'`; y
corregido el mapeo, Spring Data falla con `UnknownPathException: Could not resolve attribute
'idCategoria' of 'pe.hotel.hotel.domain.model.Habitacion'`. Además, el catálogo público devuelve
campos requeridos nulos y `meta: {}` cuando se le hace arrancar con parches de diagnóstico.

## Qué se decidió

Registrar el hallazgo y no dar por ejecutada la evidencia del plan cerrado. El arreglo se hace en
un plan nuevo, [`back-arreglo-hotel-arranque`](../../plans/back-arreglo-hotel-arranque.md), que
corrige el código y vuelve a ejecutar los cuatro casos con respuestas reales.

## Por qué

La norma exige evidencia por caso ([§6.8](../../plans/README.md#6-contrato-con-la-ejecutora)) y
trazabilidad de lo que declara la IA ([§6.11.4](../../plans/README.md#611-ejecución-por-ia-campaña-automática)).
Un plan cerrado sin evidencia ejecutada no cumple «cerrado» ([§4](../README.md#4-qué-significa-cerrado)).
No se reabre el plan ni se edita su documento: los planes cerrados se quedan como registro
([§8](../../plans/README.md#8-ciclo-de-vida)). La corrección va en un plan de arreglo, que es el
mecanismo previsto para una zona rota por un plan ya cerrado
([§6.11.5](../../plans/README.md#611-ejecución-por-ia-campaña-automática)).

## Alternativas descartadas

- **Reabrir `back-hotel-catalogo` y marcar sus pasos de nuevo.** La norma no permite reabrir un
  plan cerrado; el historial de git es el archivo.
- **Editar el §6 del plan cerrado para quitar la evidencia.** Cambiar el registro de un plan
  cerrado oculta el fallo en vez de documentarlo.
- **Aceptar las respuestas de un entorno parcheado como evidencia de `main`.** No representan
  `main` y repetirían el defecto que se está corrigiendo.
- **Bloquear la campaña sin plan de arreglo.** La campaña tiene un mecanismo específico para
  `main` roto; usarlo es la vía prevista.

## A quién afecta

- **`back-inventory-stock`**: bloqueado; su PR #72 quedó cerrado sin mergear y su trabajo sigue en
  `origin/back-inventory-stock`. Su decisión está en
  [`IA-back-inventory-stock-02.md`](IA-back-inventory-stock-02.md).
- **Dependientes directos o transitivos de `back-hotel-catalogo`**: `front-public-web-landing`,
  `back-reception-operacion`, `back-hotel-operaciones`, `back-inventory-stock`,
  `back-sales-ventas`, `back-reception-cierre`, `back-reporting-proyecciones`,
  `front-admin-web-catalogo`, `front-admin-web-recepcion`, `front-admin-web-ventas`,
  `front-admin-web-cierre`, `front-admin-web-reportes`, `front-admin-web-area-cliente`,
  `qa-runbooks` y `qa-matriz-aceptacion`.
- **Contratos**: ninguno cambia. El arreglo hace que el código cumpla `hotel.yaml`; no modifica
  `docs/contracts/**`.
