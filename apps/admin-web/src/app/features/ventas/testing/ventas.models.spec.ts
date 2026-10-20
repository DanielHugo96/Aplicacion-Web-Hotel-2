import { describe, expect, it } from 'vitest';
import type {
  AjusteResultado,
  CarritoItem,
  Movimiento,
  MovimientoAjuste,
  MovimientoVenta,
  OperacionVenta,
  Producto,
  ProductoAlta,
  ProductoUpdate,
  Venta,
  VentaAlta,
  VentaDetalle,
} from '../../../core/models/ventas.models';
import {
  carritoItem,
  movimientoAjuste,
  movimientoVenta,
  operacionVenta,
  producto,
  productoAlta,
  venta,
  ventaDetalle,
} from './ventas.fixtures';

/**
 * Contrato de los modelos: si un campo cambia de nombre o de tipo en
 * `admin-web.yaml`, estas aserciones fallan. Los fixtures están tipados, así
 * que además el compilador rechaza cualquier campo que falte o sobre.
 */
describe('modelos del módulo de productos y ventas', () => {
  it('Producto conserva los campos del schema', () => {
    const p: Producto = producto();
    expect(Object.keys(p).sort()).toEqual(
      [
        'cantidad',
        'detalle',
        'estado',
        'fechaCreacion',
        'idHotel',
        'idProducto',
        'imagenUrl',
        'nombre',
        'precio',
        'productoVersion',
        'sku',
        'umbralBajo',
        'version',
      ].sort()
    );
    expect(typeof p.idProducto).toBe('number');
    expect(typeof p.cantidad).toBe('number');
    expect(typeof p.estado).toBe('boolean');
  });

  it('ProductoAlta y ProductoUpdate no aceptan campos calculados', () => {
    const alta: ProductoAlta = productoAlta();
    expect(Object.keys(alta).sort()).toEqual(
      [
        'cantidadInicial',
        'detalle',
        'idHotel',
        'imagenUrl',
        'nombre',
        'precio',
        'sku',
        'umbralBajo',
      ].sort()
    );
    expect('version' in alta).toBe(false);
    expect('cantidad' in alta).toBe(false);

    const update: ProductoUpdate = {
      nombre: 'Agua 500ml',
      detalle: 'Botella',
      imagenUrl: '/assets/productos/agua.png',
      precio: 3.5,
      umbralBajo: 5,
      estado: true,
      productoVersion: 2,
    };
    expect(Object.keys(update).sort()).toEqual(
      ['detalle', 'estado', 'imagenUrl', 'nombre', 'precio', 'productoVersion', 'umbralBajo'].sort()
    );
    expect('cantidad' in update).toBe(false);
  });

  it('AjusteResultado conserva ajusteId, cantidad y version', () => {
    const r: AjusteResultado = {
      ajusteId: '40000000-0000-4000-8000-000000000004',
      cantidad: 17,
      version: 4,
    };
    expect(Object.keys(r).sort()).toEqual(['ajusteId', 'cantidad', 'version']);
  });

  it('Movimiento discrimina VENTA y AJUSTE por tipo', () => {
    const v: MovimientoVenta = movimientoVenta();
    const a: MovimientoAjuste = movimientoAjuste();
    const movimientos: Movimiento[] = [v, a];
    expect(Object.keys(v).sort()).toEqual(
      [
        'cantidad',
        'createdAt',
        'estado',
        'idHotel',
        'idVenta',
        'precioUnitario',
        'tipo',
        'updatedAt',
      ].sort()
    );
    expect(Object.keys(a).sort()).toEqual(
      ['ajusteId', 'createdAt', 'delta', 'idHotel', 'motivo', 'tipo'].sort()
    );
    expect(movimientos.map((m) => m.tipo)).toEqual(['VENTA', 'AJUSTE']);
    expect(v.estado).toBe('DESCONTADO');
    expect(a.delta).toBe(-3);
  });

  it('Venta y VentaDetalle conservan los importes del backend', () => {
    const v: Venta = venta();
    const d: VentaDetalle = ventaDetalle();
    expect(Object.keys(v).sort()).toEqual(
      [
        'detalles',
        'estado',
        'estadoOperacion',
        'fechaCreacion',
        'idCliente',
        'idHotel',
        'idRecepcion',
        'idVenta',
        'medioPago',
        'operationId',
        'paidAt',
        'total',
        'version',
      ].sort()
    );
    expect(Object.keys(d).sort()).toEqual(
      ['cantidad', 'idDetalleVenta', 'idProducto', 'nombreProducto', 'precioUnitario', 'subTotal'].sort()
    );
    expect(v.total).toBe(7);
    expect(d.subTotal).toBe(7);
  });

  it('VentaAlta es JSON cerrado: sin idHotel, idCliente, precios ni totales', () => {
    const body: VentaAlta = {
      idRecepcion: 1001,
      estado: 'PENDIENTE',
      medioPago: null,
      detalles: [{ idProducto: 1, cantidad: 2 }],
    };
    expect(Object.keys(body).sort()).toEqual(
      ['detalles', 'estado', 'idRecepcion', 'medioPago'].sort()
    );
    expect(Object.keys(body.detalles[0]).sort()).toEqual(['cantidad', 'idProducto']);
    const serializado = JSON.stringify(body);
    for (const prohibido of ['idHotel', 'idCliente', 'precioUnitario', 'subTotal', 'total', 'estadoOperacion']) {
      expect(serializado).not.toContain(prohibido);
    }
  });

  it('CarritoItem y OperacionVenta conservan sus campos', () => {
    const c: CarritoItem = carritoItem();
    const o: OperacionVenta = operacionVenta();
    expect(Object.keys(c).sort()).toEqual(
      [
        'cantidad',
        'fechaRegistro',
        'idCarrito',
        'idHotel',
        'idPersona',
        'idProducto',
        'idRecepcion',
        'imagenUrl',
        'nombreProducto',
        'precioUnitario',
        'version',
      ].sort()
    );
    expect(Object.keys(o).sort()).toEqual(
      ['estado', 'estadoOperacion', 'idVenta', 'operationId', 'tipo'].sort()
    );
    expect(o.tipo).toBe('VENTA');
  });
});
