# Atenas para Minecraft 1.20.1 (Forge 47.x) — mod de cliente

Todo en un solo jar (solo cliente, el servidor no lo necesita):

- **Menú principal de Atenas** (paleta violeta, logo, panorama de Minecraft). Botón ★ para entrar directo al servidor del pack.
- **Cosméticos**: 20 capas de tela segmentada (6 animadas), 9 alas (plumas que ondulan en el aire), 9 sombreros y 6 mascotas (perro, husky, dálmata, gato, gato negro y zorro). Interruptor "Animadas" en la pantalla de cosméticos. Los de otros jugadores solo se dibujan a menos de 40 bloques. Se eligen desde el menú, la pausa o una tecla (Controles → Atenas). Los ven todos los que tengan el mod.
- **Servidor con estrella** ★ siempre primero en Multijugador (lo toma del `server` del pack.json).
- **Mascotas**: te siguen caminando (corren si te alejas, saltan escalones, se sientan si te quedas quieto o te agachas, mueven la cola y las orejas). Solo existen en tu pantalla: el servidor no sabe de ellas ni chocan con nada. Código en `cosmetics/PetRender.java` (modelo y animación compartidos con el cliente 1.8.9 vía `PetSpec` y `PetBrain`).
- **Amigos y chat** (Shift+Tab / J), ahora con el icono del pack que juega cada amigo.
- **Avisos propios** (solicitudes, mensajes, amigos en línea): tarjeta con la cara del jugador, entrada con rebote, barra de tiempo y salida suave, en la partida y sobre cualquier menú. Reemplazan los avisos de Minecraft. Llegan casi al instante porque el juego consulta `/poll` cada 4 s y solo baja el estado completo (`/sync`) cuando algo cambió.

## Importante
- Los cosméticos se comparten por el Worker social: **hay que volver a desplegarlo** (`social-worker/`, `npx wrangler deploy`), trae las rutas nuevas `/cosmetics/set` y `/cosmetics/get`.
- El servidor y el icono los manda el launcher 1.6.0 (`-Datenas.server`, `-Datenas.pack`, `-Datenas.kind`). Sin launcher nuevo no hay ★.
- Compilar: igual que antes (GitHub Actions, tag `v1.0.0`; sin tag se compila `0.0.0-dev`). Luego Atenas Admin → pack 1.20.1 → Mis .jar.
- El Worker social debe tener `/poll` y el campo `pet` de `/cosmetics/set` (ya están en `social-worker/worker.mjs`).
- **Esta versión nunca se compiló dentro de Minecraft/Forge**: la parte sin Minecraft (`PetSpec`, `PetBrain`, `WingMesh`, `CapeWave`) tiene pruebas en `pruebas-java/` y pasan; el resto se revisó solo por sintaxis. Pruébalo en el juego antes de repartirlo (ver lista en `PENDIENTE.md`).

## Capas y alas (regenerar texturas)
`python3 tools/gen_wings.py` (Pillow + numpy) regenera las alas y 4 capas animadas (llamas, aurora, mareas, pulso) (12 fotogramas, tira vertical) y sus iconos. Para una capa animada nueva: PNG de 256x1536 (12 fotogramas de 256x128) en `capes/`, icono `cape_<id>.png` y una línea `addAnim(...)` en `Cosmetics.java`.
