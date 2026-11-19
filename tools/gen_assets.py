"""Assets de TeleRadio: texturas, modelos 3D (radio, televisor, pantalla), estados, recetas, loot y traducciones.

Uso: python tools/gen_assets.py
"""
import json
import os
import random

from PIL import Image, ImageDraw

HERE = os.path.dirname(__file__)
RES = os.path.join(HERE, "..", "src", "main", "resources")
NS = "teleradio"
A = os.path.join(RES, "assets", NS)
D = os.path.join(RES, "data", NS)
TB = os.path.join(A, "textures", "block")
TE = os.path.join(A, "textures", "entity")
TI = os.path.join(A, "textures", "item")
for d in (TB, TE, TI):
    os.makedirs(d, exist_ok=True)


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def hexc(h, a=255):
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def ruido(img, var, seed):
    rnd = random.Random(seed)
    px = img.load()
    for y in range(img.size[1]):
        for x in range(img.size[0]):
            r, g, b, a = px[x, y]
            if a:
                d = rnd.randint(-var, var)
                px[x, y] = (max(0, min(255, r + d)), max(0, min(255, g + d)), max(0, min(255, b + d)), a)


# ================================================================== texturas
def madera(nombre, base, veta, seed):
    img = Image.new("RGBA", (16, 16), hexc(base))
    rnd = random.Random(seed)
    d = ImageDraw.Draw(img)
    for y in range(0, 16, 3):
        x0 = rnd.randint(0, 6)
        d.line([(x0, y + rnd.randint(0, 1)), (x0 + rnd.randint(5, 12), y + rnd.randint(0, 1))], fill=hexc(veta))
    ruido(img, 6, seed)
    img.save(os.path.join(TB, nombre + ".png"))


madera("radio_madera", "6b3e26", "4e2a19", 1)
madera("tv_madera", "8a5a34", "6b4226", 2)

# frente de la radio (16x12 usado en una cara de 12x9): rejilla de tela a la izquierda, dial a la derecha
img = Image.new("RGBA", (16, 16), hexc("6b3e26"))
d = ImageDraw.Draw(img)
d.rectangle([1, 1, 8, 10], fill=hexc("c9b48a"))
for x in range(1, 9):
    if x % 2 == 0:
        d.line([(x, 1), (x, 10)], fill=hexc("a8936a"))
d.rectangle([10, 2, 14, 5], fill=hexc("f0d58a"))
d.line([(10, 4), (14, 4)], fill=hexc("8a6a2a"))
d.point((12, 3), fill=hexc("c0392b"))
d.rectangle([10, 7, 11, 8], fill=hexc("d4af37"))
d.rectangle([13, 7, 14, 8], fill=hexc("d4af37"))
d.rectangle([0, 0, 15, 11], outline=hexc("3e2314"))
ruido(img, 4, 3)
img.save(os.path.join(TB, "radio_frente.png"))

img = Image.new("RGBA", (16, 16), hexc("b8bec6"))
ruido(img, 10, 4)
img.save(os.path.join(TB, "metal.png"))
img = Image.new("RGBA", (16, 16), hexc("d4af37"))
ruido(img, 12, 5)
img.save(os.path.join(TB, "perilla.png"))

# frente del televisor: u = 16 - x del modelo, v = 16 - y. Pantalla (x 4..14, y 2..11) -> u 2..12, v 5..14
img = Image.new("RGBA", (16, 16), hexc("8a5a34"))
d = ImageDraw.Draw(img)
d.rectangle([1, 4, 13, 15], fill=hexc("2b2b2b"))           # marco de la pantalla
d.rectangle([2, 5, 12, 14], fill=hexc("1e2a2e"))           # vidrio (el video se dibuja encima)
d.rectangle([13, 4, 15, 15], fill=hexc("6b4226"))          # panel de perillas (a la derecha del que mira)
for y in (6, 9):
    d.rectangle([14, y, 14, y + 1], fill=hexc("d4af37"))
for y in range(12, 15):
    d.point((14, y), fill=hexc("3e2314"))                  # parlante
ruido(img, 4, 6)
img.save(os.path.join(TB, "tv_frente.png"))
img = Image.new("RGBA", (16, 16), hexc("3a3a3a"))
ruido(img, 6, 7)
img.save(os.path.join(TB, "tv_atras.png"))

# pantalla moderna
img = Image.new("RGBA", (16, 16), hexc("1a1a1f"))
ruido(img, 3, 8)
img.save(os.path.join(TB, "pantalla_lado.png"))
img = Image.new("RGBA", (16, 16), hexc("101014"))
d = ImageDraw.Draw(img)
d.rectangle([0, 0, 15, 15], outline=hexc("2a2a30"))
for i in range(5):
    d.point((3 + i, 3 + i), fill=hexc("26262e"))
d.point((8, 15), fill=hexc("2ecc71"))
img.save(os.path.join(TB, "pantalla_frente.png"))

# texturas que usa el renderizador (pantalla apagada y marco)
img = Image.new("RGBA", (16, 16), hexc("0b0d10"))
d = ImageDraw.Draw(img)
for i in range(6):
    d.line([(2 + i, 1), (1, 2 + i)], fill=hexc("161a20"))
img.save(os.path.join(TE, "pantalla_apagada.png"))
img = Image.new("RGBA", (16, 16), hexc("15151a"))
ruido(img, 3, 9)
img.save(os.path.join(TE, "marco.png"))

# control remoto
img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
d = ImageDraw.Draw(img)
d.rounded_rectangle([5, 1, 10, 14], radius=2, fill=hexc("2b2b30"), outline=hexc("111114"))
d.rectangle([6, 2, 9, 3], fill=hexc("c0392b"))
for y in (5, 7, 9):
    d.point((6, y), fill=hexc("b8bec6"))
    d.point((8, y), fill=hexc("b8bec6"))
d.rectangle([6, 11, 9, 12], fill=hexc("1565c0"))
img.save(os.path.join(TI, "control_remoto.png"))

# icono del mod (64x64): televisor con boton de play
img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
d = ImageDraw.Draw(img)
d.line([(24, 14), (16, 2)], fill=hexc("b8bec6"), width=2)
d.line([(40, 14), (48, 2)], fill=hexc("b8bec6"), width=2)
d.rounded_rectangle([4, 14, 60, 58], radius=6, fill=hexc("8a5a34"), outline=hexc("3e2314"), width=2)
d.rounded_rectangle([9, 19, 47, 53], radius=4, fill=hexc("1565c0"))
d.polygon([(22, 27), (22, 45), (37, 36)], fill=hexc("ffffff"))
d.ellipse([50, 24, 56, 30], fill=hexc("d4af37"))
d.ellipse([50, 36, 56, 42], fill=hexc("d4af37"))
img.save(os.path.join(A, "icon.png"))


# ================================================================== modelos
def t(name):
    return f"{NS}:block/{name}"


def el(frm, to, tex, faces=None, uv=None, rot=None):
    faces = faces or ["north", "south", "east", "west", "up", "down"]
    out = {"from": frm, "to": to, "faces": {}}
    for f in faces:
        tx = tex[f] if isinstance(tex, dict) else tex
        face = {"texture": "#" + tx}
        if uv and f in uv:
            face["uv"] = uv[f]
        out["faces"][f] = face
    if rot:
        out["rotation"] = rot
    return out


def model(name, textures, elements, particle):
    tx = dict(textures)
    tx["particle"] = particle
    write(os.path.join(A, "models", "block", name + ".json"), {"textures": tx, "elements": elements})


def caras(frente, resto):
    return {"north": frente, "south": resto, "east": resto, "west": resto, "up": resto, "down": resto}


model("radio", {"madera": t("radio_madera"), "frente": t("radio_frente"), "metal": t("metal"), "perilla": t("perilla")}, [
    el([2, 0, 4], [14, 9, 12], caras("frente", "madera"), uv={"north": [0, 0, 16, 12]}),
    el([3, 9, 6], [13, 10, 10], "madera"),                                         # tapa
    el([4, 10, 7.5], [12, 11, 8.5], "metal"),                                      # asa
    el([3, 2, 3], [5, 4, 4], "perilla"),                                           # perillas
    el([3, 5, 3], [5, 7, 4], "perilla"),
    el([11.5, 9, 9], [12, 17, 9.5], "metal", rot={"angle": -22.5, "axis": "z", "origin": [11.75, 9, 9.25]}),  # antena
], t("radio_madera"))

model("televisor", {"madera": t("tv_madera"), "frente": t("tv_frente"), "atras": t("tv_atras"), "metal": t("metal"),
                    "perilla": t("perilla")}, [
    el([1, 0, 2], [15, 13, 14], caras("frente", "madera")),
    el([3, 2, 14], [13, 11, 16], "atras"),                                         # tubo (CRT)
    el([1.8, 7, 1], [3.2, 8.4, 2], "perilla"),
    el([1.8, 4, 1], [3.2, 5.4, 2], "perilla"),
    el([6.5, 13, 7.75], [7, 20, 8.25], "metal", rot={"angle": 22.5, "axis": "z", "origin": [6.75, 13, 8]}),   # antenas de conejo
    el([9, 13, 7.75], [9.5, 20, 8.25], "metal", rot={"angle": -22.5, "axis": "z", "origin": [9.25, 13, 8]}),
    el([7, 13, 7], [9, 14, 9], "atras"),
], t("tv_madera"))

model("pantalla", {"lado": t("pantalla_lado"), "frente": t("pantalla_frente")}, [
    el([0, 0, 14], [16, 16, 16], caras("frente", "lado")),
], t("pantalla_lado"))

ROT = {"north": 0, "east": 90, "south": 180, "west": 270}
BLOQUES = {"radio": "axe", "televisor": "axe", "pantalla": "pickaxe"}
for b in BLOQUES:
    write(os.path.join(A, "blockstates", b + ".json"), {"variants": {
        f"facing={f}": ({"model": f"{NS}:block/{b}", "y": r} if r else {"model": f"{NS}:block/{b}"}) for f, r in ROT.items()}})
    write(os.path.join(A, "items", b + ".json"), {"model": {"type": "minecraft:model", "model": f"{NS}:block/{b}"}})
    write(os.path.join(D, "loot_table", "blocks", b + ".json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "condition": {"type": "minecraft:survives_explosion"},
                   "entries": [{"type": "minecraft:item", "name": f"{NS}:{b}"}]}]})
write(os.path.join(A, "models", "item", "control_remoto.json"),
      {"parent": "minecraft:item/handheld", "textures": {"layer0": f"{NS}:item/control_remoto"}})
write(os.path.join(A, "items", "control_remoto.json"), {"model": {"type": "minecraft:model", "model": f"{NS}:item/control_remoto"}})

TAGS = os.path.join(RES, "data", "minecraft", "tags", "block", "mineable")
write(os.path.join(TAGS, "axe.json"), {"values": [f"{NS}:{b}" for b, h in BLOQUES.items() if h == "axe"]})
write(os.path.join(TAGS, "pickaxe.json"), {"values": [f"{NS}:{b}" for b, h in BLOQUES.items() if h == "pickaxe"]})


# ================================================================== recetas
def shaped(name, pattern, key, count=1):
    write(os.path.join(D, "recipe", name + ".json"), {"type": "minecraft:crafting_shaped", "pattern": pattern, "key": key,
                                                      "result": {"id": f"{NS}:{name}", "count": count}})


shaped("radio", [" I ", "PNP", "PRP"], {"I": "minecraft:iron_ingot", "P": "#minecraft:planks", "N": "minecraft:note_block",
                                         "R": "minecraft:redstone"})
shaped("televisor", ["PGP", "PNP", "PRP"], {"P": "#minecraft:planks", "G": "minecraft:glass_pane", "N": "minecraft:note_block",
                                             "R": "minecraft:redstone"})
shaped("pantalla", ["IGI", "IQI", "IRI"], {"I": "minecraft:iron_ingot", "G": "minecraft:glass", "Q": "minecraft:glowstone_dust",
                                            "R": "minecraft:redstone"})
write(os.path.join(D, "recipe", "control_remoto.json"), {
    "type": "minecraft:crafting_shapeless", "ingredients": ["minecraft:iron_ingot", "minecraft:redstone", "minecraft:stone_button"],
    "result": {"id": f"{NS}:control_remoto"}})

NOMBRES = {
    "block.teleradio.radio": "Radio",
    "block.teleradio.televisor": "Televisor",
    "block.teleradio.pantalla": "Pantalla Gigante",
    "item.teleradio.control_remoto": "Control Remoto",
    "itemGroup.teleradio": "TeleRadio",
}
for lang in ("en_us", "es_es", "es_mx", "es_ar", "es_cl", "es_ec", "es_uy", "es_ve"):
    write(os.path.join(A, "lang", lang + ".json"), NOMBRES)
print("Assets de TeleRadio generados")


# ================================================================== audifonos y parlante portatil
TEQ = os.path.join(A, "textures", "entity", "equipment", "humanoid")
os.makedirs(TEQ, exist_ok=True)
# audifonos puestos (capa de armadura 64x32: cabeza en 0,0 con caras de 8x8)
img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
d = ImageDraw.Draw(img)
d.rectangle([11, 0, 12, 7], fill=hexc("212121"))        # diadema por arriba (cara superior 8..16, 0..8)
for x0 in (0, 16):                                        # orejas: cara derecha (0..8) e izquierda (16..24), fila 8..16
    d.rectangle([x0 + 2, 11, x0 + 5, 15], fill=hexc("1565c0"))
    d.rectangle([x0 + 3, 12, x0 + 4, 14], fill=hexc("90caf9"))
    d.rectangle([x0 + 3, 8, x0 + 4, 10], fill=hexc("212121"))
for x in (8, 9, 14, 15, 24, 25, 30, 31):                 # los costados de las orejas se ven de frente y de espaldas
    d.rectangle([x, 11, x, 15], fill=hexc("1565c0"))
for x in range(8, 16):                                    # diadema que asoma por el borde de arriba
    d.point((x, 8), fill=hexc("212121") if x in (11, 12) else (0, 0, 0, 0))
img.save(os.path.join(TEQ, "audifonos.png"))
write(os.path.join(A, "equipment", "audifonos.json"), {"layers": {"humanoid": [{"texture": f"{NS}:audifonos"}]}})

img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
d = ImageDraw.Draw(img)
d.arc([2, 1, 13, 12], 180, 360, fill=hexc("212121"), width=2)
d.rectangle([1, 7, 4, 13], fill=hexc("1565c0"), outline=hexc("0d47a1"))
d.rectangle([11, 7, 14, 13], fill=hexc("1565c0"), outline=hexc("0d47a1"))
d.point((2, 9), fill=hexc("90caf9"))
d.point((12, 9), fill=hexc("90caf9"))
img.save(os.path.join(TI, "audifonos.png"))
write(os.path.join(A, "models", "item", "audifonos.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/audifonos"}})
write(os.path.join(A, "items", "audifonos.json"), {"model": {"type": "minecraft:model", "model": f"{NS}:item/audifonos"}})

# parlante: cilindro acostado (tipo JBL) hecho con cajas
img = Image.new("RGBA", (16, 16), hexc("e53935"))
d = ImageDraw.Draw(img)
for y in range(1, 16, 2):
    for x in range((y // 2) % 2, 16, 2):
        d.point((x, y), fill=hexc("b71c1c"))
d.rectangle([5, 6, 10, 9], fill=hexc("212121"))
d.point((7, 7), fill=hexc("ffffff"))
img.save(os.path.join(TB, "parlante_rejilla.png"))
img = Image.new("RGBA", (16, 16), hexc("263238"))
d = ImageDraw.Draw(img)
d.ellipse([3, 3, 12, 12], fill=hexc("37474f"), outline=hexc("90a4ae"))
d.ellipse([6, 6, 9, 9], fill=hexc("e53935"))
img.save(os.path.join(TB, "parlante_tapa.png"))
write(os.path.join(A, "models", "item", "parlante_portatil.json"), {
    "parent": "minecraft:block/block",
    "textures": {"rejilla": t("parlante_rejilla"), "tapa": t("parlante_tapa"), "particle": t("parlante_rejilla")},
    "elements": [
        el([3, 5, 5.5], [13, 10, 10.5], {"north": "rejilla", "south": "rejilla", "up": "rejilla", "down": "rejilla", "east": "tapa", "west": "tapa"}),
        el([3.5, 4.5, 6], [12.5, 10.5, 10], {"north": "rejilla", "south": "rejilla", "up": "rejilla", "down": "rejilla", "east": "tapa", "west": "tapa"}),
        el([2.5, 6, 6.5], [3, 9, 9.5], "tapa"), el([13, 6, 6.5], [13.5, 9, 9.5], "tapa"),
    ]})
write(os.path.join(A, "items", "parlante_portatil.json"), {"model": {"type": "minecraft:model", "model": f"{NS}:item/parlante_portatil"}})

shaped("audifonos", ["ILI", "N N"], {"I": "minecraft:iron_ingot", "L": "minecraft:leather", "N": "minecraft:note_block"})
shaped("parlante_portatil", ["CNC", "CRC"], {"C": "minecraft:copper_ingot", "N": "minecraft:note_block", "R": "minecraft:redstone"})
NOMBRES["item.teleradio.audifonos"] = "Audífonos"
NOMBRES["item.teleradio.parlante_portatil"] = "Parlante Portátil"
for lang in ("en_us", "es_es", "es_mx", "es_ar", "es_cl", "es_ec", "es_uy", "es_ve"):
    write(os.path.join(A, "lang", lang + ".json"), NOMBRES)
print("Audífonos y parlante generados")
