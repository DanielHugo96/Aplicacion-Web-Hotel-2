# Landing pública y panel de administración

## Objetivo y límites

`public-web` presenta la cadena de hoteles al público. `admin-web` contiene login y funciones privadas según rol; incluye el área ya existente de cliente para consultar su estadía y pedir productos. Son dos builds Angular; no se duplican los microservicios.

## Landing: sí puede

| Pantalla/acción | Datos y contrato |
|---|---|
| Inicio | Presentación estática, servicios generales y enlaces a sedes |
| Sedes | GET /api/public/hoteles |
| Detalle de sede | GET /api/public/hoteles/{slug} |
| Tipos de habitación por sede | GET /api/public/hoteles/{slug}/categorias |
| Detalle de tipo/categoría | GET /api/public/hoteles/{slug}/categorias/{idCategoria} |
| Fotografías y tarifa “desde” | Solo imágenes aprobadas y precio mínimo de habitaciones publicadas de esa categoría |
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

Texto de tarifa: “Desde S/ 70.00 por noche. Consulta disponibilidad con recepción. No constituye una reserva.” Las fotografías y los textos deben ser propios o licenciados. Datos semilla siempre marcados como demostración.

## Panel: matriz de acceso

| Función | ADMIN | EMPLEADO | CLIENTE |
|---|---|---|---|
| Crear personal / roles / asignar sedes | Sí | No | No |
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

## UX y pruebas mínimas

- Público sin token recibe 200 en las cuatro rutas públicas y 401 en las privadas.
- Desconectar reception no impide ver el catálogo; la LP depende solo de hotel.
- Hotel inactivo/categoría sin habitaciones publicadas → no aparece; detalle → 404.
- Menú público no muestra botones de reservar/pagar ni disponibilidad en vivo.
- Error de red: mensaje y reintento, no inventar precios ni mostrar datos del panel como fallback.
- Responsive, navegación por teclado, labels, contraste y alt en imágenes.
- HTTP mediante Gateway; el código de la LP no contiene secretos ni APIs internas.
- Caché pública máxima 60 s; solo contenido público, nunca respuestas autenticadas.
