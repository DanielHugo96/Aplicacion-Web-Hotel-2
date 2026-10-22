# LP01 — registro de red de la landing contra el Gateway

Capturado en Chrome DevTools con el build de producción servido en `http://localhost:4200`
(la URL que el Gateway tiene en su `allowedOrigins`) y el Gateway en `http://localhost:8080`.

`/sedes`

```
reqid=126 GET http://localhost:8080/api/public/hoteles?page=0&size=20 [200]
reqid=127 GET http://localhost:4200/assets/sedes/lima-centro-fachada.jpg [200]
reqid=128 GET http://localhost:4200/assets/sedes/miraflores-fachada.jpg [200]
reqid=129 GET http://localhost:4200/assets/sedes/arequipa-fachada.jpg [200]
```

`/sedes/lima-centro`

```
GET http://localhost:8080/api/public/hoteles/lima-centro [200]
GET http://localhost:8080/api/public/hoteles/lima-centro/categorias?page=0&size=20 [200]
GET http://localhost:4200/assets/sedes/lima-centro-fachada.jpg [200]
GET http://localhost:4200/assets/habitaciones/lima-centro-individual-1.jpg [200]
GET http://localhost:4200/assets/habitaciones/lima-centro-doble-1.jpg [200]
```

`/sedes/lima-centro/habitaciones/1`

```
reqid=138 GET http://localhost:8080/api/public/hoteles/lima-centro/categorias/1 [200]
reqid=139 GET http://localhost:4200/assets/habitaciones/lima-centro-individual-1.jpg [200]
reqid=140 GET http://localhost:4200/assets/habitaciones/lima-centro-individual-2.jpg [200]
reqid=141 GET http://localhost:4200/assets/habitaciones/lima-centro-individual-3.jpg [200]
```

Observaciones:

- Ninguna petición sale del Gateway (`localhost:8080`): no hay puertos de servicio en el registro.
- Ninguna lleva `?fecha=` ni `?ocupacion=`; los únicos parámetros son `page` y `size` (y `capacidad`
  cuando se filtra), que son los del contrato público.
- Todas las imágenes se sirven desde el propio origen (`localhost:4200/assets/...`); ninguna es un
  hotlink.
