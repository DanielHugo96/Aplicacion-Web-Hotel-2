# Sistema de diseño: Cadena Hotelera Demo (public-web + admin-web)

**Proyecto:** Aplicacion-Web-Hotel-2 · sin proyecto Stitch asociado (este repositorio es la fuente).
**Versión:** 1.0 · 2026-10-12 · Estado: propuesta de diseño para implementar.
**Aplica a:** `apps/public-web` (landing, dev en `http://localhost:4300`) y `apps/admin-web` (panel + área de cliente, dev en `http://localhost:4200`). Angular 22.2.2, TypeScript ≥ 6.0, locale **es-PE** con `registerLocaleData` en ambas apps.

Este documento es la guía visual y de interacción para el frontend. Se deriva de:

- [`especificacion/02-landing-y-panel.md`](especificacion/02-landing-y-panel.md) — sí/no de la landing, formatos de UI y textos.
- [`especificacion/08-admin-web.md`](especificacion/08-admin-web.md) — rutas, formularios, mensajes y estados por pantalla.
- [`contracts/README.md`](contracts/README.md) y [`contracts/errors.md`](contracts/errors.md) — envelope, headers, catálogo único de errores con mensaje para UI.
- [`contracts/openapi/public-web.yaml`](contracts/openapi/public-web.yaml) y [`contracts/openapi/admin-web.yaml`](contracts/openapi/admin-web.yaml) — contratos de datos.

**Cómo usar este documento:** lo marcado como *obligatorio* proviene de la especificación y manda sobre cualquier preferencia estética. Todo lo demás (paleta, tipografía, layout, componentes) es la propuesta a implementar y a extender con consistencia.

| Tipo de regla | Origen | ¿Manda? |
|---|---|---|
| Formatos de moneda/fecha, textos de landing, mensajes de error, estados por pantalla, accesibilidad | Especificación y contratos | Sí, obligatorio |
| Paleta, tipografía, geometría, layout, componentes, movimiento | Este documento | Propuesta de diseño |
| Ideas marcadas como *sugerencia* | Este documento | Opcional |

---

## 1. Tema visual y atmósfera

La identidad parte de un material peruano concreto —el **sillar blanco de Arequipa**, la piedra volcánica con la que se tallan fachadas y claustros— y de dos registros de uso radicalmente distintos: una landing que **vende con fotografías** y un panel que **trabaja con datos**. Una misma marca, dos densidades.

**Concepto: “Sillar y tinta”.** Superficies color piedra, texto tinta, acento carmín cochinilla (el rojo profundo del tinte andino, muy lejano al terracota genérico) y un azul noche de cielo arequipeño para navegación y bandas oscuras. La interfaz es **casi plana**: la jerarquía se construye con juntas de 1px y cambios de superficie, no con sombras decorativas. El único momento expresivo es la bienvenida de la landing; todo lo demás calla y deja protagonismo al contenido.

**Registro público (public-web):** editorial, cálido, fotográfico. Espacio en blanco generoso, titulares tallados en serif romana, tarifas siempre con el disclaimer obligatorio. El visitante entiende en 5 segundos qué es (hoteles de demostración en Lima y Arequipa) y qué puede hacer (mirar, contactar; no reservar, no pagar).

**Registro operativo (admin-web):** sereno, denso, utilitario. Tablas legibles, cifras alineadas con cifras tabulares, estados siempre visibles como etiquetas con punto + texto. Un recepcionista con prisa debe ver “activa / vencida / cierre en curso” sin leer párrafos.

**Características clave:**

- Fotografía primero en la landing; datos primero en el panel.
- Un solo acento de marca (carmín) reservado a acciones y enlaces; los colores funcionales (verde, ámbar, rojo, azul) nunca decoran.
- Estado explícito, jamás optimista: un `202` se muestra como “pendiente”, no como éxito (obligatorio).
- Lenguaje humano y exacto, en español de Perú, con las etiquetas que fija la especificación (“Monto recibido ahora”, “estadía vencida sin cierre”).
- Accesible por defecto: contraste AA, foco visible, teclado, `alt` y etiquetas reales (obligatorio).

**Lo que este sistema evita deliberadamente:** el combo crema + serif + terracota, el lujo “navy & gold” de plantilla hotelera, el kit SaaS de tarjetas idénticas con la misma sombra suave, y los textos en mayúsculas espaciadas como decoración. Las mayúsculas no se usan para etiquetas; las etiquetas van en minúscula de oración.

---

## 2. Paleta y roles

### Base neutra — “piedra”

- **Sillar blanco** (#F8F7F4) — Fondo global de ambas apps. Blanco mineral, apenas frío; lienzo silencioso.
- **Sillar piedra** (#EFEDE6) — Superficies secundarias: cabeceras de tabla, bloques de resumen, filas alternas, tarjetas de la landing.
- **Junta de sillar** (#DCD8CC) — Bordes de 1px, divisores, contornos de tarjetas y tablas. La estructura visible del sistema.
- **Gris garúa** (#6E6A61) — Texto secundario, metadatos, placeholders. Contraste ≥ 4.5:1 sobre Sillar blanco.
- **Tinta de sillar** (#26231F) — Texto principal y titulares del panel. Casi negro con calidez mineral.

### Marca

- **Carmín cochinilla** (#8A1E33) — Color de marca y acción primaria: botones primarios, enlaces, indicador de foco, elemento activo de navegación, wordmark. Contraste 8.6:1 sobre Sillar blanco.
  - Hover **#701829**, pulsado **#5C1421**, tinte de selección **#F7E9EC**.
  - Regla dura: el carmín **nunca** se usa para errores, rechazos ni bajas. Eso es bermellón.
- **Azul noche arequipeña** (#202B3A) — Superficies oscuras de navegación (sidebar del panel), bandas oscuras y scrim sobre fotografías del hero (rgba(32,43,58,0.55)). Texto claro sobre este fondo: #F1F0EC.

### Funcionales — solo estado, nunca decoración

| Token | Hex | Fondo tintado | Uso |
|---|---|---|---|
| Verde confirmado | #1D7A4F | #E7F4EC | PAGADO, CONFIRMADA, COMPLETADA, LISTA, guardado correcto |
| Ámbar pendiente | #8F6400 | #FBF3E0 | PENDIENTE, atención, bajo stock, diferencias de monto |
| Bermellón alerta | #B3261E | #FCEBE9 | Error, RECHAZADA, estadía vencida, acciones destructivas |
| Azul proceso | #2361A8 | #E8F0FA | EN_LIMPIEZA, operación en curso, información neutral |
| Gris archivado | #5B5B5B | #ECECEA | CERRADA, CANCELADA, inactivo, sin datos |

Todos los tintados funcionales son solo relleno de etiquetas y avisos; el texto que contienen usa el color intenso correspondiente (contraste verificado ≥ 4.5:1).

**Nunca el color como único portador de significado** (obligatorio): toda etiqueta de estado lleva punto de color **+ texto**; en tablas densas, además, el valor en una columna de texto.

### Proyección y avisos de datos

- Aviso de degradación de reportes: fondo Ámbar pendiente tintado (#FBF3E0), borde izquierdo 3px #8F6400, texto Tinta; mensaje “datos eventualmente consistentes”.
- Proyección desconocida (habitación `DESCONOCIDA`): etiqueta gris “Sin datos”; **jamás** se traduce a “disponible”.

---

## 3. Tipografía

### Familias

- **Marcellus** (`--font-display`) — Serif romana de inspiración epigráfica: la letra “tallada en piedra” de la marca. Solo en display: titulares H1/H2 de la landing, wordmark y cifras editoriales grandes. Una sola grada (400); la jerarquía se logra con tamaño, no con peso. Fallback: `'Times New Roman', serif`.
- **IBM Plex Sans** (`--font-ui`) — La voz de trabajo, en ambas apps: cuerpo, formularios, tablas, botones, navegación. Pesos 400 (cuerpo), 500 (énfasis y botones), 600 (títulos de sección en panel). Humanista, precisa y con excelente comportamiento en densidades altas.
- **IBM Plex Mono** (`--font-mono`) — Solo identificadores técnicos: `correlationId`, `blockId`, `operationId`, UUID, SKU, slug, códigos de error. Nada de “etiquetas mono” decorativas: si no es un identificador, no va en mono.

No se usa una cuarta familia. Sin texto justificado en ninguna pantalla.

### Escala

| Token | Tamaño / interlínea | Uso |
|---|---|---|
| `--text-display` | clamp(2.5rem, 4vw + 1rem, 3.75rem) / 1.08 | H1 landing (Marcellus) |
| `--text-h1` | 2.25rem / 1.15 | Titular de página en panel (Plex 600) — 1.75rem en ≤768px |
| `--text-h2` | 1.75rem / 1.2 | Sección landing (Marcellus) / título de módulo (Plex 600) |
| `--text-h3` | 1.25rem / 1.3 | Subtítulos, nombre de sede en tarjeta (Plex 600) |
| `--text-body` | 1rem / 1.6 | Párrafos de landing y textos largos (Plex 400) |
| `--text-ui` | 0.875rem / 1.45 | UI del panel: tablas, formularios, botones, chips |
| `--text-small` | 0.8125rem / 1.4 | Metadatos, ayudas de campo, comentarios bajo campos |
| `--text-micro` | 0.75rem / 1.35 | Contadores de caracteres, notas legales muy breves |

- **Cifras:** `font-variant-numeric: tabular-nums` en toda tabla, importe, contador y fecha del panel. Los importes se alinean a la derecha; los números grandes de KPI pueden usar Plex Sans 600 con tracking `-0.01em`.
- **Ancho de línea:** ≤ 75 caracteres en párrafos; la landing lo cumple con contenedor estrecho.
- **Minúscula de oración siempre:** “Guardar cambios”, “Registrar check-in”, “Monto recibido ahora”. Titulares jamás en MAYÚSCULAS; ningún “eyebrow” en versalitas decorativo.
- Los `alt`, `aria-label` y textos de botón siguen las mismas reglas de idioma y puntuación (es-PE, con tildes correctas).

---

## 4. Componentes

Geometría base: **bordes apenas redondeados**. Controles con radio 4px (“esquina cincelada, no juguete”), tarjetas y diálogos 8px, etiquetas de estado en forma de píldora. Sombras casi inexistentes: solo emergen capas flotantes (menús, diálogos).

### Botones

- **Primario:** fondo Carmín cochinilla (#8A1E33), texto blanco, radio 4px, padding 10px/16px (panel) y 12px/20px (landing). Hover #701829; pulsado #5C1421; deshabilitado 40% de opacidad con cursor `not-allowed`.
- **Secundario:** fondo transparente, borde 1px Junta de sillar, texto Tinta; hover con superficie Sillar piedra.
- **Terciario / enlace:** sin caja, texto Carmín con subrayado al hover.
- **Destructivo:** solo en flujos irreversibles (dar de baja, anular venta, anular check-in). Fondo Bermellón #B3261E o borde bermellón según jerarquía; **siempre** abre diálogo de confirmación con campo de motivo cuando el contrato lo exige.
- **Estado de envío:** al enviar, el botón se bloquea con un spinner de 16px y el texto permanece (“Registrando…”); doble clic nunca produce doble envío (obligatorio).
- Etiqueta = verbo exacto de la acción; el mismo verbo se usa en el toast y en el título de la pantalla destino.

### Enlaces y foco

- Enlaces en Carmín subrayados al hover/focus. Foco visible global: `outline: 2px solid #8A1E33; outline-offset: 2px` (sobre fondo oscuro: #D7A3AE). Nunca `outline: none` sin reemplazo.

### Formularios

- Etiqueta **visible** encima del campo (minúscula de oración), requeridos marcados con asterisco + `aria-required`. El placeholder no sustituye a la etiqueta (obligatorio).
- Campo: borde 1px Junta de sillar, fondo blanco, radio 4px, alto 40px (panel) / 48px (landing), padding 10px/12px.
- Foco: borde Carmín + anillo `0 0 0 3px #F7E9EC`.
- Error de campo: borde y anillo Bermellón, mensaje bajo el campo en `--text-small` con el texto exacto del catálogo (`errors.md`), campo además referenciado en `aria-describedby`.
- Ayuda: bajo el campo en Gris garúa; contadores para límites (“0/300”), incluido el contador en vivo de bytes UTF-8 de la clave (12–72).
- Selects y datepickers: mismos estados; el datepicker muestra y acepta `dd/MM/yyyy` (locale es-PE).
- Formulario en una columna hasta 720px de ancho; en pantallas anchas, dos columnas solo para campos independientes cortos (nunca partiendo la lectura de una misma fila lógica).

### Tablas de datos (corazón del panel)

- Cabecera con fondo Sillar piedra, texto Plex 600 `--text-ui`, sin mayúsculas; filas separadas por hairline Junta de sillar (sin cebrado).
- Fila estándar 44–48px de alto; hover con fondo Sillar piedra; fila activa con barra izquierda Carmín de 3px.
- Columnas numéricas y monetarias a la derecha y con cifras tabulares; fechas `dd/MM/yyyy`; estados como píldora.
- Acciones de fila: iconos de 20px (editar, ver, acciones contextuales) con `aria-label` descriptivo; lo destructivo vive en el menú contextual, no como icono suelto.
- Paginación: “Anterior / Siguiente” + “Página X de Y · N registros” desde `meta.page/size/totalElements`; tamaño de página 20 por defecto, máximo 100 (contrato `L`).
- Cabecera de tabla pegajosa bajo el topbar; en móvil, las tablas operativas hacen scroll horizontal contenido en su tarjeta (no se recomponen en tarjetas ilegibles salvo listas simples de la landing).

### Etiquetas de estado (píldoras)

Forma: píldora, 11px/6px de padding, `--text-micro` en 500, fondo tintado, punto de 6px del color intenso + texto.

| Ámbito | Valor | Etiqueta UI | Color |
|---|---|---|---|
| Venta `estado` | PENDIENTE / PAGADO | “Pago pendiente” / “Pagado” | Ámbar / Verde |
| Venta `estadoOperacion` | PENDIENTE / CONFIRMADA / RECHAZADA / CANCELACION_PENDIENTE / CANCELADA | “En proceso” / “Confirmada” / “Rechazada” / “Anulación en proceso” / “Anulada” | Azul con spinner / Verde / Bermellón / Ámbar / Gris |
| Estadía | ACTIVA / CERRANDO / CERRADA | “Activa” / “Cierre en curso” / “Cerrada” | Verde / Ámbar / Gris |
| Estadía vencida | `vencida = true` | “Estadía vencida sin cierre” | Bermellón (fila/tarjeta con barra roja; la habitación sigue ocupada) |
| Cierre | VALIDANDO / CONFIRMANDO / COMPLETADO / RECHAZADO | “Validando” / “Confirmando cobro” / “Completado” / “Rechazado” | Azul / Ámbar / Verde / Bermellón |
| Limpieza | PENDIENTE / COMPLETADA | “Pendiente” / “Completada” | Ámbar / Verde |
| Habitación física | LISTA / EN_LIMPIEZA / MANTENIMIENTO | “Lista” / “En limpieza” / “Mantenimiento” | Verde / Azul / Ámbar |
| Ocupación (proyección) | LIBRE / OCUPADA / DESCONOCIDA | “Libre” / “Ocupada” / “Sin datos” | Gris tinta / Tinta / Gris claro — nunca “Disponible” |
| Producto | bajo stock (`cantidad ≤ umbralBajo`) | “Bajo stock” | Ámbar |

**Obligatorio:** `estado` (pago) y `estadoOperacion` (técnico) se muestran **por separado**, una píldora por columna (“Pago” y “Operación”), nunca fusionadas en un solo texto.

### Tarjetas

- **Landing — ficha de sede:** imagen full-bleed 3:2 arriba (esquinas superiores 8px), contenido con padding 24px: ciudad en Gris garúa (`--text-small`), nombre en Plex Sans 600 (`--text-h3`), descripción breve y enlace “Ver sede” en Carmín. Borde 1px Junta; sin sombra en reposo.
- **Landing — ficha de categoría:** imagen 4:3, nombre, capacidad (“Hasta 2 personas”), detalle y **tarifa** con el texto obligatorio (ver §8). El precio se presenta como dato sobrio, nunca como oferta agresiva.
- **Panel — tarjeta de KPI:** número grande (Plex 600, `--text-h1`), etiqueta corta debajo, delta opcional en `--text-small`. Sin gradientes ni ilustraciones.
- **Panel — tarjeta de resumen** (cotización, cierre): filas etiqueta-valor separadas por hairline, valores tabulares a la derecha; la fila de **penalidad** se resalta con fondo #FCEBE9, borde bermellón y su motivo visible.

### Navegación

- **Landing:** header claro sobre Sillar blanco con borde inferior Junta; wordmark “Hotel” + etiqueta “Demostración”; enlaces Sedes / Contacto / Privacidad; acceso privado como enlace sobrio (“Acceso al panel”). Sticky con fondo translúcido (blur 8px) al hacer scroll. Pie: privacidad, contacto demo y aviso de demostración.
- **Panel:** shell de dos zonas — **sidebar** oscura Azul noche (248px expandida, 64px colapsada, con tooltip al colapsar) y contenido sobre Sillar blanco. Ítem activo: fondo rgba claro + barra Carmín izquierda. Secciones visibles según rol (§ matriz de menú en `08-admin-web.md`); el área de cliente (`/mi-estadia`, catálogo, carrito, mis ventas) usa un shell propio, **sin** menú administrativo.
- **Topbar:** 56px, borde inferior Junta: selector de sede (filtro de trabajo, no seguridad), nombre de la persona, avatar/iniciales, menú de cuenta (Mi perfil, Cerrar sesión). En demo, etiqueta discreta “Demo”.
- Breadcrumbs solo en pantallas de detalle (`Recepción / Estadía 1001 / Cierre`); en el resto, el ítem activo del sidebar basta.

### Diálogos

- Radio 8px, ancho 480–560px, título `--text-h3`, cuerpo claro, acciones a la derecha (secundaria “Cancelar” + primaria o destructiva).
- Confirmaciones destructivas incluyen **motivo obligatorio** (1..300) cuando el contrato lo exige (anular check-in, anular venta, ajuste, mantenimiento) y explican la consecuencia en una frase: “La venta quedará anulada y el stock volverá a la sede.”
- Foco atrapado dentro del diálogo; `Esc` cierra salvo envío en curso; al cerrar, el foco vuelve al disparador.

### Avisos, toasts y banners

- **Toast de éxito** (200/201): abajo a la derecha, verde, pasado simple exacto: “Cambios guardados”, “Limpieza completada”, “Venta registrada”. Autocierre 5s.
- **Banner de pendiente (`202`)** — obligatorio y nunca como éxito: franja persistente arriba del contenido con spinner y texto “Operación en proceso. No repitas la acción.” + detalle del caso (“Cierre pendiente, no volver a cobrar”). Permanece hasta que el polling a `Location` confirme; muestra “Actualizado dd/MM/yyyy HH:mm” con cada reintento (`Retry-After`).
- **Banner de degradación** (reportes): Ámbar, “Datos eventualmente consistentes” cuando `meta.degraded = true` o `consistency: EVENTUAL`; no bloquea la vista.
- **Errores:** franja Bermellón con el mensaje del catálogo, acción esperada como botón (“Reintentar”, “Recargar”, “Iniciar sesión de nuevo”) y `correlationId` visible en mono con botón “Copiar”. Ver §7.
- **Sin conexión / red caída:** mensaje + botón Reintentar; en la landing, jamás inventar precios ni mostrar datos del panel como respaldo (obligatorio).

### Carga, vacío y error (patrón por pantalla)

- **Carga:** skeletons con la forma exacta del contenido (KPI cards, filas de tabla, tarjetas) sobre Sillar piedra con pulso suave de opacidad; nunca spinners de página completa.
- **Vacío:** frase exacta de `08-admin-web.md` + acción cuando exista (“Aún no hay registros” + botón Crear para ADMIN). Los vacíos de listas no llevan ilustraciones grandes; sí una línea de ayuda.
- **Error:** mensaje del catálogo, campos en `error.fields` resaltados, `correlationId` copiable, y la acción esperada (reintentar con la misma clave, recargar con ETag, esperar 202, contactar soporte). Prohibido redactar mensajes propios (obligatorio).
- **Sesión expirada (401):** pantalla/modal “Tu sesión expiró. Vuelve a iniciar sesión.” con botón al login; al volver, no repetir la acción automáticamente (el usuario decide).

### Piezas específicas del dominio

- **Selector de cliente (check-in):** buscador por documento exacto con estado de resultados; “Cliente no encontrado: créalo primero” con enlace directo al alta de cliente (solo A/E).
- **Selector de habitación (check-in):** tarjetas o filas con número, categoría, precio y estado físico + ocupación; solo se listan las habitaciones del catálogo de la sede. Si la admisión falla, el sistema rechaza con el mensaje del catálogo (`ROOM_OCCUPIED`, `ROOM_NOT_READY`) y muestra el error junto al campo `idHabitacion`.
- **Desglose de cotización/cierre:** filas → Alojamiento (noches × tarifa) / Adelanto registrado (−) / Penalidad (+ destacada con motivo) / Consumos pendientes (+) / **Cobro ahora**. El campo de importe se rotula **“Monto recibido ahora”** (obligatorio) y se compara exacto contra el cálculo del servidor; el front **no recalcula** (obligatorio). El total histórico se rotula **“Total histórico del alojamiento”** y va separado de los consumos. Ante `AMOUNT_MISMATCH`, se muestra la nueva cotización recibida, no un cálculo propio.
- **Carrito (área C):** líneas con producto, cantidad (stepper 1..9999), precio y quitar; cambio de cantidad = eliminar + agregar (contrato). Al vender, errores `PRODUCT_INACTIVE` / `STOCK_INSUFFICIENT` se pintan en la línea afectada.
- **Bajo stock:** valor en ámbar con píldora “Bajo stock”; en el listado de productos basta con eso, sin alarmas rojas.
- **Galería de imágenes:** relación fija con `object-fit: cover`, reserva de espacio (sin CLS), `alt` obligatorio (≤150) y fallback neutro si la imagen falla (nunca rompe layout ni tarifa — obligatorio).
- **Gráficos de reportes:** una sola familia de color categórica (Carmín, Azul noche, Verde confirmado, Ámbar pendiente, Azul proceso, Gris garúa en ese orden); leyenda explícita, etiquetas con `S/` y fechas es-PE. Sin donuts arcoíris ni decoración 3D.
- **Exportar:** PDF/Excel se generan en frontend (no hay endpoints de archivos). El PDF reutiliza la tipografía del panel y reglas hairline; se aceptan columnas en escala de grises.

---

## 5. Principios de layout

### Landing (public-web)

- Contenedor máximo **1200px** (páginas de texto como Privacidad: 720px), margen lateral 20/24/32/48px según breakpoint.
- Rejilla de 12 columnas; las tarjetas de sede y categoría ocupan 4/12 (3 por fila) en escritorio, 6/12 en tablet y 12/12 en móvil.
- Ritmo vertical: 48px entre bloques relacionados, 96–128px entre secciones.
- **Hero:** banda fotográfica propia (placeholder `/assets/demo/`) con scrim Azul noche al 55% y titular Marcellus en blanco sobre la foto; debajo, propuesta de valor en una línea y llamada a contacto (obligatorio el contenido de §8). Alineación a la izquierda para lectura editorial.
- Página de sede: cabecera con nombre y ciudad, descripción, dirección demo, luego rejilla de categorías. Página de categoría: galería + detalle + capacidad + tarifa con disclaimer.
- El pie repite el aviso: datos de demostración; contacto demo no clicable (`tel:` solo con datos reales — obligatorio).

### Panel (admin-web)

- Shell: sidebar 248px (colapsable a 64px) + contenido fluido con padding 24px (32px en ≥1280px). Las tablas ocupan todo el ancho; **los formularios limitan a 720px** para no volverse ilegibles.
- Jerarquía de página clásica: título de página (`--text-h1`) → descripción breve opcional → barra de acciones (búsqueda, filtros, botón primario) → contenido. Filtros de sede (`idHotel`) como primer control en las pantallas que lo requieren.
- Rejilla de KPI del dashboard: 4/2/1 columnas; tarjetas de resumen 2/1.
- Espaciado base **4px**: 4/8/12/16/24/32/48/64/96. Espaciado interno de tarjetas 20–24px; padding de celdas de tabla 12px/16px.
- Breakpoints: 480 / 640 / 768 / 1024 / 1280px. El panel es usable en tablet (≥768px) con sidebar colapsada; móvil es prioridad de la landing, no del trabajo operativo.
- Densidades por app: landing respira (alturas 48–56px en controles); panel comprime con dignidad (control 40px, fila 44–48px, chips 22px).

### Escala de capas y radios

- Radios: 4px controles / 8px tarjetas y diálogos / píldora para estados.
- Sombras: reposo **ninguna** (hairline); menús y dropdowns `0 4px 16px rgba(38,35,31,.10)`; diálogos `0 12px 32px rgba(38,35,31,.18)`. Nada más.
- z-index: contenido 0 · sticky header 100 · dropdown 200 · diálogo 300 · toast 400.

---

## 6. Formatos obligatorios de UI (es-PE)

Estos formatos los fija la especificación y se implementan con pipes de Angular y locale registrado (`registerLocaleData`). Nunca se construyen a mano con concatenación de strings.

| Dato | Formato en pantalla | Ejemplo | Nota de implementación |
|---|---|---|---|
| Moneda | `S/` + miles con coma + 2 decimales | `S/ 70.00`, `S/ 1,234.50` | `currency:'PEN':'symbol-narrow'` en es-PE; verificar el carácter exacto `S/` en prueba unitaria |
| Fecha | `dd/MM/yyyy` | `04/10/2026` | `date:'dd/MM/yyyy'` |
| Fecha y hora | `dd/MM/yyyy HH:mm` | `04/10/2026 15:30` | `date:'dd/MM/yyyy HH:mm'` |
| Estado de venta (pago) | Píldora separada | “Pago pendiente” / “Pagado” | Campos `estado` y `estadoOperacion` nunca fusionados |
| Operación técnica | Píldora separada | “Confirmada”, “En proceso”… | `202` ⇒ “En proceso”, jamás “éxito” |
| Importe de cierre | Etiqueta fija | “Monto recibido ahora” | Campo `montoRecibido`; comparación exacta contra `cobroAhora` |
| Total de alojamiento | Etiqueta fija | “Total histórico del alojamiento” | Separado de consumos; no confundir con el cobro de hoy |
| Cantidades y stock | Entero; delta con signo | `+5`, `−3`, `0` | El ajuste nunca se envía con delta 0 |

Reglas duras asociadas:

- El frontend **muestra** importes del backend y **no recalcula** totales de cobro (obligatorio).
- Los porcentajes derivados (p. ej. ocupación) se rotulan con su base y fecha; la ocupación de reportes es a medianoche America/Lima.
- Toda cifra monetaria con `tabular-nums` y alineación a la derecha en contextos tabulares.

---

## 7. Estados, errores y recuperación (interacción)

1. **Éxito:** `200/201` → acción nombrada en pasado + navegación o refresco de datos. El botón vuelve a estado normal.
2. **Pendiente `202`:** banner persistente “En proceso”, polling a `Location` con `Retry-After`, acciones que podrían duplicar la intención deshabilitadas. Al terminar: refresco del recurso y actualización del banner a resultado. “Cierre pendiente, no volver a cobrar” es texto obligatorio en el cierre con `202`.
3. **Validación `400`:** resaltar `error.fields`; si la intención cambió, nueva `Idempotency-Key` (lógica, invisible al usuario); el usuario corrige y reenvía.
4. **Conflicto de negocio `409`:** mensaje del catálogo + acción esperada. Ejemplos: `ROOM_OCCUPIED` → “Elegir otra habitación”; `STOCK_INSUFFICIENT` → “Ajustar cantidades”; `AMOUNT_MISMATCH` → mostrar nueva cotización; `IDEMPOTENCY_CONFLICT` → nueva intención (reiniciar el formulario).
5. **Precondición `412`:** “Otra persona modificó este registro. Recarga para ver los cambios.” con **Recargar** como acción primaria; nunca sobrescribir en silencio. `428`: recargar y reenviar.
6. **Límite `429`:** esperar `Retry-After`; en login, mensaje “Demasiados intentos. Espera un momento.”
7. **Autenticación `401`:** “Tu sesión expiró…” → relogin (JWT en memoria, sin refresh).
8. **Permisos `403` / inexistente `404`:** mensaje genérico del catálogo, sin filtrar existencia de recursos ajenos (anti-enumeración). El guard de Angular solo oculta; el backend decide.
9. **Dependencia `503`:** “El servicio no está disponible. Reintenta en unos segundos.” Reintento con la misma clave; en reportes, `REPORTING_NOT_READY` muestra “Los reportes aún se están preparando.”
10. **Red caída:** estado de error con Reintentar; prohibido inventar precios o mostrar datos del panel como respaldo en la landing.

Todo error visible incluye código (`error.code`), mensaje del catálogo y `correlationId` para soporte. Si el código es desconocido, se muestra `message` y el `correlationId` sin inventar traducciones.

---

## 8. Contenido y voz

**Idioma:** español de Perú, minúscula de oración, verbos en presente para acciones y pasado para confirmaciones. Frases cortas; sin jerga técnica frente al usuario (nada de “payload” ni “endpoint”); sin signos de exclamación.

### Textos obligatorios

- **Hero landing:** “Hoteles de demostración en Lima y Arequipa” + “Catálogo, sedes y contacto; sin reservas ni pagos en línea” + llamada a contacto.
- **Tarifa (siempre en categorías y fichas):** “Desde S/ 70.00 por noche. Consulta disponibilidad con recepción. No constituye una reserva.” (el importe varía con `precioDesde` real; el texto acompañante no).
- **Contacto demo:** `contacto@hotel.test` y teléfono ficticio como **texto no clicable**; `tel:`/WhatsApp solo con datos reales del equipo.
- **Privacidad:** borrador informativo, sin datos reales ni promesas de cumplimiento legal, enlazando a contacto.
- **Demo por todas partes:** las sedes se llaman “Hotel Demo …”; cualquier dato semilla se marca como demostración. La landing no muestra números de habitación, ocupantes, limpieza, disponibilidad ni datos operativos (prohibido).

### Vocabulario controlado (usar siempre estos términos)

| Concepto | Término UI | Evitar |
|---|---|---|
| Ingreso de huésped | “Registrar check-in” / “Check-in” | “Reservar”, “Reserva” |
| Salida con cobro | “Registrar salida” / “Cierre de estadía” | “Check-out pendiente” como estado interno |
| Cobro del cierre | “Monto recibido ahora” | “Total a pagar”, “Monto final” |
| Consumos | “Venta”, “Consumo” (C) | “Pedido” |
| Baja de maestro | “Dar de baja” | “Eliminar” (salvo DELETE técnico) |
| Corrección de check-in | “Anular check-in” | “Cancelar estadía” |
| Habitación sin huésped | “Libre” / “Ocupada” / “Sin datos” (proyección) | “Disponible” (prohibido sugerirlo) |

### Mensajes de estado canónicos (exactos)

- Vacíos: “No hay personas que coincidan”, “No hay tareas pendientes”, “No hay estadías en esta sede”, “Sin movimientos en el rango”, “No hay ventas”, “El catálogo está vacío”, “Tu carrito está vacío”, “Aún no tienes consumos”, “No tienes una estadía activa; consulta en recepción”, “Sin datos para tu sede todavía”.
- Etiquetas destacadas: “Estadía vencida sin cierre” (bermellón, nunca desaparece sola), “Cierre pendiente, no volver a cobrar”, “Datos eventualmente consistentes” (reportes degradados).
- Errores: los de [`contracts/errors.md`](contracts/errors.md), sin reescribir ni parafrasear.

### Tono

Sereno y servicial, nunca efusivo. La landing informa y orienta; el panel acompaña la operación sin dramatizar. Un error explica qué pasó y qué hacer; no se disculpa ni culpa al usuario.

---

## 9. Accesibilidad y movimiento

Obligatorio por especificación (02: “Responsive, navegación por teclado, labels, contraste y alt en imágenes”) y base del sistema:

- **Contraste:** texto AA (≥4.5:1) en todos los combos de la paleta; los pares de este documento ya cumplen. El color nunca es el único indicador.
- **Foco visible** en todo elemento interactivo; orden de tabulación natural; diálogos con foco atrapado y retorno al disparador.
- **Formularios:** etiqueta visible, ayuda vinculada (`aria-describedby`), mensaje de error anunciado (`role="alert"`), requeridos con `aria-required`.
- **Imágenes:** `alt` obligatorio y descriptivo (“Habitación de demostración”), decorativas con `alt=""`.
- **Idioma:** `lang="es-PE"` en el documento; textos, fechas y moneda en es-PE.
- **Tablas:** `<th scope>` correcto, sin anidaciones profundas; scroll horizontal anunciado con foco.
- **Objetivos táctiles:** ≥44px en la landing; ≥40px en controles del panel.
- **Polling y estados vivos:** cambios de estado se anuncian con `aria-live="polite"` sin interrumpir; los spinners tienen texto alternativo del contexto (“Confirmando cobro”).
- **Movimiento:** un único gesto de entrada orquestado en la landing (fade + 8px de subida, 400ms, una vez por carga de página). Hover/focus 150ms; diálogos 200ms. Nada de parallax, scroll-jacking ni animaciones en bucle. `prefers-reduced-motion: reduce` desactiva las entradas y deja solo cambios de opacidad instantáneos.
- **Exportaciones y PDF:** tablas exportadas con encabezados legibles en escala de grises (no dependen del color).

---

## 10. Tokens de diseño (base para implementar)

Sugerencia de tokens CSS compartidos; viven en `src/styles.css` de cada app bajo `:root`. Las apps comparten la base; el panel prioriza densidades y estados, la landing prioriza display y fotografía.

```css
:root {
  /* Piedra */
  --color-sillar-blanco: #F8F7F4;
  --color-sillar-piedra: #EFEDE6;
  --color-junta: #DCD8CC;
  --color-garua: #6E6A61;
  --color-tinta: #26231F;

  /* Marca */
  --color-cochinilla: #8A1E33;
  --color-cochinilla-hover: #701829;
  --color-cochinilla-pressed: #5C1421;
  --color-cochinilla-wash: #F7E9EC;
  --color-azul-noche: #202B3A;
  --color-azul-noche-scrim: rgba(32, 43, 58, 0.55);

  /* Funcionales */
  --color-ok: #1D7A4F;        --color-ok-bg: #E7F4EC;
  --color-atencion: #8F6400;  --color-atencion-bg: #FBF3E0;
  --color-error: #B3261E;     --color-error-bg: #FCEBE9;
  --color-proceso: #2361A8;   --color-proceso-bg: #E8F0FA;
  --color-archivado: #5B5B5B; --color-archivado-bg: #ECECEA;
  --color-foco-oscuro: #D7A3AE;

  /* Tipografía */
  --font-display: 'Marcellus', 'Times New Roman', serif;
  --font-ui: 'IBM Plex Sans', system-ui, sans-serif;
  --font-mono: 'IBM Plex Mono', ui-monospace, monospace;
  --text-display: clamp(2.5rem, 4vw + 1rem, 3.75rem);
  --text-h1: 2.25rem;
  --text-h2: 1.75rem;
  --text-h3: 1.25rem;
  --text-body: 1rem;
  --text-ui: 0.875rem;
  --text-small: 0.8125rem;
  --text-micro: 0.75rem;

  /* Espaciado (base 4px) */
  --space-1: 0.25rem; --space-2: 0.5rem;  --space-3: 0.75rem;
  --space-4: 1rem;    --space-6: 1.5rem;  --space-8: 2rem;
  --space-12: 3rem;  --space-16: 4rem;   --space-24: 6rem;

  /* Forma */
  --radius-control: 4px;
  --radius-card: 8px;
  --radius-pill: 999px;
  --shadow-pop: 0 4px 16px rgba(38, 35, 31, 0.10);
  --shadow-dialog: 0 12px 32px rgba(38, 35, 31, 0.18);

  /* Movimiento */
  --dur-fast: 150ms;
  --dur-base: 200ms;
  --dur-hero: 400ms;
  --ease-out: cubic-bezier(0.2, 0.6, 0.2, 1);

  /* Estructura */
  --container-landing: 1200px;
  --sidebar-w: 248px;
  --sidebar-w-collapsed: 64px;
  --topbar-h: 56px;
}
```

Recomendaciones de implementación:

- **Fuentes:** Marcellus, IBM Plex Sans (400/500/600) e IBM Plex Mono (400/500) vía self-hosting o Google Fonts con `font-display: swap`; declarar `size-adjust`/fallbacks para evitar CLS.
- **CSS:** custom properties + componentes Angular standalone; sin dependencia obligatoria de una librería visual. Si se usa Angular Material/otra, re-mapear sus tokens a esta paleta en lugar de dejar los defaults.
- **Pruebas visuales:** snapshot de la barra de estados y del desglose de cobro en ambos breakpoints críticos (1024 y 390px); prueba del formato exacto `S/ 1,234.50` y `dd/MM/yyyy HH:mm`.

---

## 11. Lenguaje para pedir pantallas nuevas (Stitch / diseño)

Cuando se generen pantallas nuevas con Stitch o se describan a otra persona del equipo, usar este vocabulario descriptivo con los hex de §2:

- **Atmósfera:** “editorial y mineral, cálida pero contenida”, “panel operativo sereno con datos densos y espacio disciplinado”.
- **Fondos:** “fondo color piedra clara (#F8F7F4)”, “superficies de piedra (#EFEDE6) separadas por juntas de 1px (#DCD8CC)”.
- **Acciones:** “botón primario carmín cochinilla (#8A1E33), esquinas apenas redondeadas de 4px, texto blanco”.
- **Navegación del panel:** “barra lateral azul noche (#202B3A), ítem activo con barra carmín y texto claro”.
- **Estados:** “píldora con punto de color y texto, nunca solo color”: verde confirmado (#1D7A4F), ámbar pendiente (#8F6400), bermellón alerta (#B3261E), azul proceso (#2361A8), gris archivado (#5B5B5B).
- **Tarjetas de sede:** “imagen full-bleed 3:2, borde hairline, sin sombra en reposo”.
- **Tipografía:** “titulares en serif epigráfica Marcellus, cuerpo y UI en IBM Plex Sans, identificadores en IBM Plex Mono”.
- **Sombras:** “casi plana: sin sombra en reposo; solo menús y diálogos flotan con la sombra suave del sistema”.

### Checklist antes de dar por lista una pantalla (cualquier app)

1. Rol correcto visible: menú y acciones según la matriz A/E/C; el área de cliente no muestra nada administrativo.
2. Formatos es-PE: `S/ 1,234.50`, `dd/MM/yyyy`, `dd/MM/yyyy HH:mm`.
3. Estados: vacío, cargando (skeleton), error (mensaje del catálogo + acción + `correlationId`).
4. `202` mostrado como pendiente con polling; ningún “éxito” optimista.
5. Acciones destructivas con motivo y confirmación; jamás “Eliminar” donde corresponde “Dar de baja”.
6. Sin textos prohibidos: “reservar”, “disponible ahora”, disponibilidad garantizada; en la landing, sin datos operativos.
7. Accesibilidad: teclado, foco visible, etiquetas, contraste, `alt`.
8. Números tabulares, importes a la derecha, importes no recalculados.
9. Copys en minúscula de oración y terminología del vocabulario controlado (§8).
10. Prueba en 390px (landing/C) y 1024px (panel); `prefers-reduced-motion` respetado.

---

## 12. Alcance y evolución

- **Fuera de alcance (MVP):** tema oscuro completo (el azul noche es solo superficie de navegación), login social (retirado), cualquier flujo de reserva o pasarela de pago.
- **Sugerencias opcionales:** aviso suave de expiración de sesión basado en `expiresIn` (15 min, sin refresh); indicador global “entorno de demostración” en el topbar; selector de sede persistente por usuario en el panel.
- **Gobierno:** cualquier patrón nuevo que contradiga estas reglas se discute primero y se agrega aquí; los mensajes de UI se actualizan solo en [`contracts/errors.md`](contracts/errors.md) y este documento enlaza, nunca duplica.
- **Trazabilidad:** este documento es derivado; ante conflicto, mandan `especificacion/02-landing-y-panel.md`, `especificacion/08-admin-web.md` y `contracts/errors.md`.
