---
name: Cadena hotelera — public-web
description: Landing editorial de una cadena hotelera en Lima y Arequipa; vende con fotografía, informa con exactitud y no reserva ni cobra en línea.
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
  display:
    fontFamily: "Marcellus, 'Times New Roman', serif"
    fontSize: "clamp(2.5rem, 4vw + 1rem, 3.75rem)"
    fontWeight: 400
    lineHeight: 1.08
  headline:
    fontFamily: "Marcellus, 'Times New Roman', serif"
    fontSize: "1.75rem"
    fontWeight: 400
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
    typography: "{typography.body}"
    rounded: "{rounded.control}"
    padding: "12px 24px"
    height: "48px"
  boton-secundario:
    backgroundColor: "transparent"
    textColor: "{colors.tinta}"
    typography: "{typography.body}"
    rounded: "{rounded.control}"
    padding: "12px 24px"
    height: "48px"
  boton-sobre-foto:
    backgroundColor: "transparent"
    textColor: "{colors.texto-oscuro}"
    typography: "{typography.body}"
    rounded: "{rounded.control}"
    padding: "12px 24px"
    height: "48px"
  campo:
    backgroundColor: "#FFFFFF"
    textColor: "{colors.tinta}"
    typography: "{typography.ui}"
    rounded: "{rounded.control}"
    padding: "10px 12px"
    height: "48px"
  tarjeta-sede:
    backgroundColor: "#FFFFFF"
    textColor: "{colors.tinta}"
    rounded: "{rounded.card}"
  tarjeta-categoria:
    backgroundColor: "#FFFFFF"
    textColor: "{colors.tinta}"
    rounded: "{rounded.card}"
---

# Sistema de diseño: `public-web`

**Ámbito:** `apps/public-web`. Rutas `/`, `/sedes`, `/sedes/:slug`, `/sedes/:slug/habitaciones/:idCategoria`, `/contacto`, `/privacidad`.
**Versión:** 2.0 · 21/10/2026 · sustituye a la parte pública de `docs/DESIGN.md` 1.0.
**Compañero:** [`DESIGN-admin-web.md`](DESIGN-admin-web.md) comparte exactamente los mismos tokens. Solo cambian densidad, layout, componentes y voz.

Este documento es la autoridad visual de la landing. **No manda sobre la especificación.** Ante conflicto, el orden es:

| Prioridad | Fuente | Qué manda |
|---|---|---|
| 1 | [`especificacion/02-landing-y-panel.md`](especificacion/02-landing-y-panel.md) | Qué puede y qué no puede hacer la landing, textos obligatorios, formatos |
| 2 | [`contracts/errors.md`](contracts/errors.md) y `contracts/openapi/public-web.yaml` | Mensajes de error literales y datos disponibles |
| 3 | Este documento | Paleta, tipografía, layout, componentes, movimiento, copy visual |
| 4 | `apps/public-web/src/styles.css` | Implementación actual; si contradice a este documento, se corrige el código |

Reglas de negocio que el diseño **nunca** puede contradecir: la landing no reserva, no cobra, no muestra disponibilidad en vivo, no muestra número de habitación, ocupantes ni limpieza, y no inventa precios si la API falla.

Los encabezados de sección van en inglés porque así los exige el formato `DESIGN.md` que consumen las herramientas de diseño; el contenido va en español.

---

## Overview

**Creative North Star: "El claustro abierto".**

La identidad nace de un material peruano concreto: el **sillar blanco de Arequipa**, la piedra volcánica con la que se tallan fachadas y claustros. De ahí salen las tres decisiones que gobiernan toda la landing: superficies color piedra, estructura visible en juntas de 1px, y una tipografía que se lee como algo grabado más que como algo impreso. El claustro aporta además el ritmo: patios de luz entre muros de piedra, alternancia de lleno y vacío, silencio entre secciones.

El registro es **editorial y fotográfico**. La página se comporta como el dossier impreso de una cadena hotelera: primero la fotografía, después el nombre de la sede, después el dato. La marca nunca grita. No hay insignias de oferta, ni contadores, ni urgencia, ni "disponible ahora": la cadena no vende por impulso, vende por confianza, y la confianza se construye con exactitud —una tarifa con su aviso completo, una dirección verificable, un enlace de contacto que funciona—.

La interfaz es **casi plana**. La jerarquía se construye con cambios de superficie y juntas de 1px, no con sombras decorativas. El único momento expresivo autorizado es la entrada del hero; todo lo demás calla y deja que el contenido ocupe el espacio. El color se reserva a dos usos: el carmín cochinilla para acciones y enlaces, y el azul noche para la banda oscura del hero y el pie.

**Key Characteristics:**

- Fotografía primero; el texto acompaña, no compite.
- Un solo acento de marca (carmín cochinilla) usado en menos del 10 % de cualquier pantalla.
- Estructura por juntas de 1px y superficies de piedra; cero sombras en reposo.
- Titulares en serif epigráfica Marcellus; cuerpo y UI en IBM Plex Sans.
- Tarifa siempre como dato sobrio, con el aviso completo, nunca como oferta.
- Un único gesto de movimiento por carga de página, en el hero.

**Lo que este sistema evita deliberadamente:** el combo crema + serif + terracota; el lujo "navy & gold" de plantilla hotelera; el kit SaaS de tarjetas idénticas con la misma sombra suave; los degradados como decoración; las etiquetas en mayúsculas espaciadas ("eyebrows") sobre los titulares; las ilustraciones SVG que imitan fotografías.

---

## Colors

La paleta es mineral y contenida: cinco neutros de piedra, un acento de marca y cinco familias funcionales que solo aparecen cuando informan de un estado.

### Primary

- **Carmín cochinilla** (`#8A1E33`): el rojo profundo del tinte andino, muy lejos del terracota genérico. Es el único color de marca: botones primarios, enlaces, subrayado activo de navegación, indicador de foco y wordmark. Contraste ≈ 8.5:1 sobre Sillar blanco. Hover `#701829`, pulsado `#5C1421`.
  - Tinte de selección y de anillo de foco: **`#F7E9EC`** (`cochinilla-wash`).
  - El carmín **nunca** representa error, rechazo ni baja: para eso está el bermellón funcional.

### Secondary

- **Azul noche arequipeña** (`#202B3A`): el cielo de Arequipa al caer la noche. Se usa en tres lugares y en ningún otro: el scrim sobre la fotografía del hero (`rgba(32, 43, 58, 0.55)`), la banda del pie de página y el texto sobre superficies oscuras. Texto claro sobre este fondo: `#F1F0EC`.

### Neutral

- **Sillar blanco** (`#F8F7F4`): fondo global. Blanco mineral, apenas cálido, nunca blanco puro.
- **Sillar piedra** (`#EFEDE6`): superficies secundarias —bloques de aviso, cabeceras de sección, fondo de las fichas al pasar el cursor—.
- **Junta de sillar** (`#DCD8CC`): bordes de 1px, divisores y contornos. Es la estructura visible del sistema: si algo necesita separarse, se separa con una junta.
- **Gris garúa** (`#6E6A61`): texto secundario, metadatos, ayudas de campo. Contraste ≈ 4.7:1 sobre Sillar blanco; no se usa para texto pequeño sobre Sillar piedra sin verificar.
- **Tinta de sillar** (`#26231F`): texto principal y titulares. Casi negro con calidez mineral.

### Funcionales — solo estado, nunca decoración

La landing pública solo necesita tres de las cinco familias: información, aviso y error. Verde y gris archivado existen en el sistema compartido pero la landing no los usa.

| Token | Hex | Fondo tintado | Uso en la landing |
|---|---|---|---|
| Azul proceso | `#2361A8` | `#E8F0FA` | Información neutral, foco de lectura, enlaces dentro de avisos |
| Ámbar pendiente | `#8F6400` | `#FBF3E0` | Aviso que no bloquea (catálogo reducido, filtro sin resultados) |
| Bermellón alerta | `#B3261E` | `#FCEBE9` | Error de carga, formulario inválido, imagen que falla |

Todos los tintados son relleno de etiquetas y avisos; el texto que contienen usa el color intenso correspondiente. **El color nunca es el único portador de significado**: todo aviso lleva texto, todo error lleva mensaje.

### Named Rules

**The One Accent Rule.** El carmín ocupa menos del 10 % de cualquier pantalla y nunca dos elementos primarios compiten en el mismo bloque. Su rareza es el punto.

**The Stone Rule.** En reposo no hay sombras. La profundidad se expresa con juntas de 1px y cambios de superficie.

**The Rate-Is-Data Rule.** La tarifa no lleva color de oferta, ni insignia, ni descuento, ni urgencia. Se presenta en Tinta con sus cifras tabulares y su aviso, como se presenta una dirección.

---

## Typography

**Display Font:** Marcellus (fallback `'Times New Roman', serif`)
**Body Font:** IBM Plex Sans (fallback `system-ui, sans-serif`)
**Label/Mono Font:** IBM Plex Mono (solo identificadores técnicos; la landing casi no la usa)

**Character:** Marcellus es una serif romana de inspiración epigráfica —la letra "tallada en piedra"— y aporta el registro monumental sin recurrir a una didona de lujo. IBM Plex Sans es la voz de trabajo: humanista, precisa, con cifras claras a tamaños pequeños. La tensión entre ambas es la identidad: piedra arriba, operación abajo. No se usa una cuarta familia.

### Hierarchy

- **Display** (400, `clamp(2.5rem, 4vw + 1rem, 3.75rem)`, 1.08): solo el `h1` del hero, en blanco sobre la fotografía. Una sola grada de peso; la jerarquía se logra con tamaño, jamás con negrita simulada.
- **Headline** (Marcellus 400, `1.75rem`, 1.2): `h2` de sección en la landing. Se usa con moderación: como máximo un `h2` por sección, y nunca dos secciones consecutivas con el mismo peso visual.
- **Title** (Plex 600, `1.25rem`, 1.3): `h3` dentro de fichas, nombre de sede, nombre de categoría.
- **Body** (Plex 400, `1rem`, 1.6): párrafos, descripciones de sede y de categoría. Medida máxima **65–75 caracteres** (`max-width: 65ch`); el texto largo nunca pasa de 75.
- **UI** (Plex 400/500, `0.875rem`, 1.45): botones, navegación, etiquetas de campo, paginación.
- **Small** (Plex 400, `0.8125rem`, 1.4): ayudas de campo, metadatos, ciudad bajo el nombre de la sede.
- **Micro** (Plex 500, `0.75rem`, 1.35): notas legales muy breves, contador de caracteres.
- **Mono** (Plex Mono, `0.75rem`): solo `correlationId` y similares. Si no es un identificador, no va en mono.

### Named Rules

**The No-Kicker Rule.** Ningún titular lleva una etiqueta por encima que lo anuncie. Nada de "Cadena hotelera", "Nuestras sedes" ni versalitas decorativas sobre un `h1` o `h2`: el titular carga su propio peso y la etiqueta se elimina. *(Cambio respecto a 1.0: la landing actual tiene un `hero__etiqueta` sobre el `h1`; se retira.)*

**The Sentence-Case Rule.** Todo va en minúscula de oración: "Ver tipos de habitación", "Consulta disponibilidad con recepción". Mayúsculas sostenidas solo en siglas reales (DNI, S/). Sin texto justificado en ninguna pantalla.

**The Figure Rule.** Toda cifra monetaria va con `font-variant-numeric: tabular-nums`. En listas y comparaciones los importes se alinean a la derecha; en prosa se alinean con el texto.

### Formatos de datos (obligatorios)

Se implementan con los pipes de Angular y el locale `es-PE` registrado con `registerLocaleData`. Nunca se construyen concatenando cadenas.

| Dato | Formato | Ejemplo |
|---|---|---|
| Moneda | `S/` + miles con coma + 2 decimales | `S/ 70.00`, `S/ 1,234.50` |
| Fecha | `dd/MM/yyyy` | `04/10/2026` |
| Fecha y hora | `dd/MM/yyyy HH:mm` | `04/10/2026 15:30` |

---

## Layout

**Modelo espacial: patios.** Un contenedor de **1200px** con márgenes laterales de 20/24/32/48px según breakpoint, rejilla de 12 columnas, y secciones separadas por **96–128px** de aire. Dentro de una sección, los bloques relacionados se separan por 48px. Ese contraste entre mucho aire entre secciones y poco dentro de ellas es lo que hace que la página se lea como un claustro y no como una pila de bloques.

Las páginas de texto —Privacidad— usan un contenedor de **720px** y la misma medida de 65–75 caracteres.

### El hero

- Banda fotográfica **full-bleed**, altura mínima de `min(72vh, 640px)` en escritorio, con la fotografía cubriendo el área (`object-fit: cover`) y `fetchpriority="high"`. En móvil la relación pasa a **4:5** y la altura se reduce a `60vh` para que el titular y la acción queden sobre la primera pantalla.
- Scrim de Azul noche al 55 % sobre toda la imagen, con un refuerzo lineal hacia la izquierda si la fotografía es clara. El texto siempre alcanza AA sobre la zona más clara de la foto: si no lo alcanza, se sube el scrim, no se cambia el color del texto.
- Contenido alineado a la izquierda, en este orden y sin nada más: `h1` en Marcellus blanco, una línea de propuesta de valor en `--text-h2` Plex 400, y **dos acciones**: "Contactar" (primaria, carmín) y "Ver sedes" (secundaria sobre foto, contorno claro). **No hay etiqueta sobre el `h1`.**
- La fotografía es propia o licenciada. Nunca un SVG o un degradado simulando una fachada.

### Ritmo de la página de inicio

La estructura es editorial, no una rejilla de tarjetas. Después del hero:

1. **Qué encontrarás aquí** — una introducción de una o dos frases y, debajo, un **índice de tres entradas en dos columnas** con `dl`/`dt`/`dd`: cada entrada lleva su título enlazado y una frase. Sin contorno de tarjeta, sin sombra, separadas por juntas de 1px. *(Cambio respecto a 1.0: hoy son tres `article.tarjeta` idénticas; la rejilla de tarjetas iguales es el patrón que este sistema evita.)*
2. **Antes de venir** — bloque sobre Sillar piedra a sangre dentro del contenedor, con el aviso de no disponibilidad y la tarifa obligatoria. Es el único bloque con fondo tintado de la página.
3. **Cierre con llamada a contacto** — una línea y un enlace, sin tarjeta.

### Página de sede

Cabecera con ciudad en `small` Gris garúa, nombre en `h1` Marcellus, descripción en `body` limitada a 65ch, y una lista de datos (`dl`) con dirección y ciudad separadas por juntas. Después, el filtro de capacidad y la rejilla de categorías.

### Rejilla de categorías

- Escritorio: 3 por fila (4/12 cada una). Tablet: 2 por fila (6/12). Móvil: 1 por fila (12/12).
- La separación entre tarjetas es de 24–32px; nunca se solapan ni comparten sombra.
- Las tarjetas de una misma fila igualan altura por la imagen, no por el texto: la imagen mantiene su relación y el cuerpo crece.

### Breakpoints

`480 / 640 / 768 / 1024 / 1280px`, mobile-first. La landing es prioritaria en móvil: toda la navegación cabe en una fila y el hero se lee completo a 390px sin desplazamiento horizontal.

---

## Elevation & Depth

**Plana por defecto.** No hay sombras en reposo en ningún elemento de la landing. La profundidad se expresa de tres maneras y solo de tres: el scrim sobre fotografía, el contraste entre Sillar blanco y Sillar piedra, y la junta de 1px.

### Shadow Vocabulary

- **`--shadow-pop`** (`0 4px 16px rgba(38, 35, 31, 0.10)`): reservada a capas flotantes que hoy la landing no tiene. Si en el futuro aparece un menú desplegable o un selector flotante, usa esta y ninguna otra.

No hay diálogos, ni toasts, ni tooltips en la landing: no se especifican sombras para ellos.

### Named Rules

**The Flat-By-Default Rule.** Una tarjeta con borde de 1px y sombra suave a la vez es un error: se elige una de las dos, y en este sistema se elige el borde.

**The Scrim-Only Rule.** La única transparencia oscura permitida sobre contenido es el scrim del hero. Nada de cristal, desenfoques decorativos ni veladuras sobre fotografías de fichas.

---

## Shapes

- **Controles** — radio `4px`: "esquina cincelada, no juguete". Botones, campos, selects y el contenedor del filtro.
- **Tarjetas y contenedores** — radio `8px`, aplicado solo a las esquinas superiores de la imagen cuando la imagen es full-bleed dentro de la ficha.
- **Píldoras** — `999px`, reservadas a etiquetas de estado. La landing no muestra estados operativos, así que en la práctica no aparecen.
- **Juntas** — 1px de `#DCD8CC`. Es la única línea del sistema; no hay bordes de 2px ni separadores punteados.
- **Fotografías** — relación fija: `3:2` en la ficha de sede, `4:3` en la ficha de categoría, `21:9`/`4:5` en el hero. Siempre con `object-fit: cover`, espacio reservado (`aspect-ratio`) y `alt` descriptivo.

---

## Components

### Botones

- **Forma:** radio 4px, altura mínima 48px, padding `12px 24px`, Plex Sans 500. El área táctil nunca baja de 44px.
- **Primario:** fondo Carmín `#8A1E33`, texto blanco. Hover `#701829`; pulsado `#5C1421`. En la landing es la acción de contacto y la acción de filtrado.
- **Secundario:** fondo transparente, borde 1px Junta, texto Tinta; hover sobre Sillar piedra.
- **Sobre fotografía:** fondo transparente, borde 1px `#F1F0EC`, texto `#F1F0EC`; hover con velo `rgba(241, 240, 236, 0.12)`. Solo en el hero.
- **Enlace:** sin caja, texto carmín, subrayado al hover y al foco.
- **Deshabilitado:** 40 % de opacidad, cursor `not-allowed`, sin cambio de color.
- **Etiqueta = verbo exacto:** "Contactar", "Ver sedes", "Filtrar", "Quitar filtro". Nunca "Haz clic aquí", nunca "Descubre más".

### Navegación

- **Cabecera:** clara sobre Sillar blanco con junta inferior de 1px; wordmark "Hotel" en Marcellus con el nombre de la cadena en Plex 600 al lado. Enlaces Sedes / Contacto / Privacidad en `--text-ui`, con el activo subrayado en carmín. A la derecha, "Acceso al panel" como enlace sobrio, no como botón.
- **Comportamiento al hacer scroll:** la cabecera se fija con fondo `rgba(248, 247, 244, 0.92)` y desenfoque de 8px, y **la junta inferior es la que mantiene el borde**; no se añade sombra al fijarse.
- **Móvil:** el wordmark y los enlaces caben en una fila; si no caben, se pliegan en un menú nativo (`<details>` o un botón con `aria-expanded`) que se abre a pantalla completa sobre Sillar blanco. Sin animación de entrada en el menú.
- **Pie:** banda Azul noche con el nombre de la cadena, el aviso de no disponibilidad, los enlaces de privacidad y contacto, y el copyright. Texto en `#F1F0EC`; enlaces subrayados al hover. El foco sobre el pie usa `#D7A3AE`.
- **Foco siempre visible:** `outline: 2px solid #8A1E33; outline-offset: 2px`; sobre superficies oscuras, `#D7A3AE`. Nunca `outline: none` sin reemplazo.

### Fichas de sede

- Imagen full-bleed `3:2` arriba, esquinas superiores de 8px.
- Cuerpo con padding 24px: ciudad en `small` Gris garúa, nombre en Plex 600 `--text-h3`, descripción de dos o tres líneas, enlace "Ver sede" en carmín.
- Borde 1px Junta, fondo blanco, **sin sombra en reposo**. Hover: fondo Sillar piedra en el cuerpo y junta a Tinta al 20 %; nada se mueve ni escala.

### Fichas de categoría

- Imagen `4:3` arriba; cuerpo con nombre, capacidad en `small` ("Hasta 2 personas"), detalle y **bloque de tarifa**.
- **Bloque de tarifa** — obligatorio y con esta jerarquía: importe en Plex 600 y tabular, precedido por la palabra "Desde" y seguido de "por noche"; debajo, en `small` y Gris garúa, el aviso "Consulta disponibilidad con recepción. No constituye una reserva." El aviso nunca se abrevia, nunca se oculta en un tooltip y nunca se separa del importe.
- Enlace "Ver tipo de habitación" con `aria-label` que incluya el nombre de la categoría y el de la sede.

### Filtro de capacidad

- Un campo numérico con etiqueta visible ("Capacidad"), ayuda bajo el campo ("Opcional, de 1 a 10 personas") y dos acciones: "Filtrar" (primaria) y "Quitar filtro" (secundaria).
- Error de validación bajo el campo, con el mensaje del contrato, `aria-invalid` y `aria-describedby` apuntando a la ayuda y al error. El error se anuncia con `role="alert"`.
- El filtro nunca se envía solo: exige la acción explícita.

### Estados de pantalla

- **Carga:** esqueletos con la forma exacta del contenido —título, dos líneas, rejilla de fichas— sobre Sillar piedra con pulso de opacidad de 1.4s. Nunca un spinner a página completa. El contenedor lleva `aria-busy="true"` y una etiqueta que describa qué se está cargando.
- **Vacío:** una frase exacta del contrato ("El catálogo está vacío para este filtro") en Gris garúa, centrada, sin ilustración. Si hay una acción razonable, se ofrece debajo.
- **Error:** mensaje del catálogo, la acción esperada como botón ("Reintentar") y el `correlationId` en mono si el contrato lo expone. Nunca se inventa un precio, ni se muestran datos internos como respaldo.
- **Imagen que falla:** el componente de imagen cae a un fondo Sillar piedra con la etiqueta del tema; el layout, el texto y la tarifa no cambian.

### Movimiento

- **Un único gesto por carga:** el contenido del hero entra con opacidad 0→1 y `translateY(8px)→0` en 400ms con `cubic-bezier(0.2, 0.6, 0.2, 1)`. Se ejecuta **una vez**, en el hero, y no se repite en cada sección ni en cada tarjeta.
- **Hover y foco:** 150ms. Cambios de estado de formulario: 200ms.
- **Nada más:** sin parallax, sin scroll-jacking, sin animaciones en bucle, sin revelados al hacer scroll.
- **`prefers-reduced-motion: reduce`:** se desactivan todas las entradas; solo quedan cambios instantáneos de opacidad. El esqueleto de carga deja de pulsar y mantiene una opacidad fija.

### Superficies del navegador

Forman parte del sistema y se tematizan desde la paleta: `::selection` con fondo `#F7E9EC` y texto Tinta; `caret-color: #8A1E33`; barras de desplazamiento discretas en Sillar piedra con el pulgar en Junta; `text-underline-offset: 3px` en todos los enlaces subrayados. Los valores por defecto del navegador no pertenecen a ningún sistema y se corrigen.

---

## Do's and Don'ts

### Do:

- **Do** usar la fórmula completa de tarifa: "Desde S/ 70.00 por noche. Consulta disponibilidad con recepción. No constituye una reserva.", con el importe real de `precioDesde` y el texto acompañante literal.
- **Do** escribir el contacto con datos reales del negocio; `tel:`, `mailto:` y WhatsApp solo se enlazan cuando el dato existe y funciona. Si un dato no está disponible, se muestra como texto, no como enlace roto.
- **Do** reservar el carmín para acciones y enlaces: menos del 10 % de la pantalla, un solo primario por bloque.
- **Do** separar con juntas de 1px y cambios de superficie; la jerarquía se construye así.
- **Do** dar a toda imagen su `alt` descriptivo, su `aspect-ratio` y su fallback neutro.
- **Do** usar `lang="es-PE"`, pipes de Angular para moneda y fechas, y cifras tabulares en importes.
- **Do** mantener el foco visible en todo elemento interactivo, con `#8A1E33` sobre claro y `#D7A3AE` sobre oscuro.
- **Do** dejar que la landing informe y oriente: sedes, tipos de habitación, tarifa, contacto, privacidad.

### Don't:

- **Don't** poner etiquetas, "eyebrows", versalitas ni numeración por encima de un titular.
- **Don't** usar tarjetas idénticas repetidas como estructura de la página, ni anidar tarjetas.
- **Don't** afirmar "disponible", "disponibilidad garantizada", "últimas habitaciones" ni nada que sugiera reserva o inventario en vivo.
- **Don't** mostrar número de habitación, huéspedes, estado de limpieza, ocupación ni ningún dato operativo.
- **Don't** mostrar la tarifa como oferta: sin insignias de descuento, sin tachados, sin cuenta atrás, sin color de promoción.
- **Don't** inventar precios ni mostrar datos del panel cuando la API falla: se muestra el error y se ofrece reintentar.
- **Don't** usar degradados, cristal, desenfoques decorativos, sombras en reposo ni bordes de color de más de 1px.
- **Don't** usar SVG, ilustraciones vectoriales ni degradados en lugar de fotografía.
- **Don't** incrustar el panel, tokens, credenciales ni rutas administrativas en el bundle público.
- **Don't** animar cada sección al hacer scroll ni repetir la entrada del hero en otras páginas.
- **Don't** escribir en mayúsculas sostenidas, con signos de exclamación ni con jerga técnica ("payload", "endpoint", "API").

### Checklist antes de dar por lista una pantalla

1. El `h1` es único, en Marcellus, y no lleva etiqueta encima.
2. La tarifa aparece con su aviso completo, en tabular y sin color de oferta.
3. Formatos `es-PE`: `S/ 1,234.50`, `dd/MM/yyyy`, `dd/MM/yyyy HH:mm`.
4. Estados resueltos: carga con esqueleto, vacío con frase exacta, error con mensaje del catálogo y acción.
5. Ninguna promesa de reserva, disponibilidad ni pago en ninguna parte del texto.
6. Se ve sin desplazamiento horizontal a 390px y se lee completo a 1024px y 1440px.
7. Teclado: orden de tabulación natural, foco visible, enlaces con texto descriptivo.
8. Contraste AA en texto y en texto sobre fotografía; `alt` en todas las imágenes.
9. `prefers-reduced-motion` respetado; un solo gesto de movimiento por carga.
10. Superficies del navegador tematizadas: selección, cursor de texto, barras de desplazamiento.

---

## Alcance

- **Fuera de alcance:** reservas, pagos, disponibilidad en vivo, cuentas de usuario en la landing, tema oscuro completo, chat, mapas interactivos.
- **Sugerencias opcionales, no comprometidas:** una página de galería por sede; un aviso de cookies; una versión en inglés con el mismo sistema tipográfico.
- **Gobierno:** cualquier patrón nuevo que contradiga estas reglas se discute primero y se agrega aquí. Si un token cambia, cambia en los dos documentos y en los dos `styles.css` en el mismo commit.
- **Trazabilidad:** este documento es derivado. Ante conflicto mandan `especificacion/02-landing-y-panel.md` y `contracts/`.
