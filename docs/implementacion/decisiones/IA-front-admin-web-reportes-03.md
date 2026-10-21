# IA-front-admin-web-reportes-03 — el `<select>` de sede usa `ngModel`, no `[value]`

- **Plan:** `front-admin-web-reportes`
- **Paso:** 3 y 4 de la sección 4. Pasos (dashboard y filtros)
- **Nivel:** 3 (decidir, aplicar y marcar)
- **Fecha:** 2026-10-21

## Qué se encontró

Verificado en el navegador real con `ng serve` y el Gateway, no solo en pruebas: en `/app/inicio`
el `<select id="dashboard-sede">` **mostraba «Consolidado de todas las sedes»** mientras los KPIs
en pantalla eran los de la sede 1 (`S/ 3,142.50`, `habitacionesDisponibles: 1`), que es la sede con
la que `SedeContextService` había consultado. En `/app/reportes` el mismo patrón: la consulta iba
con `idHotel=1` y el selector podía quedar en la primera opción.

Causa: el `<select>` usa `[value]="sedeSeleccionada() ?? ''"`, pero sus `<option>` se pintan dentro
de `@if`/`@for`. Angular aplica el `value` antes de que existan las opciones, el navegador cae a la
primera opción y la propiedad no se vuelve a escribir porque la señal no cambia.

La spec no fija este detalle de implementación de Angular; solo dice que A ve el consolidado y E su
sede asignada.

## Qué se decidió

Usar `[ngModel]` + `(ngModelChange)` en los dos selectores de sede (dashboard y reportes) con un
método `cambiarSedeValor(valor)`. `ngModel` sí reaplica el valor cuando aparecen las opciones.
Se añadió una prueba de componente que compara `select.value` y el texto de la opción elegida con la
sede de la consulta, para que el defecto no vuelva.

## Por qué

Es la corrección mínima que arregla un defecto visible y verificado: el control debe mostrar la sede
con la que se consultó. No cambia el contrato, ni la forma del estado, ni el servicio de sede; solo
el binding del formulario, que ya importa `FormsModule`.

## Alternativas descartadas

- **Añadir `[selected]` a la opción.** Funciona a medias y depende del orden de pintado; Angular
  avisa de que no se use `selected` con `ngModel`/`value`.
- **Renderizar siempre la opción del consolidado y deshabilitarla.** No resuelve el desfase de
  valor, y para E la opción no debe existir (verificado: la lista de E solo trae su sede).
- **Forzar un `detectChanges` extra con `setTimeout`.** Parche frágil, dependiente de tiempos.

## A quién afecta

`apps/admin-web/src/app/features/inicio/inicio.component.{ts,html}` y
`apps/admin-web/src/app/features/reportes/reportes.component.{ts,html}`, más sus pruebas. Sin
cambios en contratos ni en otros planes.
