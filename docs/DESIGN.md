# Sistema de diseño — índice

**Versión:** 2.0 · 21/10/2026 · sustituye al documento único 1.0.

El sistema de diseño está partido en dos documentos, uno por aplicación. Este archivo solo sirve de entrada y de contrato entre ambos: no contiene reglas visuales propias.

| Aplicación | Documento | Ámbito |
|---|---|---|
| `apps/public-web` | [`DESIGN-public-web.md`](DESIGN-public-web.md) | Landing: inicio, sedes, detalle de sede, detalle de categoría, contacto, privacidad |
| `apps/admin-web` | [`DESIGN-admin-web.md`](DESIGN-admin-web.md) | Panel administrativo (ADMIN, EMPLEADO) y área de cliente (CLIENTE) |

**Concepto compartido: "Sillar y tinta".** Superficies color piedra, texto tinta, acento carmín cochinilla y azul noche para las superficies oscuras de navegación. La landing vende con fotografía y aire; el panel trabaja con datos y densidad. Una misma marca, dos registros.

## Qué documento aplica

- Se toca una ruta de `apps/public-web` → manda `DESIGN-public-web.md`.
- Se toca una ruta de `apps/admin-web` → manda `DESIGN-admin-web.md`.
- Se toca un token compartido (color, tipografía, espacio, radio, sombra, movimiento) → se toca **en los dos**: los valores son idénticos y deben seguir siéndolo.

## Contrato de tokens

Los tokens viven en el bloque `:root` de `apps/public-web/src/styles.css` y de `apps/admin-web/src/styles.css`, y están declarados en el frontmatter YAML de cada documento. Los tres sitios deben coincidir en valor, no solo en nombre.

Estado comprobado el 21/10/2026: ambos `:root` coinciden en los 51 tokens compartidos, salvo dos detalles que el plan de implementación corrige —`--color-texto-oscuro` está definido solo en `public-web` (el panel escribe `#F1F0EC` a mano) y la barra lateral del panel usa `--shadow-pop` en reposo, cuando este sistema solo admite sombra en capas que se superponen—.

Valores por defecto de la cadena:

| Grupo | Tokens |
|---|---|
| Piedra | `--color-sillar-blanco` `#F8F7F4` · `--color-sillar-piedra` `#EFEDE6` · `--color-junta` `#DCD8CC` · `--color-garua` `#6E6A61` · `--color-tinta` `#26231F` |
| Marca | `--color-cochinilla` `#8A1E33` · hover `#701829` · pulsado `#5C1421` · tinte `#F7E9EC` · `--color-azul-noche` `#202B3A` |
| Estado | verde `#1D7A4F`/`#E7F4EC` · ámbar `#8F6400`/`#FBF3E0` · bermellón `#B3261E`/`#FCEBE9` · azul proceso `#2361A8`/`#E8F0FA` · gris archivado `#5B5B5B`/`#ECECEA` |
| Tipografía | `--font-display` Marcellus · `--font-ui` IBM Plex Sans · `--font-mono` IBM Plex Mono |
| Escala | display · h1 · h2 · h3 · body · ui · small · micro |
| Espacio | base 4px: 4/8/12/16/24/32/48/64/96 |
| Forma | control 4px · tarjeta 8px · píldora 999px |

Lo que **no** es compartido: densidad, layout, navegación, jerarquía de página, componentes propios y voz. El panel comprime (control 40px, fila 44–48px); la landing respira (control 48px, secciones de 96–128px).

## Precedencia

Cuando dos fuentes se contradicen, gana la de arriba:

1. [`especificacion/02-landing-y-panel.md`](especificacion/02-landing-y-panel.md) y [`especificacion/08-admin-web.md`](especificacion/08-admin-web.md) — qué se puede hacer, campos, validaciones, textos y formatos obligatorios.
2. [`contracts/errors.md`](contracts/errors.md) y los contratos OpenAPI — mensajes literales y datos disponibles.
3. El `DESIGN-*.md` de la aplicación — paleta, tipografía, layout, componentes, movimiento.
4. El código en `apps/*/src/styles.css` — implementación. Si contradice al documento, se corrige el código.

Nada de este sistema autoriza a inventar endpoints, códigos de error, reglas de negocio ni valores de contrato.

## Cómo se cambia

- **Un token:** se edita en los dos documentos (frontmatter y prosa si aparece) y en los dos `styles.css`, en el mismo commit. Si solo cambia una app, es un error de sistema, no una excepción.
- **Un componente o un patrón nuevo:** se propone primero, se agrega al documento de la aplicación y después se implementa. No se implementa primero y se documenta después.
- **Un mensaje de UI:** se cambia solo en [`contracts/errors.md`](contracts/errors.md); los documentos de diseño enlazan, nunca duplican el catálogo.
- **Una pantalla nueva:** se planifica citando la sección del `DESIGN-*.md` que aplica y se cierra con su checklist.

## Relación con los planes

Los planes de ámbito `front` citan el documento de la app que tocan —`DESIGN-public-web.md` o `DESIGN-admin-web.md`— y usan sus tokens y su checklist. Ver [`plans/README.md`](plans/README.md) §6 y [`implementacion/`](implementacion/README.md).

## Historial

- **1.0** (12/10/2026): documento único para las dos apps. Su contenido se distribuyó entre los dos documentos actuales, que lo corrigen en los puntos donde el sistema se contradecía: etiqueta sobre el titular del hero, rejilla de tarjetas idénticas en el inicio, glifos Unicode usados como iconos y bordes izquierdos de color en los avisos.
- **2.0** (21/10/2026): separación por aplicación, tokens en frontmatter y estructura canónica del formato `DESIGN.md`.
