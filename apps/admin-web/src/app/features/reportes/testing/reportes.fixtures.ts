import type {
  DashboardReporte,
  MetaReporte,
  ReporteBajoStockRow,
  ReporteCobrosRow,
  ReporteOcupacionRow,
  ReporteVentasRow,
} from '../../../core/models/reportes.models';

/** Fixtures de los reportes: mismos campos y nombres que `admin-web.yaml`. */

export function metaReporte(overrides: Partial<MetaReporte> = {}): MetaReporte {
  return {
    updatedAt: '2026-10-04T15:00:00Z',
    consistency: 'EVENTUAL',
    degraded: false,
    bootstrapComplete: true,
    correlationId: 'cid-reporte',
    page: 0,
    size: 20,
    totalElements: 1,
    ...overrides,
  };
}

export function dashboardReporte(
  overrides: Partial<DashboardReporte> = {}
): DashboardReporte {
  return {
    habitacionesOcupadas: 1,
    habitacionesDisponibles: 5,
    productosBajoStock: 3,
    ingresosHoy: 77,
    moneda: 'PEN',
    ...overrides,
  };
}

export function reporteVentas(overrides: Partial<ReporteVentasRow> = {}): ReporteVentasRow {
  return {
    idProducto: 1,
    nombreProducto: 'Agua 500ml',
    cantidadTotal: 12,
    totalIngresado: 42,
    ...overrides,
  };
}

export function reporteOcupacion(
  overrides: Partial<ReporteOcupacionRow> = {}
): ReporteOcupacionRow {
  return {
    idHabitacion: 101,
    numeroHabitacion: '101',
    descripcionCategoria: 'Individual',
    vecesAlquilada: 22,
    ...overrides,
  };
}

export function reporteCobros(overrides: Partial<ReporteCobrosRow> = {}): ReporteCobrosRow {
  return {
    idRecepcion: 1,
    numeroHabitacion: '101',
    nombreCliente: 'Cliente Uno',
    totalAlojamiento: 70,
    totalConsumos: 7,
    totalGeneral: 77,
    medioPagoAdelanto: 'EFECTIVO',
    medioPagoCierre: 'TARJETA',
    fechaCierre: '2026-10-20T16:03:23Z',
    ...overrides,
  };
}

export function reporteBajoStock(
  overrides: Partial<ReporteBajoStockRow> = {}
): ReporteBajoStockRow {
  return {
    idProducto: 1,
    nombreProducto: 'Agua 500ml',
    cantidad: 3,
    precio: 3.5,
    estado: true,
    ...overrides,
  };
}

export function envelopeReporte<T>(
  data: T,
  meta: Partial<MetaReporte> = {}
): { success: boolean; message: string; data: T; meta: MetaReporte } {
  return { success: true, message: 'OK', data, meta: metaReporte(meta) };
}

export function apiErrorReporte(code: string, correlationId = 'cid-reporte-error') {
  return {
    success: false,
    message: 'Error de negocio',
    data: null,
    error: { code, fields: {} },
    meta: {
      correlationId,
      consistency: 'EVENTUAL',
      degraded: false,
      bootstrapComplete: true,
      updatedAt: null,
    },
  };
}
