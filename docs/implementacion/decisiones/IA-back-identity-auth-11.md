# IA-back-identity-auth-11 — Bloqueo del plan por reincidencia del revisor

- **Plan:** `back-identity-auth`
- **Paso:** cierre del plan (revisión previa al merge; regla 6.11.3 de [`../../plans/README.md`](../../plans/README.md))
- **Nivel:** bloqueo por revisión, no una decisión de nivel 1, 2 ni 3
- **Fecha:** 2026-10-18

## Qué se encontró

El plan se implementó por completo y pasó sus verificaciones canónicas
(`bash scripts/mvn.sh -f services/pom.xml clean verify` → `BUILD SUCCESS` de los nueve módulos
con 67 pruebas verdes en `identity-service`; `python scripts/validar-planes.py` → 0 errores,
0 avisos; `python scripts/validar-contratos.py --base origin/main` → contratos solo ampliados).
El caso `SEC02` pasa por el Gateway con `400 VALIDATION_ERROR`, el `meta.correlationId` propagado
y 0 filas en `identity_db.persona`.

Aun así, el agente revisor devolvió **`RECHAZADO` dos veces**, y el segundo veredicto no era
discutible: los motivos se comprobaron uno a uno sobre el árbol de trabajo y todos eran ciertos.

Primera ronda de revisión (cuatro motivos):

1. El §6 del plan afirmaba 64 pruebas en verde e incluía un `RabbitTopologyTest` que **no existe**
   en `identity-service` (solo en notification, inventory, hotel y reception). La cifra salía de un
   reporte de Surefire obsoleto que había quedado en `target/`.
2. El §5 decía que se había retocado la descripción del 400 de `PUT /api/auth/password` en
   `identity.yaml` y el §7 decía que `docs/contracts/**` no se había tocado. Dos afirmaciones
   mutuamente excluyentes, repetidas además en `IA-back-identity-auth-04.md` y `-06.md`.
3. Defecto real de código: `DemoDataSeeder.sembrarSedes()` quedaba **fuera** de la guarda de
   `DEMO_PASSWORD`, y `persona_sede.id_persona` tiene FK a `persona`. Sin la clave de demo no se
   sembraban personas pero sí sedes, la transacción revertía y el servicio **no arrancaba**
   (reproducido: `violates foreign key constraint "persona_sede_id_persona_fkey"`).
4. El revisor no pudo ejecutar los comandos canónicos en su sesión, así que no había evidencia
   ejecutable pegada en el plan.

Segunda ronda de revisión, sobre lo corregido:

1. El §6 («Alcance de archivos de la rama») declara **9** ficheros de bitácora y **63** archivos
   totales, cuando ya son **10** y **64**: la decisión `IA-back-identity-auth-10.md` se escribió
   después de esa tabla.
2. El §7 dice «Las **nueve** decisiones están en `../implementacion/decisiones/`» cuando son diez.
3. La viñeta del outbox en el §6 se autocontradice: «tras el registro, **una fila**» y, dos
   cláusulas después, «reintento idempotente → siguen siendo **2 filas**».

Los tres motivos de la segunda ronda son de coherencia documental dentro del propio plan, no de
código. La corrección de la primera ronda sí tocó código y era la que importaba: el defecto del
seeder está arreglado, cubierto por cuatro pruebas nuevas en `DemoDataSeederTests` y verificado en
arranque real (`Started IdentityServiceApplication`, readiness 200, `persona` 0 / `persona_sede` 0).

## Qué se decidió

**Bloquear el plan y pasar al siguiente**, sin un tercer intento de corrección.

- PR de implementación **#58** (`back-identity-auth` → `main`): abierto y **cerrado sin mergear**.
- La rama `back-identity-auth` queda en `origin` con los 22 commits, para no perder el trabajo.

## Por qué

La regla **6.11.3** de [`../../plans/README.md`](../../plans/README.md) es explícita: «Hay un
intento de corrección si el revisor devuelve `RECHAZADO`; si reincide, el PR se cierra sin
mergear, se registra el bloqueo en `docs/implementacion/decisiones/` y la campaña sigue con el
siguiente plan». El intento de corrección se hizo y el revisor reincidió.

La regla 6.11.3 es además el punto 8 del orquestador (`no insistas`), y la separación que protege
la regla 9 —que quien implementa no sea quien aprueba— impide que el orquestador cierre el plan
arreglando él mismo la documentación que el revisor rechaza. Arreglarla y volver a pasar la
revisión sería una tercera ronda, que es exactamente lo que la norma corta.

## Alternativas descartadas

- **Tercer intento de corrección.** Prohibido por 6.11.3: el intento de corrección es **uno**.
- **Que el orquestador arregle el plan a mano.** El orquestador tiene permiso de escritura sobre
  `docs/plans/<plan>.md` y `docs/implementacion/**`, y los tres motivos de la segunda ronda caben
  en cinco líneas. Se descartó: la revisión la da otro agente, y aprobar un arreglo hecho por
  quien orquesta rompe la separación implementación/revisión de la regla 9.
- **Revertir el plan a `en curso` y dejarlo sin rama.** Haría que `scripts/proximo-plan.py`
  devolviera `back-identity-auth` otra vez en la siguiente vuelta y la campaña se quedaría girando
  sobre el mismo plan. Además incumple el §6.9 (el plan pasa a `cerrado` en el último PR del plan).
- **Mergear «porque el código está verde».** Un `RECHAZADO` reincidente no se anula por criterio
  propio: es justo lo que la regla 6.11.3 existe para impedir.

## A quién afecta

- `back-identity-auth` (T4): bloqueado. Sus dependientes en [`../orden.md`](../orden.md) §2
  —`back-identity-personas` (T6), `back-hotel-catalogo` (T5), `front-admin-web-base` (T5),
  `back-notification-worker` (T4) y todo lo que cuelga de ellos— no se desbloquean por esta rama:
  un plan cerrado solo en una rama no desbloquea a nadie ([`../README.md`](../README.md) §4).
- **Ningún contrato cambia.** `identity.yaml` no se modificó en la rama.
- Quien retome el plan: el trabajo está en `origin/back-identity-auth`, el código con sus 67 pruebas
  verdes, y solo faltan las cinco líneas de coherencia documental del §6 y el §7 del plan. Con eso
  corregido, el plan se puede reabrir y mergear.

## Cómo se desbloquea

1. Corregir en `docs/plans/back-identity-auth.md` los recuentos de la tabla de alcance (9 → 10 y
   63 → 64), la palabra «nueve» del §7 (→ diez) y la viñeta del outbox del §6, para que diga un
   único recuento coherente.
2. Revisar que el §6 y el §7 sigan coincidiendo con el diff real de la rama.
3. Reabrir el plan, volver a pasar el revisor y mergear el PR.

Este archivo documenta por qué se paró: **el bloqueo es documental, no funcional**. Esa distinción
importa para quien lo lea dentro de un mes y vea un `RECHAZADO` en un plan con las pruebas verdes.