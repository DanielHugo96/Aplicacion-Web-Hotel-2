---
description: "Ejecuta la campaña de planes: implementa y mergea los planes que queden de docs/plans, uno detrás de otro."
---

# Campaña de planes

Ejecuta la campaña de los planes de [`docs/plans/`](../../docs/plans) que queden sin implementar.

Sigue el agente `orquestador` de `.opencode/agents/orquestador.md`, punto a punto.

Contexto adicional para esta ejecución:

- Estás en OpenCode, con una sesión viva. Todo el trabajo pesado ocurre en sesiones hijas, así
  que tu contexto no se agota aunque la campaña sea larga.
- La persona puede estar mirando. Si te interrumpe con una pregunta, contéstale y sigue donde
  estabas: el bucle vuelve a pedir el siguiente plan a `scripts/proximo-plan.py` y retoma solo.
- **Si `main` está roto, no lo arregles tú.** Llama a `planificador-arreglos` para que genere el
  plan de arreglo, mergea su PR `docs:` y ejecútalo antes de retomar los planes normales (punto 8
  del orquestador).
- **No cierres la campaña en seco.** Cuando no quede ningún plan listo, escribe el resumen final
  que pide el punto 9 del orquestador: cerrados, arreglos, bloqueados y decisiones de contrato.
- **Si DeepSeek se queda sin cuota o sin créditos, no pares.** Pasa al ejecutor de respaldo
  `ejecutor` (MiniMax M3.1 Flash Free) según el punto 5.1 del orquestador. Si la sesión entera es la
  que no responde, quien está delante cambia el modelo de la sesión a
  `opencode-go/minimax-m3.1-flash-free#high` y relanza `/campana`: el plan a medias se retoma solo.

Si la persona te da argumentos, úsalos como límites: un nombre de plan acota la campaña a ese
plan, un número acota a cuántos planes se ejecutan en esta pasada.

**Argumentos:** $ARGUMENTS