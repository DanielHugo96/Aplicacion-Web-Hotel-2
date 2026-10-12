# Guía Git para trabajar en equipo (desde cero)

> Si nunca has usado Git en equipo, lee esto una vez completa. Luego solo usa el [resumen para copiar y pegar](#-resumen-rápido-para-copiar-y-pegar).

**La idea en 10 segundos:**
- `main` es la versión oficial y que funciona. **Nadie trabaja directo ahí.**
- Cada uno trabaja en **su propia rama** (una copia para su tarea).
- Cuando terminas, pides unir tu trabajo con un **Pull Request (PR)**. Alguien lo revisa y recién ahí se une a `main`.

**Palabras que debes conocer:**
- **Rama (branch):** una copia del proyecto donde puedes trabajar sin romper lo de los demás.
- **main:** la rama principal, la oficial.
- **Commit:** guardar tus cambios con un mensaje que explique qué hiciste.
- **Push:** subir tus commits de tu compu a GitHub.
- **Pull:** bajar a tu compu lo último que está en GitHub.
- **PR (Pull Request):** pedir en GitHub que revisen tu rama y la unan a `main`.
- **Merge:** unir una rama con otra (se hace en GitHub al aceptar el PR).

---

## 1. Ponte al día (siempre antes de empezar algo nuevo)

Antes de crear tu rama, asegúrate de tener lo último de `main`.

```bash
git switch main
git pull
```

¿Qué hace cada cosa?
- `git switch main` → te cambia a la rama `main`.
- `git pull` → descarga lo último de GitHub a tu compu.

> Haz esto **siempre**. Si no lo haces, trabajas sobre algo viejo y luego chocan los cambios.

---

## 2. Crea tu rama (una rama = una tarea)

Cada tarea tiene su propia rama. Ejemplo: si vas a hacer el login, tu rama se llama `login-jwt`.

```bash
git switch -c nombre-de-lo-que-estas-haciendo
```

Ejemplo real:
```bash
git switch -c login-jwt
```

Reglas para el nombre:
- Todo en minúsculas.
- Usa guiones `-` en vez de espacios. Ej: `crud-productos`, `fix-error-login`.
- Corto y claro: que se entienda qué estás haciendo.

> `switch -c` significa: crea la rama **y** cámbiate a ella al mismo tiempo.

---

## 3. Trabaja y sube tus cambios

Trabaja normal en tu código. Cuando avances algo que funcione, guárdalo:

```bash
git add .
git commit -m "feat: agrega login con JWT"
git push -u origin nombre-de-tu-rama
```

¿Qué hace cada cosa?
- `git add .` → prepara todos tus archivos modificados para guardarlos.
- `git commit -m "..."` → los guarda con un mensaje. El mensaje debe explicar qué hiciste.
- `git push -u origin nombre-de-tu-rama` → sube tu rama a GitHub. El `-u` solo se usa **la primera vez**. Después solo usa `git push`.

Ejemplo real:
```bash
git add .
git commit -m "feat: agrega login con JWT"
git push -u origin login-jwt
```

**¿Cómo escribo el mensaje del commit?**
Empieza con una palabra + dos puntos:
- `feat:` si agregaste algo nuevo. Ej: `feat: agrega registro de usuarios`
- `fix:` si arreglaste un error. Ej: `fix: corrige error al iniciar sesión`
- `docs:` si solo cambiaste documentación. Ej: `docs: actualiza README`
- `style:` si solo cambiaste formato sin tocar la lógica.

---

## 4. Abre un PR a `main` en GitHub (para unir tu trabajo)

Tú **no** unes tu rama directo. Pides que la revisen:

1. Entra al repo en GitHub. Te saldrá un botón amarillo que dice **Compare & pull request**. Dale clic.
2. Verifica que diga: `base: main` <- `compare: nombre-de-tu-rama`.
3. Escribe qué hiciste (1 o 2 líneas bastan).
4. Clic en **Create pull request**.
5. Avísale a tu compañero por WhatsApp/Discord para que lo revise.
6. Si te pide cambios, los haces en tu rama, haces `commit` + `push`, y el PR se actualiza solo.
7. Cuando te lo aprueban, se hace **Merge** en GitHub.

> Nunca hagas merge de tu propio PR sin que alguien lo revise.

---

## 5. Borra tu rama y vuelve a `main` (después del merge)

Cuando tu PR ya fue aceptado y mergeado en GitHub, tu rama ya no sirve. Bórrala y actualízate:

```bash
git switch main
git pull
git branch -d nombre-de-la-rama
git push -d origin nombre-de-la-rama
```

Ejemplo real:
```bash
git switch main
git pull
git branch -d login-jwt
git push -d origin login-jwt
```

¿Qué hace cada cosa?
- `git switch main` → vuelves a `main`.
- `git pull` → bajas el `main` ya actualizado (con tu trabajo incluido).
- `git branch -d ...` → borra la rama en tu compu.
- `git push -d origin ...` → borra la rama en GitHub.

> La `d` es de *delete* (borrar). Solo borra ramas que **ya fueron mergeadas**.

---

## Extra: traer lo último de `main` mientras trabajas en tu rama

Si estás varios días en tu rama y tus compañeros ya unieron cosas a `main`, tu rama se queda vieja. Actualízala sin salirte de ella:

```bash
git pull origin main
```

Hazlo cada 1 o 2 días. Así evitas que al final todo choque (conflictos gigantes).

Si al hacer eso te sale un **conflicto** (Git te dice `CONFLICT`), no te asustes:
1. Abre el archivo marcado, busca `<<<<<<<`, `=======`, `>>>>>>>`.
2. Borra lo que no sirva y deja el código correcto.
3. Guarda y luego:
```bash
git add .
git commit -m "fix: resuelve conflicto con main"
git push
```

---

## 🚫 Lo que NUNCA debes hacer

- ❌ Trabajar directo en `main`.
- ❌ `git push` directo a `main`.
- ❌ `git push --force` (borra el trabajo de otros).
- ❌ Mergear tu propio PR sin revisión.
- ❌ Borrar una rama que aún no fue mergeada.

---

## ⚡ Resumen rápido para copiar y pegar

**Empezar tarea nueva:**
```bash
git switch main
git pull
git switch -c nombre-de-tu-tarea
```

**Guardar y subir (todos los días):**
```bash
git add .
git commit -m "feat: describe lo que hiciste"
git push
# La primera vez: git push -u origin nombre-de-tu-tarea
```

**Actualizar tu rama con lo nuevo de main:**
```bash
git pull origin main
```

**Terminar (después del merge en GitHub):**
```bash
git switch main
git pull
git branch -d nombre-de-tu-tarea
git push -d origin nombre-de-tu-tarea
```
