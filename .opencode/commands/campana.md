---
description: "Ejecuta la campaña de planes: implementa, revisa y mergea los planes que queden de docs/plans, uno detrás de otro."
---

# Campaña de planes

Ejecuta la campaña de los planes de [`docs/plans/`](../../docs/plans) que queden sin implementar.

Sigue el agente `orquestador` de `.opencode/agents/orquestador.md` de `.opencode/agents/orquestador.md`, punto a punto.

Contexto adicional para esta ejecución:

- Estás en OpenCode, con una sesión viva. Todo el trabajo pesado ocurre en sesiones hijas, así
  que tu contexto no se agota aunque la campaña sea larga.
- La persona puede estar mirando. Si te interrumpe con una pregunta, contéstale y sigue donde
  estabas: el bucle vuelve a pedir el siguiente plan a `scripts/proximo-plan.py` y retoma solo.
- **No cierres la campaña en seco.** Cuando no quede ningún plan listo, escribe el resumen final
  que pide el punto 9 del orquestador: cerrados, bloqueados y decisiones de contrato.

Si la persona te da argumentos, úsalos como límites: un nombre de plan acota la campaña a ese
plan, un número acota a cuántos planes se ejecutan en esta pasada.

**Argumentos:** $ARGUMENTS