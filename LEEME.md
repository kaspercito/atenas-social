# Atenas para Minecraft 1.20.1 (Forge 47.x) — mod de cliente

Todo en un solo jar (solo cliente, el servidor no lo necesita):

- **Menú principal de Atenas** (paleta violeta, logo, panorama de Minecraft). Botón ★ para entrar directo al servidor del pack.
- **Cosméticos**: 14 capas, 9 alas (7 plumas con aleteo en el aire), 9 sombreros. Se eligen desde el menú, la pausa o una tecla (Controles → Atenas). Los ven todos los que tengan el mod.
- **Servidor con estrella** ★ siempre primero en Multijugador (lo toma del `server` del pack.json).
- **Amigos y chat** (Shift+Tab / J), ahora con el icono del pack que juega cada amigo.

## Importante
- Los cosméticos se comparten por el Worker social: **hay que volver a desplegarlo** (`social-worker/`, `npx wrangler deploy`), trae las rutas nuevas `/cosmetics/set` y `/cosmetics/get`.
- El servidor y el icono los manda el launcher 1.6.0 (`-Datenas.server`, `-Datenas.pack`, `-Datenas.kind`). Sin launcher nuevo no hay ★.
- Compilar: igual que antes (GitHub Actions, tag `v1.1.0`). Luego Atenas Admin → pack 1.20.1 → Mis .jar.
