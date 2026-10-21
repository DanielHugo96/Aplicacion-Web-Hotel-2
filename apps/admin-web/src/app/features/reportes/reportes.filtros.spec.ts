import { describe, expect, it } from 'vitest';
import {
  MAX_DIAS_RANGO,
  MENSAJES_RANGO,
  hoyLima,
  isoMedianocheLima,
  rangoIso,
  sumarDias,
  validarFechas,
  validarRangoIso,
} from './reportes.filtros';

/** Construye el ISO esperado sin escribir la hora a mano: la misma zona del contrato. */
function isoLima(fecha: string): string {
  const partes = new Intl.DateTimeFormat('en-US', {
    timeZone: 'America/Lima',
    timeZoneName: 'longOffset',
  }).formatToParts(new Date(`${fecha}T00:00:00Z`));
  const offset = partes.find((p) => p.type === 'timeZoneName')?.value?.replace('GMT', '') ?? '-05:00';
  return `${fecha}T00:00:00${offset}`;
}

describe('filtros de reportes (ISO-8601 con offset de Lima)', () => {
  it('convierte un día a medianoche de America/Lima con su offset', () => {
    expect(isoMedianocheLima('2026-09-01')).toBe(isoLima('2026-09-01'));
    expect(isoMedianocheLima('2026-10-20')).toBe(isoLima('2026-10-20'));
  });

  it('rechaza fechas imposibles y formatos que no son yyyy-mm-dd', () => {
    expect(isoMedianocheLima('2026-02-30')).toBeNull();
    expect(isoMedianocheLima('01/09/2026')).toBeNull();
    expect(isoMedianocheLima('')).toBeNull();
  });

  it('suma días sin depender de la zona local (cruza fin de mes y año)', () => {
    expect(sumarDias('2026-12-31', 1)).toBe('2027-01-01');
    expect(sumarDias('2026-02-28', 1)).toBe('2026-03-01');
    expect(sumarDias('2026-10-20', -30)).toBe('2026-09-20');
  });

  it('hoyLima devuelve yyyy-mm-dd de la zona del contrato', () => {
    expect(hoyLima(new Date('2026-10-21T02:30:00Z'))).toBe('2026-10-20');
    expect(hoyLima(new Date('2026-10-21T06:00:00Z'))).toBe('2026-10-21');
  });

  it('el rango semiabierto incluye el último día elegido', () => {
    const rango = rangoIso('2026-10-01', '2026-10-20');
    expect(rango).not.toBeNull();
    expect(rango!.inicio).toBe(isoLima('2026-10-01'));
    expect(rango!.fin).toBe(isoLima('2026-10-21'));
  });

  it('exige inicio y fin', () => {
    expect(validarFechas('', '')).toEqual({
      inicio: MENSAJES_RANGO.inicioRequerido,
      fin: MENSAJES_RANGO.finRequerido,
    });
  });

  it('rechaza inicio posterior o igual al fin', () => {
    const errores = validarFechas('2026-10-20', '2026-10-01');
    expect(errores['inicio']).toBe(MENSAJES_RANGO.orden);
  });

  it('acepta justo 366 días y rechaza un día más con el mensaje exacto', () => {
    const inicio = '2026-01-01';
    const hasta = sumarDias(inicio, MAX_DIAS_RANGO - 1);
    expect(validarFechas(inicio, hasta)).toEqual({});

    const fuera = sumarDias(inicio, MAX_DIAS_RANGO);
    expect(validarFechas(inicio, fuera)['inicio']).toBe(
      'El rango no puede superar 366 días'
    );
  });

  it('ocupación exige medianoche de Lima en el ISO enviado', () => {
    const inicio = isoLima('2026-09-01');
    const fin = isoLima('2026-10-01');
    expect(validarRangoIso(inicio, fin, true)).toEqual({});

    const conHora = '2026-09-01T03:00:00-05:00';
    expect(validarRangoIso(conHora, fin, true)['inicio']).toBe(MENSAJES_RANGO.medianoche);
    // El mismo instante con otro offset tampoco es medianoche de Lima.
    expect(validarRangoIso('2026-09-01T08:00:00Z', fin, true)['inicio']).toBe(
      MENSAJES_RANGO.medianoche
    );
  });

  it('exige ISO-8601 con offset explícito', () => {
    expect(validarRangoIso('2026-09-01', '2026-10-01')['inicio']).toBe(
      MENSAJES_RANGO.inicioInvalido
    );
    expect(validarRangoIso('2026-09-01T00:00:00-05:00', '2026-10-01')['fin']).toBe(
      MENSAJES_RANGO.finInvalido
    );
  });
});
