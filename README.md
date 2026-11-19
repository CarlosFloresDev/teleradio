# TeleRadio

Mod de Fabric para **Minecraft 26.3**: radios, televisores y pantallas gigantes que reproducen **YouTube**, radios online
y archivos de audio/video (mp3, mp4...). **Todos los jugadores cercanos escuchan y ven lo mismo al mismo tiempo.**

## Instalar
Fabric Loader 0.19.5+ y Fabric API para 26.3, y `build/libs/teleradio-0.2.0.jar` en `mods`
(en el servidor **y** en cada cliente).

**La primera vez** que abras el control de una radio o tele, el mod ofrece descargar 3 programas gratuitos
(~175 MB, una sola vez, a `.minecraft/teleradio/bin`):
- **yt-dlp**: saca el audio y el video de YouTube;
- **ffmpeg**: decodifica el audio y el video;
- **Deno**: lo necesita yt-dlp para YouTube.

Cada jugador que quiera escuchar/ver debe descargarlos en su PC (es un botón). En Linux/Mac hay que instalarlos
con el sistema (`yt-dlp`, `ffmpeg`, `deno`).

## Bloques
| Bloque | Receta | Qué hace |
|---|---|---|
| **Radio** | lingote de hierro, 4 tablones, bloque musical, redstone | solo audio; salen notas musicales |
| **Televisor** | tablones, panel de vidrio, bloque musical, redstone | tele antigua con video en la pantalla |
| **Pantalla Gigante** | 6 hierro, vidrio, polvo de piedra luminosa, redstone | panel para la pared; la imagen se agranda de **1x1 hasta 16x9 bloques** (crece a la derecha y hacia arriba) |
| **Control Remoto** | hierro + redstone + botón de piedra | apunta a cualquiera de los 3 (hasta 32 bloques) y abre su control |
| **Audífonos** | hierro, cuero, hierro / 2 bloques musicales | música **mientras caminas, solo para ti**; se pueden poner en la cabeza |
| **Parlante Portátil** | cobre, bloque musical, cobre / cobre, redstone, cobre | música que **te sigue y escuchan los de cerca** (hasta 32 bloques) |

**Audífonos y parlante**: funcionan desde cualquier parte del inventario (o puestos). Click derecho abre el mismo control
(buscar, cola, pausa, volumen...). Si llevas los dos, suena por el último que usaste. Si los sueltas, se calla.

## El control (click derecho al bloque)
- **Buscar**: pega un link (YouTube, radio online, mp3/mp4) o escribe el nombre de una canción y se busca en YouTube.
  - **▶ Poner** reproduce ahora.
  - **+ Cola** la agrega a la lista (hasta 20). Al terminar una, empieza la siguiente sola.
- **-10 s / +10 s**, **Pausa / Seguir**, **Siguiente**, **Detener** y **Repetir** (la canción vuelve a la lista).
- **Volumen** (0-100 %) y **Alcance**: de 4 a 64 bloques hasta donde se escucha (el sonido sale del bloque, en 3D).
- Pantalla gigante: **Ancho** (1-16) y **Alto** (1-9).
- Usa el volumen de **Tocadiscos/Bloques musicales** de las opciones de sonido de Minecraft.

## Límites (para que el juego no se ponga lento)
- Video hasta 480p a 20 cuadros por segundo.
- Como mucho 4 reproducciones a la vez cerca de ti, y solo 2 de ellas con video. Suenan las más cercanas.
- Se reproduce solo si estás cerca: alcance + 8 bloques, o 48 bloques para ver un video.
- Solo se aceptan links web (http/https), nunca archivos del PC.

## Desarrollo
```
gradlew-d.cmd build              # jar en build/libs
gradlew-d.cmd runGametest        # controles, límites, avance automático, sincronización
gradlew-d.cmd runClientGameTest  # prueba real: reproduce Big Buck Bunny de YouTube en una pantalla 7x4 y un televisor
python tools/gen_assets.py       # texturas, modelos, recetas, traducciones
```
Para las pruebas se usan los programas ya descargados en `D:\DevTools\media-tools` (`-Dteleradio.bin`).

Cómo funciona por dentro:
1. El servidor solo guarda el link y en qué segundo va la pista.
2. Cada cliente usa yt-dlp para obtener las direcciones del audio y del video.
3. Dos ffmpeg empiezan en ese segundo: uno decodifica el audio (PCM mono, para sonido 3D) y otro el video (RGBA a 20 fps).
4. El audio entra al motor de sonido de Minecraft mediante un mixin, y el video se sube a una textura que dibuja el renderizador del bloque.
