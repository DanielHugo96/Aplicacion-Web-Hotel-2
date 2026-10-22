---
name: Cadena hotelera — admin-web
description: Panel operativo de una cadena hotelera; densidad disciplinada, estados explícitos y cifras alineadas para trabajar con prisa.
colors:
  sillar-blanco: "#F8F7F4"
  sillar-piedra: "#EFEDE6"
  junta: "#DCD8CC"
  garua: "#6E6A61"
  tinta: "#26231F"
  cochinilla: "#8A1E33"
  cochinilla-hover: "#701829"
  cochinilla-pressed: "#5C1421"
  cochinilla-wash: "#F7E9EC"
  azul-noche: "#202B3A"
  scrim: "rgba(32, 43, 58, 0.55)"
  texto-oscuro: "#F1F0EC"
  foco-oscuro: "#D7A3AE"
  ok: "#1D7A4F"
  ok-bg: "#E7F4EC"
  atencion: "#8F6400"
  atencion-bg: "#FBF3E0"
  error: "#B3261E"
  error-bg: "#FCEBE9"
  proceso: "#2361A8"
  proceso-bg: "#E8F0FA"
  archivado: "#5B5B5B"
  archivado-bg: "#ECECEA"
typography:
  headline:
    fontFamily: "'IBM Plex Sans', system-ui, sans-serif"
    fontSize: "2.25rem"
    fontWeight: 600
    lineHeight: 1.15
  section:
    fontFamily: "'IBM Plex Sans', system-ui, sans-serif"
    fontSize: "1.75rem"
    fontWeight: 600
    lineHeight: 1.2
  title:
    fontFamily: "'IBM Plex Sans', system-ui, sans-serif"
    fontSize: "1.25rem"
    fontWeight: 600
    lineHeight: 1.3
  body:
    fontFamily: "'IBM Plex Sans', system-ui, sans-serif"
    fontSize: "1rem"
    fontWeight: 400
    lineHeight: 1.6
  ui:
    fontFamily: "'IBM Plex Sans', system-ui, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 400
    lineHeight: 1.45
  small:
    fontFamily: "'IBM Plex Sans', system-ui, sans-serif"
    fontSize: "0.8125rem"
    fontWeight: 400
    lineHeight: 1.4
  micro:
    fontFamily: "'IBM Plex Sans', system-ui, sans-serif"
    fontSize: "0.75rem"
    fontWeight: 500
    lineHeight: 1.35
  mono:
    fontFamily: "'IBM Plex Mono', ui-monospace, monospace"
    fontSize: "0.75rem"
    fontWeight: 400
    lineHeight: 1.35
  wordmark:
    fontFamily: "Marcellus, 'Times New Roman', serif"
    fontSize: "1.25rem"
    fontWeight: 400
    lineHeight: 1.2
rounded:
  control: "4px"
  card: "8px"
  pill: "999px"
spacing:
  s1: "0.25rem"
  s2: "0.5rem"
  s3: "0.75rem"
  s4: "1rem"
  s6: "1.5rem"
  s8: "2rem"
  s12: "3rem"
  s16: "4rem"
  s24: "6rem"
components:
  boton-primario:
    backgroundColor: "{colors.cochinilla}"
    textColor: "#FFFFFF"
    typography: "{typography.ui}"
    rounded: "{rounded.control}"
    padding: "10px 16px"
    height: "40px"
  boton-secundario:
    backgroundColor: "transparent"
    textColor: "{colors.tinta}"
    typography: "{typography.ui}"
    rounded: "{rounded.control}"
    padding: "10px 16px"
    height: "40px"
  boton-destructivo:
    backgroundColor: "{colors.error}"
    textColor: "#FFFFFF"
    typography: "{typography.ui}"
    rounded: "{rounded.control}"
    padding: "10px 16px"
    height: "40px"
  campo:
    backgroundColor: "#FFFFFF"
    textColor: "{colors.tinta}"
    typography: "{typography.ui}"
    rounded: "{rounded.control}"
    padding: "10px 12px"
    height: "40px"
  tabla-cabecera:
    backgroundColor: "{colors.sillar-piedra}"
    textColor: "{colors.tinta}"
    typography: "{typography.ui}"
    padding: "12px 16px"
  pildora-ok:
    backgroundColor: "{colors.ok-bg}"
    textColor: "{colors.ok}"
    typography: "{typography.micro}"
    rounded: "{rounded.pill}"
    padding: "3px 10px"
  pildora-atencion:
    backgroundColor: "{colors.atencion-bg}"
    textColor: "{colors.atencion}"
    typography: "{typography.micro}"
    rounded: "{rounded.pill}"
    padding: "3px 10px"
  pildora-error:
    backgroundColor: "{colors.error-bg}"
    textColor: "{colors.error}"
    typography: "{typography.micro}"
    rounded: "{rounded.pill}"
    padding: "3px 10px"
---

# Sistema de diseño: `admin-web`

**Ámbito:** `apps/admin-web`. Panel administrativo (ADMIN, EMPLEADO) y área de cliente (CLIENTE).
**Versión:** 2.0 · 21/10/2026 · sustituye a la parte de panel de `docs/DESIGN.md` 1.0.
**Compañero:** [`DESIGN-public-web.md`](DESIGN-public-web.md) comparte exactamente los mismos tokens. Solo cambian densidad, layout, componentes y voz.

Este documento es la autoridad visual del panel. **No manda sobre la especificación.** Ante conflicto, el orden es:

| Prioridad | Fuente | Qué manda |
|---|---|---|
| 1 | [`especificacion/08-admin-web.md`](especificacion/08-admin-web.md) | Rutas, campos, validaciones, mensajes y estados por pantalla |
| 2 | [`contracts/errors.md`](contracts/errors.md) y `contracts/openapi/admin-web.yaml` | Catálogo de errores y datos disponibles |
| 3 | Este documento | Paleta, tipografía, layout, componentes, movimiento, jerarquía |
| 4 | `apps/admin-web/src/styles.css` | Implementación actual; si contradice a este documento, se corrige el código |

Reglas de negocio que el diseño **nunca** puede contradecir: un `202` se muestra como pendiente y jamás como éxito; el front no recalcula importes; el color nunca es el único portador de significado; el mensaje de error sale del catálogo, nunca se redacta uno propio; los guards ocultan, el backend decide.

Los encabezados de sección van en inglés porque así los exige el formato `DESIGN.md` que consumen las herramientas de diseño; el contenido va en español.

---

## Overview

**Creative North Star: "La mesa de recepción".**

Si la landing es el claustro, el panel es la mesa de recepción: una superficie de trabajo estrecha, ordenada y siempre a la vista, donde cada objeto tiene su sitio porque alguien va a tener que encontrarlo con prisa y con un huésped delante. Esa es la escena que gobierna el diseño. No hay nada decorativo en la mesa; todo lo que está, está porque se usa.

El registro es **operativo**: sereno, denso, utilitario. Un recepcionista debe ver "activa", "vencida", "cierre en curso" sin leer un párrafo, encontrar el botón de cobrar sin buscarlo y saber que una operación sigue en proceso sin volver a pulsarla. La densidad no es una estética: es respeto por el tiempo de quien trabaja aquí. Cada píxel de aire tiene que ganarse su sitio, y cada píxel de compresión también.

La misma paleta de piedra y el mismo carmín de la landing sostienen el panel; lo que cambia es la proporción. Aquí el azul noche deja de ser una banda y se convierte en el muro lateral del sistema —la barra de navegación— y el carmín se vuelve escaso: aparece en la acción primaria, en el ítem activo y en el foco, nunca más de una vez por bloque.

**Key Characteristics:**

- Datos primero: tablas, cifras y estados. La decoración no existe.
- Densidad disciplinada: control de 40px, fila de 44–48px, celda de 12/16px.
- Estado explícito y separado: pago y operación nunca se fusionan.
- Carmín escaso; funcionales reservados a estado real.
- Cifras tabulares y alineadas a la derecha en todo contexto numérico.
- Cero sombras en reposo; solo flotan menús y diálogos.

**Lo que este sistema evita deliberadamente:** el kit SaaS de tarjetas idénticas con sombra suave y degradado; las cifras gigantes sin jerarquía; los iconos emoji o glifos Unicode haciendo de icono; el texto en mayúsculas espaciadas como etiqueta; los gráficos de dona multicolor y el 3D; los estados representados solo por color.

---

## Colors

La paleta es la misma de la landing, con los funcionales en primer plano porque aquí informan de operaciones reales.

### Primary

- **Carmín cochinilla** (`#8A1E33`): la acción primaria, el ítem activo de navegación, el foco y el wordmark. Contraste ≈ 8.5:1 sobre Sillar blanco. Hover `#701829`, pulsado `#5C1421`, tinte `#F7E9EC`.
  - Regla dura: el carmín **no** representa error, rechazo ni baja. Eso es bermellón.

### Secondary

- **Azul noche arequipeña** (`#202B3A`): el muro de navegación (sidebar), el scrim de los diálogos (`rgba(32, 43, 58, 0.55)`) y las superficies oscuras puntuales. Texto claro sobre este fondo: `#F1F0EC`.

### Neutral

- **Sillar blanco** (`#F8F7F4`): fondo del área de contenido.
- **Sillar piedra** (`#EFEDE6`): cabeceras de tabla, bloques de resumen, filas alternas cuando hagan falta, fondo de hover de fila.
- **Junta de sillar** (`#DCD8CC`): bordes de 1px de campos, tablas, tarjetas y divisores.
- **Gris garúa** (`#6E6A61`): texto secundario, ayudas, metadatos, sub de página.
- **Tinta de sillar** (`#26231F`): texto principal y titulares.

### Funcionales — solo estado, nunca decoración

| Token | Hex | Fondo tintado | Significado |
|---|---|---|---|
| Verde confirmado | `#1D7A4F` | `#E7F4EC` | PAGADO, CONFIRMADA, COMPLETADA, LISTA, guardado correcto |
| Ámbar pendiente | `#8F6400` | `#FBF3E0` | PENDIENTE, atención, bajo stock, diferencias de monto |
| Bermellón alerta | `#B3261E` | `#FCEBE9` | Error, RECHAZADA, estadía vencida, acción destructiva |
| Azul proceso | `#2361A8` | `#E8F0FA` | EN_LIMPIEZA, operación en curso, información neutral |
| Gris archivado | `#5B5B5B` | `#ECECEA` | CERRADA, CANCELADA, inactivo, sin datos |

Todos los tintados son relleno de etiquetas y avisos; el texto que contienen usa el color intenso correspondiente (contraste verificado ≥ 4.5:1).

### Named Rules

**The Dot-And-Text Rule.** Toda etiqueta de estado lleva punto de color **más** texto. El color nunca es el único portador de significado; en tablas densas, además, el valor aparece en una columna de texto.

**The One Accent Rule.** Un solo elemento carmín por bloque. Si dos acciones compiten, una es secundaria.

**The No-Invented-State Rule.** Un `202` es "En proceso", nunca "Listo". Una proyección `DESCONOCIDA` es "Sin datos", nunca "Libre". El estado se traduce del contrato, no se interpreta.

---

## Typography

**Display Font:** Marcellus (solo el wordmark de la barra lateral; fallback `'Times New Roman', serif`)
**Body Font:** IBM Plex Sans 400/500/600 (fallback `system-ui, sans-serif`)
**Label/Mono Font:** IBM Plex Mono 400/500

**Character:** Aquí la tipografía no tiene registro editorial: tiene que ser rápida de leer en tamaños pequeños y con muchas cifras. IBM Plex Sans resuelve cuerpo, formularios, tablas, botones y navegación; Marcellus sobrevive únicamente en el wordmark como última huella de la marca. IBM Plex Mono se usa solo para identificadores técnicos.

### Hierarchy

- **Headline** (Plex 600, `2.25rem`, 1.15; `1.75rem` en ≤768px): `h1` de página. Una por pantalla, siempre el mismo tamaño relativo.
- **Section** (Plex 600, `1.75rem`, 1.2): `h2` de módulo o bloque, cuando una página tiene dos secciones reales.
- **Title** (Plex 600, `1.25rem`, 1.3): `h3` de tarjeta, título de diálogo, nombre en resumen.
- **Body** (Plex 400, `1rem`, 1.6): párrafos de ayuda largos y texto del área de cliente.
- **UI** (Plex 400/500, `0.875rem`, 1.45): el tamaño por defecto del panel —tablas, campos, botones, navegación—. El `body` del panel es `--text-ui`: un panel que respira a 16px desperdicia filas.
- **Small** (Plex 400, `0.8125rem`, 1.4): ayudas de campo, metadatos, mensajes de error de campo.
- **Micro** (Plex 500, `0.75rem`, 1.35): píldoras de estado, contadores, notas legales.
- **Mono** (Plex Mono, `0.75rem`): `correlationId`, `blockId`, `operationId`, UUID, SKU, slug y códigos de error. Nada de "etiquetas mono" decorativas.

### Named Rules

**The Aligned-Figure Rule.** `font-variant-numeric: tabular-nums` en toda tabla, importe, contador y fecha. Los importes se alinean a la derecha; las cifras grandes de KPI pueden usar Plex 600 con tracking `-0.01em`, nunca más cerrado que `-0.02em`.

**The Sentence-Case Rule.** Minúscula de oración siempre: "Guardar cambios", "Registrar check-in", "Monto recibido ahora". Ningún titular en mayúsculas; ninguna etiqueta en versalitas espaciadas. Las siglas reales (DNI, PEN, SKU) conservan su forma.

**The Identifiers-Are-Mono Rule.** El mono no es un disfraz de "técnico": si no es un identificador, código o cifra de máquina, va en Plex Sans.

### Formatos de datos (obligatorios)

Implementados con pipes de Angular y locale `es-PE` registrado con `registerLocaleData`. Nunca por concatenación de cadenas.

| Dato | Formato | Ejemplo |
|---|---|---|
| Moneda | `S/` + miles con coma + 2 decimales | `S/ 1,234.50` |
| Fecha | `dd/MM/yyyy` | `04/10/2026` |
| Fecha y hora | `dd/MM/yyyy HH:mm` | `04/10/2026 15:30` |
| Cantidades y stock | entero; delta con signo | `+5`, `−3` |

El ajuste de stock nunca se envía con delta 0. Una cifra que el backend calcula no se recalcula en pantalla: se muestra.

---

## Layout

**Modelo espacial: la mesa.** Dos zonas fijas y una fluida.

- **Barra lateral** de `248px`, colapsable a `64px`, en Azul noche a sangre. Contiene el wordmark, el menú por rol y el control de colapso. Al colapsar, cada ítem conserva su etiqueta como tooltip accesible (`aria-label` + tooltip visual), nunca desaparece sin nombre.
- **Barra superior** de `56px` sobre Sillar blanco con junta inferior: en la izquierda el selector de sede (filtro de trabajo, no de seguridad) y en la derecha la persona con su rol y el menú de cuenta (Mi perfil, Cerrar sesión). En tablet y móvil, el botón de menú abre la barra lateral como panel superpuesto.
- **Contenido** fluido con padding de 24px (32px en ≥1280px) y ancho máximo de 1400px. Las tablas ocupan todo el ancho; **los formularios se limitan a 720px** para no volverse ilegibles.

### Anatomía de una página

1. **Cabecera de página:** `h1` y, debajo, una línea de contexto en Gris garúa. A la derecha, como máximo una acción secundaria ("Ver reportes").
2. **Barra de herramientas:** búsqueda y filtros a la izquierda, acción primaria a la derecha. En las pantallas que lo requieren, el filtro de sede es el primer control.
3. **Contenido:** tabla, rejilla de tarjetas, formulario o gráfico.
4. **Pie de lista:** paginación cuando hay `meta.page/size/totalElements`.

Sin migas de pan salvo en pantallas de detalle (`Recepción / Estadía 1001 / Cierre`), donde sustituyen al título de sección, no lo duplican.

### Rejillas

- **KPI del inicio:** 4 columnas ≥1280px, 2 columnas 768–1279px, 1 columna en móvil.
- **Tarjetas de resumen:** 2 columnas, 1 en móvil.
- **Formularios:** una columna hasta 720px; dos columnas solo para campos independientes y cortos, nunca partiendo una fila lógica (fechas de entrada y salida sí pueden compartir fila; nombre y documento no).

### Densidad

La densidad es un token, no una impresión.

| Pieza | Medida |
|---|---|
| Espaciado base | 4px: 4/8/12/16/24/32/48/64/96 |
| Control (input, select, botón) | 40px de alto |
| Fila de tabla | 44–48px |
| Celda de tabla | padding 12px/16px |
| Píldora de estado | 22px de alto |
| Padding interno de tarjeta | 20–24px |
| Área de cliente (C) | controles de 44px: se usa en móvil y con menos prisa |

### Breakpoints y responsive

`480 / 640 / 768 / 1024 / 1280px`. El panel es usable en tablet (≥768px) con la barra colapsada; en móvil la prioridad es no romper: la barra lateral se superpone con scrim, las tablas hacen scroll horizontal contenido en su tarjeta y los formularios pasan a una columna. El trabajo operativo se diseña a 1024px o más; el área de cliente se diseña a 390px.

---

## Elevation & Depth

**Plana en reposo, con dos excepciones.** La estructura se lee por juntas de 1px y cambios de superficie. Nada flota salvo lo que literalmente se superpone al contenido.

### Shadow Vocabulary

- **`--shadow-pop`** (`0 4px 16px rgba(38, 35, 31, 0.10)`): menús desplegables, tooltips, el toast.
- **`--shadow-dialog`** (`0 12px 32px rgba(38, 35, 31, 0.18)`): diálogos y confirmaciones.
- **Scrim de diálogo:** `rgba(32, 43, 58, 0.55)`, el mismo azul noche del hero de la landing.

### Capas

`contenido 0 · cabecera fija y cabecera de tabla 100 · desplegable 200 · diálogo 300 · toast 400`.

### Named Rules

**The No-Ghost-Card Rule.** Un contenedor lleva borde de 1px **o** sombra, nunca las dos. En este sistema se elige el borde; la sombra queda para lo que se superpone.

**The Sticky-Header Rule.** La cabecera de tabla se fija bajo la barra superior con fondo Sillar piedra y junta inferior. No se le añade sombra al fijarse: el borde es la señal.

---

## Shapes

- **Controles** — `4px`: campos, botones, selects, contenedores de imagen.
- **Tarjetas, diálogos y contenedores de tabla** — `8px`.
- **Píldoras** — `999px`, solo para etiquetas de estado y "chips" de rol. No para botones.
- **Juntas** — 1px de `#DCD8CC`. No hay bordes de 2px ni separadores punteados.
- **Barra activa de navegación** — 3px carmín a la izquierda del ítem activo, a sangre del borde del sidebar. Es la única barra vertical de color del sistema.
- **Resalte de penalidad** — fondo `#FCEBE9` y borde bermellón de 1px en la fila; el motivo se lee en la misma fila.

---

## Components

### Botones

- **Forma:** radio 4px, altura 40px, padding `10px 16px`, Plex Sans 500 `--text-ui`.
- **Primario:** carmín con texto blanco. Uno por pantalla o por bloque de acciones.
- **Secundario:** transparente con borde Junta y texto Tinta; hover sobre Sillar piedra.
- **Enlace:** sin caja, carmín subrayado; hover sin subrayado.
- **Destructivo:** bermellón con texto blanco, **solo** en flujos irreversibles (dar de baja, anular venta, anular check-in, mantenimiento). Siempre abre confirmación con motivo cuando el contrato lo exige.
- **Estado de envío:** al enviar, el botón se bloquea, muestra un spinner de 16px y conserva su texto ("Registrando…"). El doble clic nunca produce doble envío.
- **Deshabilitado:** 40 % de opacidad y `not-allowed`; el motivo se explica en el texto de ayuda cercano, no en un tooltip.
- **Etiqueta = verbo exacto**, y el mismo verbo se repite en el toast y en el título de la pantalla de destino.

### Formularios

- Etiqueta **visible** encima del campo, en minúscula de oración; requeridos con asterisco y `aria-required`. El placeholder nunca sustituye a la etiqueta.
- Campo: borde 1px Junta, fondo blanco, radio 4px, alto 40px, padding `10px 12px`.
- Foco: borde carmín y anillo `0 0 0 3px #F7E9EC` (nunca `outline: none` sin reemplazo visible).
- Error de campo: borde y anillo bermellón, mensaje bajo el campo en `--text-small` con el texto exacto del catálogo, campo con `aria-invalid` y `aria-describedby`.
- Ayuda: bajo el campo en Gris garúa. Contadores para límites ("0/300") y contador en vivo de bytes UTF-8 en las claves (12–72).
- Selects y selectores de fecha: mismos estados; el formato mostrado y aceptado es `dd/MM/yyyy`.
- Un formulario largo se agrupa en bloques con `h3` y junta superior; nunca en una lista continua de 20 campos.

### Tablas de datos

Es el corazón del panel y donde más se nota la densidad.

- Cabecera con fondo Sillar piedra, Plex 600 `--text-ui`, minúscula de oración, fija bajo la barra superior.
- Filas separadas por hairline Junta; sin cebrado. Hover en Sillar piedra; fila activa con barra carmín de 3px a la izquierda.
- Columnas numéricas y monetarias a la derecha y con cifras tabulares; fechas `dd/MM/yyyy`; estados como píldora.
- Acciones de fila: iconos SVG de 20px, trazo 1.5px, con `aria-label` descriptivo. Lo destructivo vive en el menú contextual de la fila, nunca como icono suelto.
- Paginación: "Anterior / Siguiente" y "Página X de Y · N registros", desde `meta.page/size/totalElements`; 20 por defecto, máximo 100.
- En móvil, scroll horizontal contenido en la tarjeta de la tabla, con la primera columna fija. No se convierte una tabla operativa en tarjetas ilegibles.

### Iconografía

- Un solo juego de iconos, dibujado en SVG, trazo de 1.5px, 20px en tablas y 16px dentro de controles.
- **Prohibido usar glifos Unicode o emoji como icono** (`≡`, `⟨`, `→`, `✓`, `⚠`). *(Cambio respecto a 1.0: el control de colapso del menú usa hoy glifos; pasa a icono SVG con `aria-label`.)*
- Todo icono con acción lleva nombre accesible; todo icono decorativo lleva `aria-hidden="true"`.

### Etiquetas de estado

Forma: píldora de 22px, `3px 10px`, `--text-micro` en 500, fondo tintado, punto de 6px del color intenso **+ texto**.

| Ámbito | Valor del contrato | Etiqueta UI | Color |
|---|---|---|---|
| Venta `estado` | PENDIENTE / PAGADO | "Pago pendiente" / "Pagado" | Ámbar / Verde |
| Venta `estadoOperacion` | PENDIENTE / CONFIRMADA / RECHAZADA / CANCELACION_PENDIENTE / CANCELADA | "En proceso" / "Confirmada" / "Rechazada" / "Anulación en proceso" / "Anulada" | Azul con spinner / Verde / Bermellón / Ámbar / Gris |
| Estadía | ACTIVA / CERRANDO / CERRADA | "Activa" / "Cierre en curso" / "Cerrada" | Verde / Ámbar / Gris |
| Estadía vencida | `vencida = true` | "Estadía vencida sin cierre" | Bermellón, con barra roja en la fila; la habitación sigue ocupada |
| Cierre | VALIDANDO / CONFIRMANDO / COMPLETADO / RECHAZADO | "Validando" / "Confirmando cobro" / "Completado" / "Rechazado" | Azul / Ámbar / Verde / Bermellón |
| Limpieza | PENDIENTE / COMPLETADA | "Pendiente" / "Completada" | Ámbar / Verde |
| Habitación física | LISTA / EN_LIMPIEZA / MANTENIMIENTO | "Lista" / "En limpieza" / "Mantenimiento" | Verde / Azul / Ámbar |
| Ocupación (proyección) | LIBRE / OCUPADA / DESCONOCIDA | "Libre" / "Ocupada" / "Sin datos" | Tinta / Tinta / Gris — **nunca "Disponible"** |
| Producto | `cantidad ≤ umbralBajo` | "Bajo stock" | Ámbar |

**Obligatorio:** `estado` (pago) y `estadoOperacion` (técnico) se muestran **por separado**, una píldora por columna ("Pago" y "Operación"), nunca fusionadas en un solo texto.

### Avisos, toasts y banners

- **Toast de éxito** (200/201): abajo a la derecha, verde, en pasado simple y exacto —"Cambios guardados", "Limpieza completada", "Venta registrada"—. Autocierre a los 5s, con botón de cierre y `role="status"`.
- **Banner de pendiente (`202`)**: franja persistente sobre el contenido, con spinner, texto "Operación en proceso. No repitas la acción." y el detalle del caso ("Cierre pendiente, no volver a cobrar"). Permanece hasta que el polling a `Location` confirme; cada reintento actualiza "Actualizado dd/MM/yyyy HH:mm" según `Retry-After`. Las acciones que duplicarían la intención quedan deshabilitadas mientras dura.
- **Banner de degradación** (reportes): ámbar, "Datos eventualmente consistentes" cuando `meta.degraded = true` o `consistency: EVENTUAL`. No bloquea la vista.
- **Banner de error:** bermellón, con el mensaje del catálogo, la acción esperada como botón ("Reintentar", "Recargar", "Iniciar sesión de nuevo") y el `correlationId` en mono con botón "Copiar".
- **Banner de éxito** (sin toast, para resultados que deben quedar visibles): verde, una línea.
- Ningún banner lleva borde izquierdo de color de más de 1px: la señal es el fondo tintado y el borde completo de 1px. *(Cambio respecto a 1.0: el banner de pendiente usa hoy `border-left: 3px`.)*

### Diálogos

- Radio 8px, ancho `min(560px, 100%)`, padding 24px, `max-height: 90dvh` con scroll interno, título `h3`, cuerpo claro y acciones a la derecha (secundaria "Cancelar" + primaria o destructiva).
- Confirmaciones destructivas con **motivo obligatorio** (1..300) cuando el contrato lo exige, y una frase que explique la consecuencia: "La venta quedará anulada y el stock volverá a la sede."
- Foco atrapado dentro del diálogo; `Esc` cierra salvo envío en curso; al cerrar, el foco vuelve al disparador.
- No se usa un diálogo para una tarea que necesita su propia pantalla (check-in, cierre): esos son rutas.

### Estados de pantalla

- **Carga:** esqueletos con la forma exacta del contenido —filas de tabla, tarjetas de KPI, líneas de formulario— sobre Sillar piedra, con pulso de opacidad de 1.6s. Nunca un spinner a página completa.
- **Vacío:** la frase exacta del contrato, centrada, en Gris garúa, con una línea de ayuda y la acción disponible si existe ("Aún no hay registros" + "Crear" para ADMIN). Sin ilustraciones grandes.
- **Error:** mensaje del catálogo, campos de `error.fields` resaltados, `correlationId` copiable y la acción esperada (reintentar con la misma clave, recargar con ETag, esperar el 202, contactar soporte). Prohibido redactar mensajes propios.
- **Sesión expirada (401):** modal "Tu sesión expiró. Vuelve a iniciar sesión." con botón al login; al volver, la acción no se repite automáticamente.

### Protocolo de estados (interacción)

1. **Éxito** `200/201`: acción nombrada en pasado, navegación o refresco de datos; el botón vuelve a su estado.
2. **Pendiente** `202`: banner persistente, polling a `Location` con `Retry-After`, acciones duplicables deshabilitadas; al terminar, refresco del recurso y el banner pasa a resultado.
3. **Validación** `400`: resaltar `error.fields`; el usuario corrige y reenvía con una intención nueva.
4. **Conflicto** `409`: mensaje del catálogo y acción esperada —`ROOM_OCCUPIED` "Elegir otra habitación", `STOCK_INSUFFICIENT` "Ajustar cantidades", `AMOUNT_MISMATCH` mostrar la nueva cotización—.
5. **Precondición** `412`: "Otra persona modificó este registro. Recarga para ver los cambios." con **Recargar** como acción primaria; jamás sobrescribir en silencio. `428`: recargar y reenviar.
6. **Límite** `429`: esperar `Retry-After`; en login, "Demasiados intentos. Espera un momento."
7. **Autenticación** `401`: relogin; el JWT vive en memoria.
8. **Permisos** `403` / inexistente `404`: mensaje genérico del catálogo, sin revelar si el recurso ajeno existe.
9. **Dependencia** `503`: "El servicio no está disponible. Reintenta en unos segundos." con reintento de la misma clave; en reportes, `REPORTING_NOT_READY`: "Los reportes aún se están preparando."
10. **Red caída:** estado de error con Reintentar; nunca datos de respaldo inventados.

### Tarjetas

- **KPI:** etiqueta corta arriba en `small` Gris garúa, cifra debajo en Plex 600 `--text-h1` con cifras tabulares, y opcionalmente un enlace de detalle en carmín. Fondo blanco, borde 1px Junta, sin sombra, sin icono decorativo, sin degradado. Una sola métrica domina visualmente por pantalla; las demás acompañan.
- **Resumen de cotización o cierre:** filas etiqueta-valor separadas por hairline; valores tabulares a la derecha; la fila de **penalidad** resaltada con fondo `#FCEBE9`, borde bermellón y su motivo visible. Cierra con la fila "Cobro ahora" en Plex 600 y junta superior.
- **Tarea de limpieza:** tarjeta compacta con número de habitación en Plex 600, categoría, píldora de estado y la acción "Completar". Una tarjeta por tarea; en escritorio, rejilla de tres.

### Piezas de dominio

- **Selector de cliente (check-in):** búsqueda por documento exacto, con estado de resultados y "Cliente no encontrado: créalo primero" enlazando al alta (solo A/E).
- **Selector de habitación (check-in):** filas o tarjetas con número, categoría, precio y dos píldoras —estado físico y ocupación—; solo habitaciones del catálogo de la sede. Un rechazo se pinta junto al campo `idHabitacion` con el mensaje del catálogo.
- **Desglose de cotización/cierre:** Alojamiento (noches × tarifa) / Adelanto registrado (−) / Penalidad (+ destacada con motivo) / Consumos pendientes (+) / **Cobro ahora**. El campo se rotula **"Monto recibido ahora"** y se compara exacto contra el cálculo del servidor; el front no recalcula. El total histórico se rotula **"Total histórico del alojamiento"** y va separado de los consumos.
- **Carrito (área C):** líneas con producto, cantidad (stepper 1..9999), precio y quitar; cambiar cantidad equivale a quitar + agregar. Los errores `PRODUCT_INACTIVE` y `STOCK_INSUFFICIENT` se pintan en la línea afectada.
- **Bajo stock:** valor en ámbar con píldora "Bajo stock"; sin alarmas rojas ni iconos de advertencia.
- **Galería de imágenes:** relación fija con `object-fit: cover`, espacio reservado (`aspect-ratio`, sin CLS), `alt` obligatorio (≤150) y fallback neutro si la imagen falla.
- **Gráficos de reportes:** una sola familia categórica en este orden —carmín, azul noche, verde confirmado, ámbar pendiente, azul proceso, gris garúa—; leyenda explícita, etiquetas con `S/` y fechas `es-PE`; sin donas arcoíris, sin 3D, sin sombras. El color va siempre acompañado de etiqueta o patrón.
- **Exportar:** PDF y Excel se generan en el frontend. El PDF reutiliza la tipografía del panel y las reglas hairline, con encabezados legibles en escala de grises: una tabla exportada no puede depender del color para entenderse.

### Navegación por rol

- La barra lateral muestra solo las secciones del rol; el área de cliente (`/mi-estadia`, catálogo, carrito, mis ventas) usa un shell propio **sin** menú administrativo.
- Ítem activo: fondo claro translúcido, texto `#F1F0EC`, barra carmín de 3px a la izquierda y `aria-current="page"`.
- El menú no se reordena por frecuencia de uso: el orden es estable entre sesiones para que la memoria muscular sirva.

---

## Do's and Don'ts

### Do:

- **Do** mostrar `estado` y `estadoOperacion` en columnas separadas, una píldora cada una.
- **Do** mostrar el `202` como "En proceso" con polling y acciones duplicables deshabilitadas.
- **Do** usar cifras tabulares y alinear importes a la derecha.
- **Do** mantener la densidad: control 40px, fila 44–48px, celda 12/16px.
- **Do** usar iconos SVG de un solo juego, trazo 1.5px, con nombre accesible.
- **Do** repetir el mensaje exacto del catálogo de errores y ofrecer la acción esperada.
- **Do** resaltar la penalidad con fondo bermellón teñido y su motivo en la misma fila.
- **Do** conservar el foco visible y atraparlo en los diálogos, devolviéndolo al disparador al cerrar.
- **Do** tematizar las superficies del navegador: selección en `#F7E9EC` sobre Tinta, `caret-color` carmín, barras de desplazamiento en Sillar piedra y Junta.

### Don't:

- **Don't** fusionar pago y operación en una sola etiqueta ni mostrar un `202` como éxito.
- **Don't** usar el carmín para errores, rechazos ni bajas: eso es bermellón.
- **Don't** representar un estado solo con color; siempre punto + texto.
- **Don't** recalcular importes en el front ni "corregir" lo que devuelve el servidor.
- **Don't** traducir `DESCONOCIDA` como "Libre" ni sugerir disponibilidad.
- **Don't** usar glifos Unicode o emoji como iconos.
- **Don't** poner etiquetas en mayúsculas espaciadas ni numerar secciones decorativamente.
- **Don't** añadir sombras a tarjetas en reposo ni bordes de color de más de 1px.
- **Don't** usar degradados, cristal decorativo, ilustraciones ni gráficos 3D.
- **Don't** reemplazar una tabla operativa por tarjetas en pantallas grandes.
- **Don't** escribir mensajes de error propios ni parafrasear el catálogo.
- **Don't** mostrar datos de otras personas, de otras sedes o de módulos no permitidos por rol.

### Vocabulario controlado

| Concepto | Término UI | Evitar |
|---|---|---|
| Ingreso de huésped | "Registrar check-in" / "Check-in" | "Reservar", "Reserva" |
| Salida con cobro | "Registrar salida" / "Cierre de estadía" | "Check-out pendiente" como estado interno |
| Cobro del cierre | "Monto recibido ahora" | "Total a pagar", "Monto final" |
| Consumos | "Venta", "Consumo" (C) | "Pedido" |
| Baja de maestro | "Dar de baja" | "Eliminar" (salvo DELETE técnico) |
| Corrección de check-in | "Anular check-in" | "Cancelar estadía" |
| Habitación sin huésped | "Libre" / "Ocupada" / "Sin datos" | "Disponible" |
| Total del alojamiento | "Total histórico del alojamiento" | "Total a pagar" |

### Mensajes de vacío canónicos (exactos)

"No hay personas que coincidan" · "No hay tareas pendientes" · "No hay estadías en esta sede" · "Sin movimientos en el rango" · "No hay ventas" · "El catálogo está vacío" · "Tu carrito está vacío" · "Aún no tienes consumos" · "No tienes una estadía activa; consulta en recepción" · "Sin datos para tu sede todavía".

### Voz

Sereno y servicial, nunca efusivo. El panel acompaña la operación sin dramatizar. Un error explica qué pasó y qué hacer; no se disculpa ni culpa al usuario. Sin signos de exclamación. Sin jerga técnica frente al usuario ("payload", "endpoint", "timeout"): se dice "el servicio no responde".

### Checklist antes de dar por lista una pantalla

1. Rol correcto visible: el menú y las acciones corresponden a A/E/C; el área de cliente no muestra nada administrativo.
2. Formatos `es-PE`: `S/ 1,234.50`, `dd/MM/yyyy`, `dd/MM/yyyy HH:mm`.
3. Estados resueltos: vacío con frase exacta, carga con esqueleto, error con mensaje del catálogo, acción y `correlationId`.
4. `202` como pendiente con polling; ningún éxito optimista.
5. Pago y operación en píldoras separadas; ningún estado solo por color.
6. Acciones destructivas con motivo y confirmación; "Dar de baja" donde corresponde.
7. Importes tabulares, a la derecha, y no recalculados en el front.
8. Densidad respetada: 40px de control, 44–48px de fila, 12/16px de celda.
9. Iconos SVG con nombre accesible; sin glifos Unicode ni emoji.
10. Teclado: foco visible, diálogos con foco atrapado, retorno al disparador.
11. Contraste AA en texto y en píldoras; ninguna etiqueta depende solo del color.
12. Probado a 1024px (panel) y 390px (área de cliente); `prefers-reduced-motion` respetado.

---

## Alcance

- **Fuera de alcance:** tema oscuro completo (el azul noche es solo el muro de navegación), login social, pasarela de pago, notificaciones en tiempo real por WebSocket.
- **Sugerencias opcionales, no comprometidas:** atajos de teclado visibles en tablas (`J/K` para moverse, `E` para editar); selector de sede persistente por usuario; aviso suave de expiración de sesión basado en `expiresIn`.
- **Gobierno:** cualquier patrón nuevo que contradiga estas reglas se discute primero y se agrega aquí. Si un token cambia, cambia en los dos documentos y en los dos `styles.css` en el mismo commit.
- **Trazabilidad:** este documento es derivado. Ante conflicto mandan `especificacion/08-admin-web.md` y `contracts/`.
