# Evidencia real — front-admin-web-reportes

Ejecutado el **2026-10-21** desde la rama `front-admin-web-reportes`, con el Gateway en
`http://localhost:8080` y todos los servicios registrados en Eureka:

```text
API-GATEWAY UP 8080          REPORTING-SERVICE UP 8086
IDENTITY-SERVICE UP 8081     SALES-SERVICE UP 8085
HOTEL-SERVICE UP 8082        RECEPTION-SERVICE UP 8083
INVENTORY-SERVICE UP 8084    NOTIFICATION-SERVICE UP 8087
```

Infraestructura por Compose (`hotel-postgres-1`, `hotel-kafka-1`, `hotel-rabbitmq-1`,
`hotel-mailpit-1`) con healthcheck verde. `back-reporting-proyecciones` está cerrado (PR #107) y
sirve las proyecciones que este plan consume.

Sin mocks en esta sección: todo lo de abajo son respuestas HTTP reales del Gateway y capturas de la
app servida con `ng serve` en `http://localhost:4200`. Los tokens humanos se obtienen con
`POST /api/auth/login` y **no se transcriben** (el prefijo de la clave está en `.env`, que Git
ignora).

## 1. Comandos canónicos

En `apps/admin-web`:

```text
$ npm ci
added 281 packages, and audited 282 packages in 21s
2 vulnerabilities (1 moderate, 1 high)

$ npm run build
Application bundle generation complete. [8.519 seconds]
▲ WARNING bundle initial exceeded maximum budget. Budget 500.00 kB was not met by 12.51 kB
  with a total of 512.51 kB.
Output location: apps/admin-web/dist/admin-web

$ npm test -- --watch=false
 Test Files  49 passed (49)
      Tests  305 passed (305)

$ TZ=UTC npm test -- --watch=false
 Test Files  49 passed (49)
      Tests  305 passed (305)

$ npm test -- --watch=false      (TZ local America/Lima)
 Test Files  49 passed (49)
      Tests  305 passed (305)
```

El aviso de presupuesto inicial (512.51 kB frente a 500 kB) es **preexistente**: ya estaba en
`front-admin-web-ventas` (511.28 kB) y `front-admin-web-cierre` (511.89 kB); este plan añade
~1.2 kB de rutas lazy y no cambia el límite de `angular.json`.

En la raíz del repositorio:

```text
$ python scripts/validar-planes.py
32 plan(es) revisado(s); 32 previsto(s) en el catálogo; 0 error(es), 0 aviso(s).

$ python scripts/validar-contratos.py --base origin/main
Contratos: 8 OpenAPI, 20 schemas de evento. Regla: aditivos frente a origin/main.
Los contratos solo se han ampliado. Correcto.
```

## 2. Integración real por Gateway (paso 14)

Tokens: ADMIN `admin@hotel.test`, EMPLEADO `recepcion.lima@hotel.test` (sede 1),
CLIENTE `cliente.uno@hotel.test`. Rango de ejemplo `inicio=2026-09-20T00:00:00-05:00` y
`fin=2026-10-21T00:00:00-05:00` (offset de America/Lima, rango semiabierto).

```bash
PW=$(grep '^DEMO_PASSWORD=' .env | cut -d= -f2- | tr -d '\r')
ADMIN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d "{\"correo\":\"admin@hotel.test\",\"clave\":\"$PW\"}" | python -c 'import sys,json;print(json.load(sys.stdin)["data"]["token"])')
```

### 2.1 Dashboard

`GET /api/reportes/dashboard` como ADMIN → **HTTP 200**

```json
{"success": true, "message": "OK", "data": {"habitacionesOcupadas": 1, "habitacionesDisponibles": 3,
 "productosBajoStock": 19, "ingresosHoy": 0.0, "moneda": "PEN"},
 "meta": {"correlationId": "8199c4a3-ec13-4497-86ff-38c2e4f0e2d9",
 "updatedAt": "2026-10-20T19:13:29.746035Z", "consistency": "EVENTUAL", "degraded": true,
 "bootstrapComplete": true}}
```

`GET /api/reportes/dashboard?idHotel=1` como ADMIN → **HTTP 200**

```json
{"success": true, "message": "OK", "data": {"habitacionesOcupadas": 0, "habitacionesDisponibles": 1,
 "productosBajoStock": 11, "ingresosHoy": 0.0, "moneda": "PEN"},
 "meta": {"correlationId": "97b4d58f-d92e-425e-a961-4e2a92566f16",
 "updatedAt": "2026-10-20T19:13:29.746035Z", "consistency": "EVENTUAL", "degraded": true,
 "bootstrapComplete": true}}
```

`ingresosHoy` vale `0.0` en la corrida final porque el dashboard suma **el día en curso** y la
comprobación se repitió pasada la medianoche de Lima; en la primera corrida, el mismo endpoint con
el día 20/10 devolvió `3642.5` (consolidado) y `3142.5` (sede 1). Es el comportamiento del backend,
no un fallo de la pantalla: el valor se muestra tal cual llega, sin recalcular
(`00-dashboard.png` con `S/ 0.00`, `05-dashboard-empleado.png` con la sede del E).

### 2.2 Los cuatro reportes (ADMIN, sede 1)

`GET /api/reportes/ventas?idHotel=1&…&page=0&size=2` → **HTTP 200**

```json
{"success": true, "message": "OK",
 "data": [{"idProducto": 1, "nombreProducto": "Agua 500ml", "cantidadTotal": 12, "totalIngresado": 42.0},
          {"idProducto": 2, "nombreProducto": "Snack", "cantidadTotal": 6, "totalIngresado": 48.0}],
 "meta": {"correlationId": "a3a45e01-e8d7-48d4-b0c7-0b7ba0a6e697",
 "updatedAt": "2026-10-20T19:13:29.746035Z", "consistency": "EVENTUAL", "degraded": true,
 "bootstrapComplete": true, "page": 0, "size": 2, "totalElements": 8}}
```

`…&page=1&size=2` → **HTTP 200** con `meta.page: 1`, `meta.size: 2`, `meta.totalElements: 8` y las
filas 3 y 4 (`Producto integración 8943799E editado`, `Producto integración 9CDB0752 editado`): la
paginación `L` del contrato funciona y el orden lo decide el backend.

`GET /api/reportes/ocupacion?idHotel=1&…&page=0&size=2` → **HTTP 200**

```json
{"data": [{"idHabitacion": 101, "numeroHabitacion": "101", "descripcionCategoria": "Individual", "vecesAlquilada": 22},
          {"idHabitacion": 102, "numeroHabitacion": "102", "descripcionCategoria": "Doble", "vecesAlquilada": 9}],
 "meta": {"correlationId": "72f7f5ce-45f8-414b-9a56-9829d2850743", "page": 0, "size": 2, "totalElements": 2, …}}
```

`GET /api/reportes/cobros?idHotel=1&…&page=0&size=2` → **HTTP 200**

```json
{"data": [{"idRecepcion": 1, "numeroHabitacion": "101", "nombreCliente": "Cliente Uno",
  "totalAlojamiento": 70.0, "totalConsumos": 0.0, "totalGeneral": 70.0,
  "medioPagoAdelanto": "EFECTIVO", "medioPagoCierre": "EFECTIVO",
  "fechaCierre": "2026-10-20T16:03:23.991393Z"}, …],
 "meta": {"correlationId": "8dea16ba-5ee0-4b41-bc52-18b9d8e24ac3", "page": 0, "size": 2, "totalElements": 19, …}}
```

`GET /api/reportes/productos-bajo-stock?idHotel=1&limite=5&page=0&size=3` → **HTTP 200**

```json
{"data": [{"idProducto": 1, "nombreProducto": "Agua 500ml", "cantidad": 3, "precio": 3.5, "estado": true},
          {"idProducto": 2, "nombreProducto": "Snack", "cantidad": 4, "precio": 8.0, "estado": true},
          {"idProducto": 3, "nombreProducto": "Kit de aseo", "cantidad": 5, "precio": 5.0, "estado": true}],
 "meta": {"correlationId": "b4cea86d-8a32-4d72-84af-3a981fd41c08", "page": 0, "size": 3, "totalElements": 11, …}}
```

### 2.3 Roles y guards (paso 10)

| Petición | Actor | Resultado |
|---|---|---|
| `GET /api/reportes/ventas?idHotel=1&…` | EMPLEADO (sede 1) | **HTTP 200** con las mismas filas |
| `GET /api/reportes/ventas?idHotel=2&…` | EMPLEADO (sede 1) | **HTTP 404** `RESOURCE_NOT_FOUND` |
| `GET /api/reportes/dashboard` | EMPLEADO | **HTTP 400** `VALIDATION_ERROR` `fields.idHotel = "obligatorio para tu rol"` |
| `GET /api/reportes/ventas?idHotel=1&…` | CLIENTE | **HTTP 403** `FORBIDDEN` |
| `GET /api/reportes/dashboard` | sin token | **HTTP 401** `UNAUTHENTICATED` |

Cuerpos literales de los tres últimos:

```json
{"success": false, "message": "No encontramos lo que buscas.", "data": null,
 "error": {"code": "RESOURCE_NOT_FOUND", "fields": {}},
 "meta": {"correlationId": "30086812-8d3e-485b-ab91-07460d4de2e9", "updatedAt": null}}

{"success": false, "message": "No tienes permiso para esta acción.", "data": null,
 "error": {"code": "FORBIDDEN", "fields": {}},
 "meta": {"correlationId": "74c046fb-32b1-406d-bede-6784e2374de9"}}

{"success": false, "message": "Tu sesión expiró. Vuelve a iniciar sesión.", "data": null,
 "error": {"code": "UNAUTHENTICATED", "fields": {}},
 "meta": {"correlationId": "f92d7109-1760-4281-b560-820f8a355af5"}}
```

En la UI, con el CLIENTE autenticado: la barra del área de cliente no tiene el enlace «Reportes»
(captura `07-cliente-sin-reportes.png`) y navegar a `/app/reportes` por `popstate` devuelve a
`/mi-estadia` (redirect del `rolGuard`, sin mostrar datos).

### 2.4 Validaciones del contrato (paso 4)

`GET /api/reportes/ventas?idHotel=1&inicio=2025-10-01…&fin=2026-10-21…` (367 días) → **HTTP 400**

```json
{"success": false, "message": "Revisa los datos marcados.", "data": null,
 "error": {"code": "VALIDATION_ERROR", "fields": {"inicio": "el intervalo supera 366 días"}},
 "meta": {"correlationId": "1eb8833b-c86e-49dc-a77d-e1c473488560", "updatedAt": null}}
```

`GET /api/reportes/ocupacion?idHotel=1&inicio=2026-09-20T03:00:00-05:00&…` → **HTTP 400**

```json
{"success": false, "message": "Revisa los datos marcados.", "data": null,
 "error": {"code": "VALIDATION_ERROR", "fields": {"inicio": "ocupación exige medianoche America/Lima"}},
 "meta": {"correlationId": "b4701294-b2a5-4515-980a-f7a69e49fc4d", "updatedAt": null}}
```

### 2.5 Consistencia eventual

Las cinco respuestas reales traen `"consistency": "EVENTUAL"`, `"degraded": true`,
`"bootstrapComplete": true` y `updatedAt: "2026-10-20T19:13:29.746035Z"`. La UI muestra el banner
ámbar «Datos eventualmente consistentes» y la línea
`Consistencia EVENTUAL · Actualizado: 20/10/2026 14:13 · Proyecciones listas: sí` sin bloquear la
tabla (capturas `00-dashboard.png`, `01-reportes-ventas.png`).

El 503 se verificó por prueba unitaria contra el catálogo (`reportes.component.spec.ts`,
`inicio.component.spec.ts`, `reportes.service.spec.ts`) porque el backend real responde 200 con las
proyecciones ya listas; no se forzó un 503 sobre el servicio en marcha.

## 3. Capturas

| Archivo | Qué muestra |
|---|---|
| `00-dashboard.png` | `/app/inicio` ADMIN con sede 1: KPIs, `S/ 0.00` de `ingresosHoy` del día en curso, banner y meta EVENTUAL |
| `01-reportes-ventas.png` | Ventas reales (8 filas), importes `S/ 42.00`, paginación `Página 1 de 1 · 8 registros` |
| `02-reportes-ocupacion.png` | Ocupación con píldoras «Ocupada» (nunca «Disponible») |
| `03-reportes-cobros.png` | Cobros con desglose por medio de pago y `20/10/2026 11:03` |
| `04-reportes-bajo-stock.png` | Bajo stock con `limite=5`, `S/ 3.50` y estado «Activo» |
| `05-dashboard-empleado.png` | EMPLEADO: su sede asignada, sin opción «Consolidado» |
| `06-reportes-empleado.png` | EMPLEADO en `/app/reportes`: solo su sede |
| `07-cliente-sin-reportes.png` | CLIENTE: sin «Reportes» en el menú |
| `08-dashboard-1024.png` | Dashboard a 1024 px, sin desbordamiento horizontal |
| `09-reportes-1024.png` | Reportes a 1024 px, tabla dentro del contenedor |
| `10-filtro-366-dias.png` | Mensaje exacto «El rango no puede superar 366 días» en el campo |

## 4. Exportación (paso 11)

Con la tabla de ventas real cargada (8 filas) se interceptó `URL.createObjectURL` en el navegador
para inspeccionar los blobs que produce la propia pantalla, sin tocar el backend:

```text
PDF:  application/pdf, 2448 bytes, nombre reporte-ventas-2026-10-20.pdf
XLS:  application/vnd.ms-excel, 3424 bytes, nombre reporte-ventas-2026-10-20.xls
```

Comprobación del contenido contra las filas de la tabla:

```text
filasTabla: 8
faltan en PDF: []            (tras desescapar las cadenas `(…) Tj` de WinAnsi)
hay "..." en el PDF: false
"Página 1 de 1" en el PDF: true
faltan en XLS: []
```

Los archivos de muestra son `reporte-ventas-muestra.pdf` y `reporte-ventas-muestra.xls`, generados
con el mismo generador. `pypdf` abre el PDF y extrae el texto completo, encabezado incluido:

```text
pages 1
Ventas por producto
Hotel Demo Lima Centro · del 2026-09-20 al 2026-10-20
Producto Cantidad Total ingresado
Agua 500ml 12 42
Snack 6 48
Producto integración 8943799E editado 4 46
Página 1 de 1
```

El PDF no comprime el stream (no lleva `/Filter /FlateDecode`) y usa las fuentes base
`/BaseFont /Helvetica` y `/BaseFont /Helvetica-Bold` en escala de grises (`0.15 g`, `0.85 G`); el
Excel es SpreadsheetML 2003 con celdas numéricas reales (`<Data ss:Type="Number">12</Data>`) y
juntas hairline `#DCD8CC`. Ninguno de los dos llama a un endpoint de archivo: se generan en el
navegador (`URL.createObjectURL`) y se descargan con un `<a download>`.

## 5. Sin cabeceras falsificables ni puertos de servicio

```text
$ grep -rn "X-User-Id\|X-Role\|X-Hotel-Id" apps/admin-web/src --include=*.ts
… solo en las aserciones de los .spec.ts que comprueban que NO se envían

$ grep -rn "8081\|8082\|8083\|8084\|8085\|8761\|eureka\|Eureka" apps/admin-web/src
(sin resultados)
```

`API_BASE_URL` sigue siendo el único punto de salida y vale `http://localhost:8080`.
