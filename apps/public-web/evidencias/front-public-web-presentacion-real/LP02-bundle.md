# LP02 — sin parámetros de disponibilidad ni endpoints privados

Comandos ejecutados en `apps/public-web` tras `npm run build`.

## Bundle

```bash
grep -rn "fecha=\|ocupacion=" dist/public-web/browser --include=*.js
# (sin resultados)

grep -rnoE "/api/(auth|persona|hotel|piso|categoria|habitacion|recepcion|producto|venta|reportes)/" \
  dist/public-web/browser --include=*.js
# (sin resultados)

grep -rnoE "/api/public/[a-z/{}\$-]*" dist/public-web/browser --include=*.js | sort -u
# dist/public-web/browser/chunk-CjRtIW8w.js:1:/api/public/hoteles
# dist/public-web/browser/chunk-CjRtIW8w.js:1:/api/public/hoteles/${encode
# dist/public-web/browser/main-XUP26SBY.js:5:/api/public/
```

Las tres únicas rutas del bundle son las cuatro públicas del contrato (la de detalle de categoría se
construye con la misma base `/api/public/hoteles`). No aparecen rutas administrativas, ni el panel,
ni tokens.

## Peticiones de la landing

Registro de red de `LP01-red.md`: las únicas llamadas al Gateway son

```
GET /api/public/hoteles?page=0&size=20
GET /api/public/hoteles/lima-centro
GET /api/public/hoteles/lima-centro/categorias?page=0&size=20
GET /api/public/hoteles/lima-centro/categorias/1
```

Ninguna lleva `?fecha=`, `?ocupacion=`, cabecera `Authorization` ni apunta a un puerto de servicio.
