# Evidencia de las nueve alertas

- `00-alertas-en-limpio-inicial.*`: captura del paso 2 sobre la demo recién reseteada (commit
  `fa08672`), con las nueve filas en `no`.
- `readiness-caida.json` / `readiness-recuperada.json`: paso 15, sonda fallida > 1min y recuperación.
- `15-limpieza-dlt-dlq.json`: descarte de los mensajes de prueba de DLT y DLQ con su contenido.
- `16-alertas-en-limpio.*`: revalidación tras el descarte; solo «Reporting degradado» sigue en `1`
  y queda registrado en `17-hallazgo.txt` y en `IA-qa-runbooks-03`.
- `alertas.*`: captura intermedia con condiciones de simulacro (DLT > 0) usada para comprobar el
  disparo; no es el estado limpio.
