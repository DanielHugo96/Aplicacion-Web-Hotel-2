import { describe, expect, it } from 'vitest';
import {
  CarritoItem,
  OperacionVenta,
  PasswordChange,
  Perfil,
  Producto,
  Recepcion,
  Venta,
  VentaAlta,
} from './cliente.models';
import {
  carritoItem,
  operacion,
  PERSONA_CLIENTE,
  producto,
  recepcion,
  venta,
} from '../../features/cliente/testing/cliente.fixtures';

/**
 * Contrato congelado de los schemas del área de cliente. Los fixtures están
 * declarados con `satisfies` en `cliente.fixtures.ts`: si un campo cambia de
 * nombre, de tipo o de obligatoriedad, TypeScript falla al compilar. Estas
 * pruebas fijan además el conjunto exacto de claves en tiempo de ejecución.
 */
function claves(obj: object): string[] {
  return Object.keys(obj).sort();
}

const CAMPOS_RECEPCION = [
  'idRecepcion',
  'idHotel',
  'idCliente',
  'idHabitacion',
  'numero',
  'categoriaNombre',
  'pisoNombre',
  'detalleHabitacion',
  'precioHabitacion',
  'nombre',
  'apellido',
  'tipoDocumento',
  'documento',
  'correo',
  'fechaEntrada',
  'fechaSalida',
  'fechaSalidaConfirmacion',
  'precioInicial',
  'adelanto',
  'medioPagoAdelanto',
  'precioRestante',
  'totalPagado',
  'costoPenalidad',
  'motivoPenalidad',
  'observacion',
  'estado',
  'estadoEstadia',
  'archivada',
  'vencida',
  'version',
  'closureId',
  'cleaningCycleId',
  'cierre',
].sort();

const CAMPOS_PRODUCTO = [
  'idProducto',
  'sku',
  'nombre',
  'detalle',
  'imagenUrl',
  'idHotel',
  'precio',
  'cantidad',
  'umbralBajo',
  'estado',
  'fechaCreacion',
  'version',
  'productoVersion',
].sort();

const CAMPOS_CARRITO = [
  'idCarrito',
  'idRecepcion',
  'idPersona',
  'idHotel',
  'idProducto',
  'nombreProducto',
  'precioUnitario',
  'imagenUrl',
  'cantidad',
  'fechaRegistro',
  'version',
].sort();

const CAMPOS_VENTA = [
  'idVenta',
  'idRecepcion',
  'idCliente',
  'idHotel',
  'total',
  'estado',
  'estadoOperacion',
  'operationId',
  'medioPago',
  'version',
  'fechaCreacion',
  'paidAt',
  'detalles',
].sort();

describe('modelos del área de cliente (admin-web.yaml)', () => {
  it('Recepcion conserva los campos y tipos del schema', () => {
    const r: Recepcion = recepcion();
    expect(claves(r)).toEqual(CAMPOS_RECEPCION);
    expect(typeof r.estadoEstadia).toBe('string');
    expect(r.medioPagoAdelanto).toBe('EFECTIVO');
    expect(r.cierre).toBeNull();
  });

  it('Producto conserva precio y stock informativos del backend', () => {
    const p: Producto = producto();
    expect(claves(p)).toEqual(CAMPOS_PRODUCTO);
    expect(p.precio).toBe(3.5);
    expect(p.cantidad).toBe(20);
  });

  it('CarritoItem conserva la cantidad y el precio unitario', () => {
    const c: CarritoItem = carritoItem();
    expect(claves(c)).toEqual(CAMPOS_CARRITO);
    expect(c.cantidad).toBe(2);
    expect(c.precioUnitario).toBe(3.5);
  });

  it('Venta conserva las dos píldoras, operationId y líneas inmutables', () => {
    const v: Venta = venta();
    expect(claves(v)).toEqual(CAMPOS_VENTA);
    expect(claves(v.detalles[0])).toEqual(
      [
        'idDetalleVenta',
        'idProducto',
        'nombreProducto',
        'cantidad',
        'precioUnitario',
        'subTotal',
      ].sort()
    );
    expect(v.medioPago).toBeNull();
    expect(v.estadoOperacion).toBe('CONFIRMADA');
  });

  it('VentaAlta es JSON cerrado sin precios ni IDs derivados', () => {
    const alta = {
      idRecepcion: 1001,
      estado: 'PENDIENTE',
      detalles: [{ idProducto: 1, cantidad: 2 }],
    } satisfies VentaAlta;
    expect(claves(alta)).toEqual(['detalles', 'estado', 'idRecepcion'].sort());
    expect(claves(alta.detalles[0])).toEqual(['cantidad', 'idProducto'].sort());
  });

  it('OperacionVenta trae operationId, tipo, estado y estadoOperacion', () => {
    const op: OperacionVenta = operacion();
    expect(claves(op)).toEqual(
      ['operationId', 'idVenta', 'tipo', 'estado', 'estadoOperacion'].sort()
    );
  });

  it('Perfil y PasswordChange mantienen sus campos exactos', () => {
    const perfil = {
      nombre: 'Cliente',
      apellido: 'Uno',
      fotoUrl: 'https://example.test/foto.jpg',
    } satisfies Perfil;
    expect(claves(perfil)).toEqual(['apellido', 'fotoUrl', 'nombre'].sort());

    const cambio = {
      claveActual: 'clave-actual',
      claveNueva: 'clave-nueva-larga',
    } satisfies PasswordChange;
    expect(claves(cambio)).toEqual(['claveActual', 'claveNueva'].sort());
  });

  it('Persona coincide con el schema de identidad', () => {
    expect(claves(PERSONA_CLIENTE)).toEqual(
      [
        'idPersona',
        'tipoDocumento',
        'documento',
        'nombre',
        'apellido',
        'correo',
        'fotoUrl',
        'idTipoPersona',
        'tipoPersona',
        'sedes',
        'estado',
        'fechaCreacion',
        'version',
      ].sort()
    );
  });
});
