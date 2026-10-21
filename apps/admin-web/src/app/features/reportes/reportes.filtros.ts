/**
 * Validación y construcción de los filtros de `/app/reportes`.
 *
 * El contrato (`reporting/api.md` § REST legacy) exige `inicio`/`fin`
 * ISO-8601 **con offset**, `inicio < fin`, intervalo semiabierto
 * `[inicio, fin)` de 366 días como máximo y, solo en ocupación, medianoche de
 * `America/Lima`. Las pantallas usan `input[type=date]`: el día se interpreta
 * en hora de Lima y se envía con su offset, de modo que el rango nunca depende
 * de la zona horaria de la máquina que abre el panel.
 *
 * Los mensajes de validación son los de `08-admin-web.md` §3; el rango
 * excedido usa el texto exacto «El rango no puede superar 366 días».
 */

export const MAX_DIAS_RANGO = 366;
export const ZONA_LIMA = 'America/Lima';

export const MENSAJES_RANGO = {
  inicioRequerido: 'Ingresa la fecha de inicio',
  finRequerido: 'Ingresa la fecha de fin',
  inicioInvalido: 'La fecha de inicio no es válida',
  finInvalido: 'La fecha de fin no es válida',
  orden: 'La fecha de inicio debe ser anterior a la fecha de fin',
  excedido: 'El rango no puede superar 366 días',
  medianoche: 'Ocupación solo admite fechas en medianoche de America/Lima',
} as const;

export type ErroresRango = Record<string, string>;

/** Offset de Lima (`-05:00`) para el instante dado; Lima no tiene horario de verano. */
export function offsetLima(instante: Date = new Date()): string {
  const partes = new Intl.DateTimeFormat('en-US', {
    timeZone: ZONA_LIMA,
    timeZoneName: 'longOffset',
  }).formatToParts(instante);
  const nombre = partes.find((p) => p.type === 'timeZoneName')?.value ?? 'GMT-05:00';
  const match = /GMT([+-]\d{2}:\d{2})/.exec(nombre);
  return match ? match[1] : '-05:00';
}

/** `yyyy-mm-dd` → ISO-8601 a medianoche de Lima con su offset. */
export function isoMedianocheLima(fecha: string): string | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(fecha ?? '');
  if (!match) return null;
  const anio = Number(match[1]);
  const mes = Number(match[2]);
  const dia = Number(match[3]);
  const comprobacion = new Date(Date.UTC(anio, mes - 1, dia));
  if (
    comprobacion.getUTCFullYear() !== anio ||
    comprobacion.getUTCMonth() !== mes - 1 ||
    comprobacion.getUTCDate() !== dia
  ) {
    return null;
  }
  // El offset de Lima no cambia con la fecha: se calcula con el instante del día.
  return `${fecha}T00:00:00${offsetLima(comprobacion)}`;
}

/** Suma días a un `yyyy-mm-dd` sin depender de la zona horaria local. */
export function sumarDias(fecha: string, dias: number): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(fecha);
  if (!match) return fecha;
  const d = new Date(Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3])));
  d.setUTCDate(d.getUTCDate() + dias);
  return d.toISOString().slice(0, 10);
}

/** Fecha de hoy en Lima (`yyyy-mm-dd`), sin depender de la zona de la máquina. */
export function hoyLima(ahora: Date = new Date()): string {
  const partes = new Intl.DateTimeFormat('en-CA', {
    timeZone: ZONA_LIMA,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(ahora);
  return partes;
}

function esMedianocheLima(iso: string): boolean {
  // Medianoche de Lima = las 05:00 UTC del mismo día.
  const instante = new Date(iso);
  if (Number.isNaN(instante.getTime())) return false;
  const partes = new Intl.DateTimeFormat('en-CA', {
    timeZone: ZONA_LIMA,
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(instante);
  return partes === '00:00:00';
}

/**
 * Valida un rango ISO ya construido.
 *
 * @param medianocheLima exigido por el reporte de ocupación
 * @returns campos con su mensaje; vacío si el rango es válido
 */
export function validarRangoIso(
  inicio: string,
  fin: string,
  medianocheLima = false
): ErroresRango {
  const errores: ErroresRango = {};
  const desde = new Date(inicio);
  const hasta = new Date(fin);
  const tieneOffset = /T\d{2}:\d{2}(:\d{2}(\.\d+)?)?(Z|[+-]\d{2}:\d{2})$/.test(inicio ?? '');
  const tieneOffsetFin = /T\d{2}:\d{2}(:\d{2}(\.\d+)?)?(Z|[+-]\d{2}:\d{2})$/.test(fin ?? '');
  if (!inicio) {
    errores['inicio'] = MENSAJES_RANGO.inicioRequerido;
  } else if (!tieneOffset || Number.isNaN(desde.getTime())) {
    errores['inicio'] = MENSAJES_RANGO.inicioInvalido;
  }
  if (!fin) {
    errores['fin'] = MENSAJES_RANGO.finRequerido;
  } else if (!tieneOffsetFin || Number.isNaN(hasta.getTime())) {
    errores['fin'] = MENSAJES_RANGO.finInvalido;
  }
  if (Object.keys(errores).length > 0) return errores;

  if (!desde.getTime() || !hasta.getTime() || desde.getTime() >= hasta.getTime()) {
    errores['inicio'] = MENSAJES_RANGO.orden;
    return errores;
  }
  const dias = (hasta.getTime() - desde.getTime()) / 86_400_000;
  if (dias > MAX_DIAS_RANGO) {
    errores['inicio'] = MENSAJES_RANGO.excedido;
    return errores;
  }
  if (medianocheLima && (!esMedianocheLima(inicio) || !esMedianocheLima(fin))) {
    errores['inicio'] = MENSAJES_RANGO.medianoche;
  }
  return errores;
}

/**
 * Valida los días elegidos en la pantalla. `hasta` es inclusivo para la
 * persona usuaria: se envía como `fin` a medianoche del día siguiente.
 */
export function validarFechas(
  inicio: string,
  hasta: string,
  medianocheLima = false
): ErroresRango {
  const errores: ErroresRango = {};
  if (!inicio) errores['inicio'] = MENSAJES_RANGO.inicioRequerido;
  if (!hasta) errores['fin'] = MENSAJES_RANGO.finRequerido;
  if (Object.keys(errores).length > 0) return errores;
  const isoInicio = isoMedianocheLima(inicio);
  const isoFin = isoMedianocheLima(sumarDias(hasta, 1));
  if (!isoInicio) return { inicio: MENSAJES_RANGO.inicioInvalido };
  if (!isoFin) return { fin: MENSAJES_RANGO.finInvalido };
  return validarRangoIso(isoInicio, isoFin, medianocheLima);
}

/** Rango ISO `[inicio, fin)` para enviar al Gateway; `null` si las fechas no son válidas. */
export function rangoIso(
  inicio: string,
  hasta: string
): { inicio: string; fin: string } | null {
  const isoInicio = isoMedianocheLima(inicio);
  const isoFin = isoMedianocheLima(sumarDias(hasta, 1));
  if (!isoInicio || !isoFin) return null;
  return { inicio: isoInicio, fin: isoFin };
}
