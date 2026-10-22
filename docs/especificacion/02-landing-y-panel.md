# Landing pública y panel de administración

## Objetivo y límites

`public-web` presenta la cadena de hoteles al público. `admin-web` contiene login y funciones privadas según rol; incluye el área ya existente de cliente para consultar su estadía y pedir productos. Son dos builds Angular; no se duplican los microservicios.

Ambas apps son una **aplicación de presentación real**: se ven y se operan como un producto terminado, con una cadena hotelera, sedes, habitaciones, productos e imágenes verosímiles. «Presentación» describe el uso —se muestra y se sustenta—, no la calidad del dato: la cadena y sus datos son ficticios, no corresponden a una empresa real y no se presentan como tales.

La autoridad visual es [`../DESIGN-public-web.md`](../DESIGN-public-web.md) para la landing y [`../DESIGN-admin-web.md`](../DESIGN-admin-web.md) para el panel; [`../DESIGN.md`](../DESIGN.md) solo indexa ambos documentos. Este documento manda sobre ellos en reglas de negocio, datos permitidos y textos obligatorios.

## Landing: sí puede

| Pantalla/acción | Datos y contrato |
|---|---|
| Inicio | Presentación, servicios generales, sedes destacadas y enlaces a sedes |
| Sedes | GET /api/public/hoteles |
| Detalle de sede | GET /api/public/hoteles/{slug} |
| Tipos de habitación por sede | GET /api/public/hoteles/{slug}/categorias |
| Detalle de tipo/categoría | GET /api/public/hoteles/{slug}/categorias/{idCategoria} |
| Fotografía de la sede | `imagenUrl` e `imagenAlt` de `HotelPublico`; si faltan, fallback neutro |
| Fotografías de habitación y tarifa “desde” | Solo imágenes aprobadas y precio mínimo de habitaciones publicadas de esa categoría |
| Contactar | Enlace tel:, mailto: o WhatsApp del negocio, elegido por el visitante |
| Acceso privado | Enlace al login del panel; no embebe el panel ni tokens |

Páginas estáticas: `/`, `/sedes`, `/sedes/:slug`, `/sedes/:slug/habitaciones/:idCategoria`, `/contacto`, `/privacidad`. Estas son rutas Angular, no endpoints backend adicionales.

## Landing: no puede

- Ver huéspedes, documentos, correos privados, estadías, consumos, stock, ingresos ni reportes.
- Mostrar número de habitación concreto, ocupantes, estado de limpieza ni lista operativa de habitaciones.
- Afirmar “disponible ahora” o garantizar disponibilidad para fechas. No consulta la proyección de ocupación.
- Crear/editar/eliminar personas, sedes, productos, ventas o habitaciones.
- Hacer check-in/check-out, reservar, cobrar, emitir comprobantes o cambiar roles.
- Conectarse directamente a DB, Feign, Kafka, RabbitMQ o Eureka.
- Tener formulario que envíe correo arbitrario o abra un endpoint SMTP público. Contacto es enlace externo, no una cola pública.
- Subir archivos ni consumir rutas administrativas usando credenciales incrustadas.

Texto de tarifa: “Desde S/ 70.00 por noche. Consulta disponibilidad con recepción. No constituye una reserva.” Las fotografías y los textos son propios o licenciados, y su procedencia queda registrada en el repositorio. Los datos de la cadena son ficticios y marcados como tales donde corresponde; ver § Condición de presentación.

## Panel: matriz de acceso

| Función | ADMIN | EMPLEADO | CLIENTE |
|---|---|---|---|
| Crear personal / asignar roles existentes y sedes | Sí | No | No |
| Crear y buscar clientes | Sí | Sí, tipo Cliente únicamente | Solo registro propio / perfil propio |
| Configurar sedes, pisos, categorías, habitaciones | Sí | Leer sedes asignadas | No |
| Completar limpieza | Sí | Sí, sede asignada | No |
| Registrar estadía y cierre | Sí | Sí, sede asignada | No |
| Consultar estadías | Todas | Sedes asignadas | Solo propias |
| Productos/precios/stock | CRUD | Lectura de sede | Catálogo habilitado para su estadía |
| Ajustar inventario | Sí | No | No |
| Carrito y solicitar consumo | Sí | Sí, sede asignada | Solo estadía propia ACTIVA |
| Registrar pago de consumos | Sí | Sí, sede asignada | No |
| Anular venta impagada | Sí | Sí, sede asignada | No |
| Reportes | Todas las sedes | Solo sede asignada | No |
| Notificaciones técnicas/DLQ | Operador autorizado | No | No |

Cliente nunca envía un rol, propietario o precio que el servidor acepte como autoridad. Los guards de Angular son UX; el backend aplica todas las restricciones.

En el cierre de estadía, A/E fijan `costoPenalidad` (0..precioInicial) y, si es > 0, `motivoPenalidad` (1..300); el panel muestra el desglose de la cotización y resalta la penalidad. La habitación con estadía vencida (ACTIVA con `fechaSalida` anterior a hoy Lima) se muestra con la etiqueta “estadía vencida sin cierre”; no se libera ni se cierra sola. El detalle de pantallas, formularios y estados del panel está en [`08-admin-web.md`](08-admin-web.md); este documento es la visión general.

Política operativa visible: check-in desde las 15:00 y check-out hasta las 12:00 (America/Lima). Una noche es la diferencia de días entre entrada y salida; no hay estancias por horas.

## Condición de presentación real

La aplicación deja de ser una demo y pasa a ser una presentación real. Estas reglas sustituyen a la marca de demostración en todas las pantallas.

- **Marca única:** la cadena se llama **Casa Sillar** en la landing, el panel, el área de cliente, los correos y los datos semilla. Se escribe igual en todas partes; no se usan variantes ni el prefijo “Hotel Demo”.
- **Sedes:** `lima-centro`, `miraflores` y `arequipa` conservan sus slugs porque son claves estables del contrato y de las pruebas; cambian su nombre comercial, descripción, dirección, teléfono y correo por datos verosímiles. Los slugs no se muestran al visitante salvo en la URL.
- **Datos ficticios verosímiles:** los nombres, direcciones, teléfonos, correos e imágenes describen una cadena plausible, pero no corresponden a una empresa real ni a personas reales. No se usan datos de terceros, documentos reales ni correos de dominio ajeno en los seeds.
- **Límite de veracidad:** la aplicación no afirma ser un negocio real en operación, no garantiza disponibilidad y no cobra en línea. La landing mantiene el aviso de que no constituye una reserva; el pie y la página de privacidad aclaran que los datos son de una presentación. «Presentación» no autoriza a inventar métricas, testimonios, premios, certificaciones ni reseñas.
- **Seeds como contenido inicial:** los datos semilla son el contenido con el que arranca la presentación. Se pueden editar desde el panel como cualquier registro; no son intocables y no se vuelven a sembrar si ya existen.
- **Textos de la landing:** los textos de las pantallas siguen siendo propios de la app y viven en el código, con i18n preparado para es-PE. No se sirven desde el backend.

### Imágenes

- **Procedencia:** propias o con licencia que permita el uso en el repositorio. Se descargan y se guardan en el repo; nunca se enlazan en caliente desde un banco de imágenes, un acortador ni un CDN de terceros.
- **Registro:** cada imagen tiene su ficha en un archivo de créditos del repositorio con autor, origen, licencia y fecha de descarga. Una imagen sin ficha no entra.
- **Formato:** relación fija declarada en el contenedor para no producir saltos de layout, `object-fit: cover`, `alt` descriptivo y fallback neutro propio si la imagen no carga. Un fallo de imagen nunca altera el precio, la tarifa ni el layout.
- **Sede:** la landing muestra la fotografía de la sede cuando el contrato la entrega; si no, cae al fallback sin dejar el hueco vacío.
- **Habitación y producto:** la galería curada de la categoría viene de las habitaciones publicadas; el producto muestra su imagen en el catálogo, el carrito y las ventas. Las URLs se validan con la misma regla del contrato común (https de la lista permitida o `/assets/`, ≤ 500).

## Formatos de UI (obligatorios)

- Moneda: `S/ 70.00` (símbolo `S/`, dos decimales); separador de miles con coma: `S/ 1,234.50`. La API sigue en ISO-8601 y PEN; los pipes de Angular convierten.
- Fechas: `dd/MM/yyyy`; timestamps `dd/MM/yyyy HH:mm`; locale **es-PE** registrado con `registerLocaleData` en ambas apps.
- Estados de venta: mostrar `estado` (pago) y `estadoOperacion` por separado; un 202 se muestra como “pendiente”, nunca como éxito definitivo.
- “Monto recibido ahora” es la etiqueta del campo `montoRecibido` del cierre; el total histórico del alojamiento se rotula como tal.

## Textos y assets (public-web)

- **Inicio (hero):** “Casa Sillar en Lima y Arequipa” + propuesta de valor en una frase (“Catálogo, sedes y contacto; sin reservas ni pagos en línea”) + llamada a contacto.
- **Sedes/categorías:** descripciones de los seeds de la cadena; tarifa con el texto obligatorio “Desde S/ 70.00 por noche. Consulta disponibilidad con recepción. No constituye una reserva.”
- **Contacto:** datos de la cadena mostrados como texto cuando no son operables; un enlace `tel:`, `mailto:` o WhatsApp solo se activa si el dato funciona. Los datos de la presentación no apuntan a buzones ni teléfonos de terceros.
- **Privacidad:** borrador informativo sin promesas de cumplimiento legal; declara que la cadena y sus datos son de una presentación y enlaza a contacto.
- **Imágenes:** fotografías propias o licenciadas guardadas en `/assets/`, con `alt` explícito y ficha de créditos. Las sedes, habitaciones y productos muestran su imagen; el fallback neutro cubre el hueco cuando una imagen no carga.

Los textos viven en la app (i18n preparado para es-PE); no se sirven desde el backend.

## UX y pruebas mínimas

- Público sin token recibe 200 en las cuatro rutas públicas y 401 en las privadas.
- Desconectar reception no impide ver el catálogo; la LP depende solo de hotel.
- Hotel inactivo/categoría sin habitaciones publicadas → no aparece; detalle → 404.
- Menú público no muestra botones de reservar/pagar ni disponibilidad en vivo.
- Error de red: mensaje y reintento, no inventar precios ni mostrar datos del panel como fallback.
- Responsive, navegación por teclado, labels, contraste y alt en imágenes.
- HTTP mediante Gateway; el código de la LP no contiene secretos ni APIs internas.
- Caché pública máxima 60 s; solo contenido público, nunca respuestas autenticadas.
- La landing se percibe completa: toda sede tiene fotografía o fallback, toda categoría con habitaciones publicadas tiene al menos una imagen, y ninguna ficha queda con un hueco gris sin explicación.
- La marca es la misma en landing, panel, área de cliente, correos y seeds; ninguna pantalla conserva la marca de demostración.
