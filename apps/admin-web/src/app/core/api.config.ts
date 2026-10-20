/**
 * Base del Gateway (http://localhost:8080), configurable por entorno.
 * Todo el HTTP de la app pasa por el Gateway: nunca puertos de servicios,
 * brokers ni bases de datos. Si hace falta reconfigurar, se edita este
 * único módulo (o se provee otro valor con APP_CONFIG en app.config).
 */
export const API_BASE_URL = 'http://localhost:8080';

export const API_PATHS = {
  authLogin: '/api/auth/login',
  authRegistro: '/api/auth/registro',
  authMe: '/api/auth/me',
  authPassword: '/api/auth/password',

  personas: '/api/persona',
  personaListar: '/api/persona/listar',
  personaBuscar: (id: number | string) => `/api/persona/buscar/${id}`,
  personaRegistrar: '/api/persona/registrar',
  personaActualizar: (id: number | string) => `/api/persona/actualizar/${id}`,
  personaEliminar: (id: number | string) => `/api/persona/eliminar/${id}`,

  tiposPersona: '/api/tipopersona',
  tipoPersonaListar: '/api/tipopersona/listar',
  tipoPersonaBuscar: (id: number | string) => `/api/tipopersona/buscar/${id}`,
  tipoPersonaActualizar: (id: number | string) => `/api/tipopersona/actualizar/${id}`,

  hoteles: '/api/hotel',
  hotelListar: '/api/hotel/listar',
  hotelBuscar: (id: number | string) => `/api/hotel/buscar/${id}`,
  hotelRegistrar: '/api/hotel/registrar',
  hotelActualizar: (id: number | string) => `/api/hotel/actualizar/${id}`,
  hotelEliminar: (id: number | string) => `/api/hotel/eliminar/${id}`,

  pisos: '/api/piso',
  pisoListar: '/api/piso/listar',
  pisoBuscar: (id: number | string) => `/api/piso/buscar/${id}`,
  pisoRegistrar: '/api/piso/registrar',
  pisoActualizar: (id: number | string) => `/api/piso/actualizar/${id}`,
  pisoEliminar: (id: number | string) => `/api/piso/eliminar/${id}`,

  categorias: '/api/categoria',
  categoriaListar: '/api/categoria/listar',
  categoriaBuscar: (id: number | string) => `/api/categoria/buscar/${id}`,
  categoriaRegistrar: '/api/categoria/registrar',
  categoriaActualizar: (id: number | string) => `/api/categoria/actualizar/${id}`,
  categoriaEliminar: (id: number | string) => `/api/categoria/eliminar/${id}`,

  habitaciones: '/api/habitacion',
  habitacionListar: '/api/habitacion/listar',
  habitacionBuscar: (id: number | string) => `/api/habitacion/buscar/${id}`,
  habitacionRegistrar: '/api/habitacion/registrar',
  habitacionActualizar: (id: number | string) => `/api/habitacion/actualizar/${id}`,

  estadosHabitacion: '/api/estadohabitacion',
  estadoHabitacionListar: '/api/estadohabitacion/listar',
  estadoHabitacionBuscar: (id: number | string) => `/api/estadohabitacion/buscar/${id}`,
  estadoHabitacionActualizar: (id: number | string) => `/api/estadohabitacion/actualizar/${id}`,
} as const;
