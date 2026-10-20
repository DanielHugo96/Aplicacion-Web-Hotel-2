import { InjectionToken } from '@angular/core';

/**
 * Configuración pública de la landing. Se puede sobreescribir en tiempo de
 * ejecución con `globalThis.__PUBLIC_WEB_CONFIG__` sin recompilar el bundle.
 * Nunca contiene secretos: solo la base del Gateway y el enlace al panel.
 */
export interface PublicWebConfig {
  gatewayUrl: string;
  panelUrl: string;
}

declare global {
  // eslint-disable-next-line no-var
  var __PUBLIC_WEB_CONFIG__: Partial<PublicWebConfig> | undefined;
}

export const DEFAULT_PUBLIC_WEB_CONFIG: PublicWebConfig = {
  gatewayUrl: 'http://localhost:8080',
  panelUrl: 'http://localhost:4300',
};

export function resolvePublicWebConfig(): PublicWebConfig {
  const runtime = globalThis.__PUBLIC_WEB_CONFIG__ ?? {};
  return {
    ...DEFAULT_PUBLIC_WEB_CONFIG,
    ...runtime,
  };
}

export const PUBLIC_WEB_CONFIG = new InjectionToken<PublicWebConfig>(
  'PUBLIC_WEB_CONFIG',
  {
    providedIn: 'root',
    factory: resolvePublicWebConfig,
  },
);

/** Base única del Gateway: la landing nunca apunta a puertos de servicios. */
export const GATEWAY_BASE_URL = new InjectionToken<string>('GATEWAY_BASE_URL', {
  providedIn: 'root',
  factory: () => resolvePublicWebConfig().gatewayUrl.replace(/\/+$/, ''),
});

/** Enlace sobrio al login del panel; no embebe el panel ni tokens. */
export const PANEL_URL = new InjectionToken<string>('PANEL_URL', {
  providedIn: 'root',
  factory: () => resolvePublicWebConfig().panelUrl.replace(/\/+$/, ''),
});
