"""
Générateur des sprites du chat de Fern (widget « Le chat »).

Le chat est décrit par des formes simples (ellipses, triangles, queue en courbe), converties en pixels,
puis marbrées façon écaille de tortue avec un « bruit » (des taches au hasard, mais toujours les mêmes).

Usage (depuis la racine du dépôt) :
    python3 design/tools/chat_sprites.py
→ réécrit app/src/main/java/com/atelierjlg/fern/ui/widgets/CatSprites.kt
→ et un aperçu design/tools/chat_sprites.png (nécessite Pillow : pip install pillow)

Pour retoucher : change les formes dans sit() / sleep() / stretch(), les seuils dans fur(),
ou les couleurs dans PAL, puis relance le script.
"""
import math
from PIL import Image, ImageDraw

def h(ix, iy, seed=7):
    v = math.sin(ix * 127.1 + iy * 311.7 + seed * 74.7) * 43758.5453
    return v - math.floor(v)

def noise(x, y, scale=4.5, seed=7):
    x /= scale; y /= scale
    x0, y0 = math.floor(x), math.floor(y); fx, fy = x - x0, y - y0
    sx, sy = fx*fx*(3-2*fx), fy*fy*(3-2*fy)
    a = h(x0,y0,seed)*(1-sx) + h(x0+1,y0,seed)*sx
    b = h(x0,y0+1,seed)*(1-sx) + h(x0+1,y0+1,seed)*sx
    return a*(1-sy) + b*sy

def ell(cx, cy, rx, ry, ang=0):
    c, s = math.cos(math.radians(ang)), math.sin(math.radians(ang))
    def f(x, y):
        dx, dy = x-cx, y-cy
        u, v = dx*c + dy*s, -dx*s + dy*c
        return (u/rx)**2 + (v/ry)**2 <= 1
    return f

def tri(p1, p2, p3):
    def f(x, y):
        def sgn(a, b, c): return (x-b[0])*(a[1]-b[1]) - (a[0]-b[0])*(y-b[1])
        d1, d2, d3 = sgn(p1,p2,p3), sgn(p2,p3,p1), sgn(p3,p1,p2)
        neg = d1 < 0 or d2 < 0 or d3 < 0; pos = d1 > 0 or d2 > 0 or d3 > 0
        return not (neg and pos)
    return f

def capsule(x1, y1, x2, y2, r):
    def f(x, y):
        dx, dy = x2-x1, y2-y1; L = dx*dx+dy*dy
        t = max(0, min(1, ((x-x1)*dx + (y-y1)*dy)/L)) if L else 0
        px, py = x1+t*dx, y1+t*dy
        return (x-px)**2 + (y-py)**2 <= r*r
    return f

def bezier_tail(pts, r, n=40):
    caps = []
    def B(t):
        (x0,y0),(x1,y1),(x2,y2),(x3,y3) = pts
        u = 1-t
        return (u**3*x0+3*u*u*t*x1+3*u*t*t*x2+t**3*x3, u**3*y0+3*u*u*t*y1+3*u*t*t*y2+t**3*y3)
    prev = B(0)
    for i in range(1, n+1):
        p = B(i/n); rr = r*(1-0.35*i/n)
        caps.append(capsule(prev[0],prev[1],p[0],p[1],rr)); prev = p
    return lambda x, y: any(c(x,y) for c in caps)

class Canvas:
    def __init__(s, w, hgt): s.w, s.h = w, hgt; s.g = [['.']*w for _ in range(hgt)]
    def fill(s, shape, ch, mask=None, ss=3):
        for y in range(s.h):
            for x in range(s.w):
                cnt = sum(shape(x+(i+.5)/ss, y+(j+.5)/ss) for i in range(ss) for j in range(ss))
                if cnt*2 >= ss*ss and (mask is None or mask(x, y, s.g[y][x])):
                    s.g[y][x] = ch(x, y) if callable(ch) else ch
    def px(s, x, y, ch):
        if 0 <= x < s.w and 0 <= y < s.h: s.g[y][x] = ch
    def rows(s): return [''.join(r) for r in s.g]

def fur(x, y):
    # Deux « octaves » de bruit : de grandes plaques + des mouchetures, pour un marbrage naturel.
    n = 0.65 * noise(x, y, 4.0) + 0.35 * noise(x + 31, y + 9, 1.8, 5)
    if n > 0.70: return 'l' if noise(x, y, 1.3, 9) > 0.55 else 'o'
    if n > 0.63: return 'o'
    if n < 0.40: return 'k'
    return 'b'

def body_mask(x, y, cur): return cur != '.'

# ── Assis, de face ─────────────────────────────────────────────────────────
def sit(eyes='open', tail=0, blush=False):
    c = Canvas(34, 40)
    tails = [((22,36),(32,36),(33,26),(29,20)), ((22,36),(31,37),(34,30),(33,22))]
    c.fill(bezier_tail(tails[tail], 2.3), fur)
    c.fill(ell(16, 29, 10.5, 9.5), fur)                 # corps
    c.fill(ell(16, 22, 7.5, 6), fur)                    # poitrail
    c.fill(tri((7,8),(9,-1),(14,5)), fur)               # oreille gauche
    c.fill(tri((25,8),(23,-1),(18,5)), fur)             # oreille droite
    c.fill(ell(16, 11.5, 9.5, 8), fur)                  # tête
    c.fill(tri((8.6,6),(9.4,1.2),(12.3,4.6)), 'p')      # intérieur des oreilles
    c.fill(tri((23.4,6),(22.6,1.2),(19.7,4.6)), 'p')
    # liseré caramel au milieu du visage (typique de l'écaille de tortue)
    c.fill(ell(17.6, 12.5, 1.5, 6, -8), lambda x,y: 'o' if noise(x,y,2) > .45 else 'b', body_mask)
    c.fill(capsule(11.5, 33, 11.5, 38.5, 2.2), fur)     # pattes avant
    c.fill(capsule(20.5, 33, 20.5, 38.5, 2.2), fur)
    for x in range(10, 14): c.px(x, 38, 'w'); c.px(x, 39, 'w')
    for x in range(19, 23): c.px(x, 38, 'w')
    c.fill(ell(16, 37.6, 9.5, 1.6), 's', lambda x,y,cur: cur in 'bklo' and y >= 37)
    # yeux
    for ex in (12, 20):
        if eyes == 'open':
            c.fill(ell(ex+.5, 11.5, 2.4, 1.8), 'e')
            c.fill(ell(ex+.5, 11.5, .6, 1.6), 'n')
            c.px(ex+1, 10, 'W')
        elif eyes == 'closed':
            for dx in range(-2, 3): c.px(ex+dx, 12, 'd')
            c.px(ex-2, 11, 'd'); c.px(ex+2, 11, 'd')
        elif eyes == 'happy':
            for dx, dy in ((-2,12),(-1,11),(0,10),(1,10),(2,11),(3,12)): c.px(ex+dx, dy, 'd')
    # nez, bouche, moustaches
    c.px(16, 15, 'p'); c.px(15, 15, 'p'); c.px(17, 15, 'p'); c.px(16, 16, 'p')
    c.px(15, 17, 'd'); c.px(17, 17, 'd'); c.px(16, 16, 'p')
    if blush:
        for (bx, by) in ((10,14),(11,14),(21,14),(22,14)): c.px(bx, by, 'r')
    return c.rows()

# ── Endormi, roulé en boule ────────────────────────────────────────────────
def sleep():
    c = Canvas(44, 26)
    c.fill(ell(25, 15, 16, 9.5), fur)                   # corps
    c.fill(tri((5,9),(6,1),(11,6)), fur)                # oreilles
    c.fill(tri((14,7),(16,0),(19,6)), fur)
    c.fill(ell(12, 13, 8.5, 7), fur)                    # tête posée
    c.fill(tri((6.2,8),(6.6,3),(9.6,6.3)), 'p')
    c.fill(tri((15.4,6.3),(16.1,2.4),(17.8,6)), 'p')
    c.fill(ell(13, 14, 2, 5.5, 20), lambda x,y: 'l' if noise(x,y,2) > .4 else 'o', body_mask)
    c.fill(bezier_tail(((40,18),(43,26),(20,26),(6,21)), 2.4), fur)   # queue enroulée devant
    c.fill(capsule(7, 21, 13, 21, 2), fur)               # patte sous le menton
    for x in range(6, 10): c.px(x, 22, 'w')
    for ex in (8, 15):
        for dx in range(-1, 2): c.px(ex+dx, 13, 'd')
        c.px(ex-2, 12, 'd'); c.px(ex+2, 12, 'd')
    c.px(11, 16, 'p'); c.px(12, 16, 'p')
    c.fill(ell(26, 24.2, 15, 1.5), 's', lambda x,y,cur: cur in 'bklo' and y >= 23)
    return c.rows()

# ── L'étirement du matin, de profil ────────────────────────────────────────
def stretch():
    c = Canvas(46, 30)
    c.fill(bezier_tail(((36,11),(40,4),(43,2),(44,-1)), 2.1), fur)   # queue dressée
    c.fill(ell(26, 15, 13, 6.5, 14), fur)               # dos qui descend vers l'avant
    c.fill(capsule(35, 16, 37, 28, 2.6), fur)           # pattes arrière
    c.fill(capsule(31, 17, 31, 28, 2.4), fur)
    c.fill(capsule(16, 21, 3, 27, 2.3), fur)            # pattes avant allongées
    c.fill(capsule(17, 22, 6, 28, 2.3), fur)
    c.fill(tri((7,12),(7,4),(12,9)), fur)               # oreilles
    c.fill(tri((15,10),(17,3),(19,10)), fur)
    c.fill(ell(13, 16, 7.5, 6.3), fur)                  # tête basse
    c.fill(tri((7.8,11),(7.9,6.5),(10.5,9.6)), 'p')
    c.fill(ell(13, 17, 1.8, 5, 10), lambda x,y: 'l' if noise(x,y,2) > .4 else 'o', body_mask)
    for x in range(1, 5): c.px(x, 28, 'w')
    for x in range(4, 8): c.px(x, 29, 'w')
    for ex in (10, 16):                                  # yeux plissés de plaisir
        for dx in range(-1, 2): c.px(ex+dx, 16, 'd')
    c.px(12, 19, 'p'); c.px(13, 19, 'p'); c.px(12, 20, 'r'); c.px(13, 20, 'r')   # petit bâillement
    return c.rows()

def heart():
    c = Canvas(11, 10)
    c.fill(ell(3, 3, 2.6, 2.6), 'h'); c.fill(ell(8, 3, 2.6, 2.6), 'h')
    c.fill(tri((0.4,4),(10.6,4),(5.5,9.6)), 'h')
    c.px(2, 2, 'H'); c.px(3, 2, 'H'); c.px(2, 3, 'H')
    return c.rows()

PAL = {'b':'#3B2A22','k':'#1F1612','o':'#A8703F','l':'#C99A63','s':'#271B16','p':'#D98C8C',
       'r':'#C76F78','e':'#8FC46A','n':'#14100C','W':'#F4EEDD','d':'#120D0A','w':'#E8DCC6',
       'h':'#EC9AA0','H':'#F7D4D6'}

SPRITES = {
    'sitA': sit('open', 0), 'sitB': sit('closed', 1), 'happyA': sit('happy', 0, True),
    'happyB': sit('happy', 1, True), 'stretch': stretch(), 'sleep': sleep(), 'heart': heart(),
}

def preview(path, names, S=8):
    sp = [SPRITES[n] for n in names]
    W = sum((len(s[0])+3)*S for s in sp); H = (max(len(s) for s in sp)+3)*S
    img = Image.new('RGB', (W, H), '#1E3427'); d = ImageDraw.Draw(img); ox = 0
    for s in sp:
        rows = len(s); cols = len(s[0]); top = H - (rows+1)*S
        filled = lambda x, y: 0 <= y < rows and 0 <= x < cols and s[y][x] != '.'
        for y in range(-1, rows+1):
            for x in range(-1, cols+1):
                px, py = ox+(x+1)*S, top+y*S
                if filled(x, y): d.rectangle([px,py,px+S-1,py+S-1], fill=PAL[s[y][x]])
                elif filled(x-1,y) or filled(x+1,y) or filled(x,y-1) or filled(x,y+1):
                    d.rectangle([px,py,px+S-1,py+S-1], fill='#6F7F66')
        ox += (cols+3)*S
    img.save(path)

NAMES = {
    'sitA': "Assis, yeux ouverts", 'sitB': "Assis, clignement (queue de l'autre côté)",
    'happyA': "Nourri, content : yeux en ^, joues roses", 'happyB': "Nourri, queue de l'autre côté",
    'stretch': "L'étirement du matin (de profil)", 'sleep': "Endormi, roulé en boule", 'heart': "Le petit cœur",
}


def kotlin():
    out = ['package com.atelierjlg.fern.ui.widgets', '', 'import androidx.compose.ui.graphics.Color', '',
           '// FICHIER GÉNÉRÉ par design/tools/chat_sprites.py : modifier le script plutôt que ce fichier.', '',
           '/**', ' * Le chat de Jules, d\'après sa photo : écaille de tortue (brun très foncé marbré de caramel),',
           ' * yeux verts, pattes claires. Un caractère = un pixel (« . » = transparent).', ' */',
           'object CatSprites {', '    /** Ses vraies couleurs (pas celles du thème : c\'est son pelage !). */',
           '    val palette = mapOf(']
    out += [f"        '{k}' to Color(0xFF{v[1:].upper()})," for k, v in PAL.items() if k not in 'hH']
    out += ['    )', '', '    val heartPalette = mapOf(']
    out += [f"        '{k}' to Color(0xFF{v[1:].upper()})," for k, v in PAL.items() if k in 'hH']
    out += ['    )']
    for name, rows in SPRITES.items():
        out += ['', f'    /** {NAMES[name]}. */', f'    val {name} = listOf(']
        out += [f'        "{r}",' for r in rows]
        out += ['    )']
    out += ['}', '']
    return '\n'.join(out)


if __name__ == '__main__':
    import os
    root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    with open(os.path.join(root, 'app/src/main/java/com/atelierjlg/fern/ui/widgets/CatSprites.kt'), 'w') as f:
        f.write(kotlin())
    preview(os.path.join(root, 'design/tools/chat_sprites.png'), ['sitA', 'happyA', 'sitB', 'stretch', 'sleep', 'heart'])
