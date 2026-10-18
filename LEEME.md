# Atenas para Minecraft 1.20.1 (Forge 47.x) — mod de cliente

Todo en un solo jar (solo cliente, el servidor no lo necesita):

- **Menú principal de Atenas** (paleta violeta, logo, panorama de Minecraft). Botón ★ para entrar directo al servidor del pack.
- **Cosméticos** (los mismos que el cliente 1.8.9): 32 capas de tela HD (26 animadas), 23 alas (9 de plumas y 10 de membrana), 12 sombreros en malla 3D HD y 7 mascotas (perro, husky, dálmata, gato, gato negro y zorro). Interruptor "Animadas" en la pantalla de cosméticos. Los de otros jugadores solo se dibujan a menos de 40 bloques. Se eligen desde el menú, la pausa o una tecla (Controles → Atenas). Los ven todos los que tengan el mod.
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

## Cambios de interfaz (menú, ventana y amigos)
- **Menú principal:** ahora reemplaza *cualquier* `TitleScreen` (también el que traen otros mods del pack) y se corre al final de la cola de eventos. Antes solo cambiaba la clase exacta de Minecraft, por eso el menú de libro seguía saliendo.
- **Ventana:** nombre `Atenas | Minecraft 1.20.1` y logo de Atenas como icono (`WindowBrand.java`), igual que el cliente 1.8.9.
- **Amigos y Cosméticos:** mismo fondo de neón que el menú, panel de cristal con esquinas cortadas, cabecera con estado, filas con avatar/estado/badges, pie con atajos y estado de verificación de la cuenta.

## Pantalla de carga propia y ficha de cuenta (sin probar en el juego)
- `AtenasLoading.java` envuelve el overlay de carga del arranque (Mojang/Forge) y lo tapa con la pantalla de Atenas (logo, barra, %). Si algo falla, devuelve el original. El launcher ya no abre una ventana de carga encima para este pack.
- Menú principal: ficha de la cuenta abajo a la derecha con la cara del skin (o el Steve/Alex por defecto) y el estado.
- La versión del jar sale del tag de git (`v1.2.3`); sin tag es `0.0.0-dev`.

## Cosméticos HD (portados del cliente 1.8.9)
- **Capas:** texturas HD del cliente (hoja de 1024x512 por fotograma, tira de 1024x6144 en las animadas) con el mismo UV que ya usaba `CapeMesh` (la hoja 64x32 escalada x16), así que no cambió nada del modelo. Iconos a 128x128.
  Peso: el jar crece unos 28 MB por las capas. VRAM: ~33 MB por cada capa distinta visible; si da problemas, bajar la resolución con `tools/gen_capes_hd.py`.
- **Sombreros (12: halo, corona, galera, gato, cuernos, mago, auriculares, conejo, gorra + pirata, vikingo y laurel):** ahora son mallas `.atm` en `assets/atenassocial/models/hats/` (los mismos archivos del cliente) con atlas HD en `textures/cosmetics/hats/`.
  Código: `cosmetics/HatMesh.java` (carga el .atm y emite los quads con `VertexConsumer`; flags: 1 brilla, 2 pulsa, 4 gira, 8 suave), `cosmetics/AtenasTypes.java` (tipo de dibujo aditivo sin culling para las piezas que brillan) y `HatLayer` en `CosmeticRender.java`.
  Los sombreros del catálogo (descargados, UV de cajas 64x64) siguen con el modelo de cajas. Los de otros jugadores solo se dibujan a menos de 24 bloques (cada malla trae miles de vértices).
- **TexFilter:** Minecraft carga las texturas de entidad con filtro NEAREST y sin mipmaps; `cosmetics/TexFilter.java` pone filtro suave + mipmaps una vez por textura (capas, sombreros e iconos). En las capas animadas los mipmaps se limitan al nivel 3 para que no se mezclen fotogramas.
- **Generadores:** `tools/gen_capes_hd.py` (+ `hd_*.py`) y `tools/gen_hats.py` (+ `hatlib.py`, `hatpaint.py`) son los mismos del cliente: `python3 tools/gen_hats.py src/main/resources/assets/atenassocial [ids...]`. **No ejecutar `gen_capes.py` ni `gen_capes_anim.py`**: escriben capas a baja resolución y pisarían las HD.
- Alas y mascotas ya eran idénticas en los dos mods (mismos archivos).
- Nombres de capas, alas y sombreros iguales a los del cliente. Capa lejana (más de 16 bloques): menos filas y sin cantos, como en el cliente. Se quitaron las 9 texturas viejas sin registrar.
- Sin probar en el juego: el Java nuevo pasa el análisis de sintaxis pero no se compiló contra Forge. Ver en juego que los brillos del halo/auriculares/cuernos se ven bien y que el laurel y el vikingo no se cruzan con la capa/alas.
