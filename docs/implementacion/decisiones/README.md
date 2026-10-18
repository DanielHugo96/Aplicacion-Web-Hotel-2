# Bitácora de decisiones de la IA

Un archivo por decisión que tuvo que tomar un agente durante la ejecución de un plan.

Existe por una razón concreta: la norma del proyecto dice que ante ambigüedad hay que **parar y
preguntar** ([`../../plans/README.md`](../../plans/README.md) §6.6). La campaña automática no
puede preguntar, porque sus agentes no tienen esa herramienta. Lo que hace este directorio es
sustituir la pregunta por un registro: la IA decide, escribe por qué aquí, y sigue. La traza
queda para que una persona pueda revisar después qué se decidió y, si no estaba de acuerdo,
corregirlo.

Un archivo sin registro sería una decisión invisible, que es exactamente lo que la norma
prohíbe.

## Formato

Un archivo por plan y decisión: `IA-<plan>-NN.md`, con `NN` de dos dígitos empezando en `01`.
Ejemplos: `IA-back-identity-auth-01.md`, `IA-front-admin-web-base-03.md`.

Si una decisión afecta a varios planes, el nombre es `IA-<plan-del-que-salio>-NN.md` y el cuerpo
indica a qué planes afecta.

```markdown
# IA-<plan>-NN — <título corto>

- **Plan:** `back-identity-auth`
- **Paso:** 7 de la sección 4. Pasos
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-18

## Qué se encontró

<El conflicto o la ambigüedad concreta. Cita los documentos y apartados que chocan, con lo que
dice cada uno. Si es un caso donde la spec no decide nada, dilo así: "la spec no dice NADA de X".>

## Qué se decidió

<La decisión, en una frase.>

## Por qué

<La regla que aplicaste. Normalmente la jerarquía de fuentes de
[`../README.md`](../README.md) §1: norma > plan > catálogo > registro. Si la decisión es
"la spec no dice nada", explica qué criterio usaste y por qué es el menos invasivo.>

## Alternativas descartadas

<Qué otras opciones había y por qué no. Esto es lo más valioso del archivo: evita que la misma
decisión se vuelva a tomar al revés más adelante.>

## A quién afecta

<Planes, contratos, endpoints, eventos o servicios involucrados. Si la decisión tocaba un contrato
ya cerrado, indícalo aquí de forma destacada.>
```

## Niveles

| Nivel | Qué significa | ¿Se registra? |
|---|---|---|
| 1 | Resoluble leyendo la spec, los contratos o el código. | No |
| 2 | Contradicción entre documentos, o la spec no fija un detalle menor. | Sí |
| 3 | El paso exige inventar algo que la spec no fija, o tocar zona ajena. No se inventa. | Sí, como bloqueo |

Un nivel 3 **no** se resuelve aquí: se deja sin marcar el paso, el plan queda `en curso` y la
campaña pasa al plan siguiente. Este archivo documenta por qué se paró y qué haría falta para
desbloquearlo.

## Qué NO va aquí

- Decisiones de nivel 1. Si se registra todo, el directorio deja de ser útil.
- Razonamiento ni historia. Solo el conflicto, la decisión y el motivo.
- Decisiones que tomó una persona. Esas van en su PR, como siempre.