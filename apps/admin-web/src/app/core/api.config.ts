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
} as const;
