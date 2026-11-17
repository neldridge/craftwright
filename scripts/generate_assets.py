"""Generate Craftwright's pixel textures and block geometry (Python standard library only)."""
import json
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/craftwright'

class Canvas:
    def __init__(self, w=16, h=16, color='#00000000'):
        self.w, self.h = w, h
        self.pixels = [self.color(color)] * (w*h)
    @staticmethod
    def color(value):
        value = value.lstrip('#')
        return tuple(bytes.fromhex(value + ('ff' if len(value) == 6 else '')))
    def rect(self, x0, y0, x1, y1, color):
        for y in range(y0, y1+1):
            for x in range(x0, x1+1):
                self.pixels[y*self.w+x] = self.color(color)
    def save(self, path):
        path.parent.mkdir(parents=True, exist_ok=True)
        def chunk(kind, data):
            return struct.pack('>I', len(data))+kind+data+struct.pack('>I', zlib.crc32(kind+data))
        raw = b''.join(b'\0'+bytes(c for p in self.pixels[y*self.w:(y+1)*self.w] for c in p) for y in range(self.h))
        path.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR', struct.pack('>IIBBBBB',self.w,self.h,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw))+chunk(b'IEND',b''))

def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2)+'\n')

textures = {}
def save(name, canvas):
    textures[name] = canvas
    canvas.save(ASSETS/'textures'/f'{name}.png')

for advanced in (False, True):
    name = 'advanced_crafter' if advanced else 'crafter'
    dark, mid, light, accent = ('#202d3a','#435366','#8297a8','#64e4dd') if advanced else ('#49372f','#ae6846','#ebac71','#a9df98')
    for face in ('side','top','bottom','frame'):
        c = Canvas(color=dark)
        c.rect(0,0,15,0,light); c.rect(0,1,15,2,mid)
        c.rect(0,13,15,15,mid); c.rect(0,15,15,15,dark)
        c.rect(0,0,1,14,mid); c.rect(14,0,15,14,mid)
        for x,y in ((1,1),(14,1),(1,14),(14,14)):
            c.rect(x,y,x,y,light)
        if face == 'side':
            c.rect(3,4,12,11,'#131e25'); c.rect(4,5,11,5,light)
            for y in (7,9):
                c.rect(4,y,10,y,mid)
            c.rect(11,7,11,9,accent)
            c.rect(4,11,6,11,accent)
            c.rect(8,11,11,11,mid)
        elif face == 'top':
            c.rect(3,3,12,12,light); c.rect(4,4,11,11,dark)
            for x in (4,7,10):
                for y in (4,7,10):
                    c.rect(x,y,x+1,y+1,accent if advanced else '#d5a46b')
        elif face == 'bottom':
            c.rect(4,4,11,11,'#131e25')
            for x in (5,8): c.rect(x,5,x+1,10,mid)
        save(f'block/{name}_{face}',c)
    tex = {face:f'craftwright:block/{name}_{face}' for face in ('side','top','bottom','frame')}
    tex['particle'] = tex['side']
    def box(start,end,texture, cull=False):
        faces = {}
        for face in ('north','south','east','west','up','down'):
            entry = {'texture':'#'+texture}
            if cull: entry['cullface']=face
            faces[face]=entry
        return {'from':start,'to':end,'faces':faces}
    body = box([1,1,1],[15,15,15],'side')
    body['faces']['up']['texture']='#top'
    body['faces']['down']['texture']='#bottom'
    elements = [body]
    # Corner posts and heavy base keep a full-block silhouette with inset side panels.
    for x in (0,14):
        for z in (0,14): elements.append(box([x,0,z],[x+2,16,z+2],'frame'))
    elements += [box([0,0,0],[16,2,16],'frame'),box([0,14,0],[16,15,16],'frame'),box([2,15,2],[14,16,14],'top')]
    write_json(ASSETS/'models/block'/f'{name}.json', {'parent':'minecraft:block/block','textures':tex,'elements':elements})

for name in ('speed_upgrade','pattern_upgrade',*(f'{direction}_upgrade_{tier}' for direction in ('pull','push') for tier in range(1,5))):
    c = Canvas()
    c.rect(3,1,12,14,'#162630'); c.rect(2,3,13,12,'#162630')
    c.rect(3,2,12,12,'#526775'); c.rect(4,3,11,11,'#223c43')
    c.rect(3,2,12,2,'#a9bdc5'); c.rect(3,3,3,11,'#7f969f')
    for x in (4,6,8,10): c.rect(x,13,x,14,'#e7bd6b')
    if name.startswith(('pull','push')):
        tier = int(name[-1]); color = ('#d99462','#dbe7ee','#ffe080','#6aeee0')[tier-1]
        # Pull points inward/down; push points outward/up.
        c.rect(7,4,8,9,color)
        if name.startswith('pull'):
            c.rect(5,7,10,7,color); c.rect(6,8,9,8,color)
        else:
            c.rect(5,6,10,6,color); c.rect(6,5,9,5,color)
        for i in range(tier): c.rect(4+2*i,11,4+2*i,11,color)
    elif name == 'pattern_upgrade':
        for x in (5,8):
            for y in (4,7): c.rect(x,y,x+1,y+1,'#c4a4f5')
        c.rect(5,10,10,10,'#8b71bc')
    else:
        for x,y in ((8,4),(7,5),(6,6),(7,7),(8,7),(8,8),(7,9),(6,10)):
            c.rect(x,y,x+1,y,'#ffe080')
    save(f'item/{name}',c)
    write_json(ASSETS/'models/item'/f'{name}.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'craftwright:item/{name}'}})

# Enlarged contact sheet for reviewing exact pixels without game-side filtering.
preview = Canvas(448,224,'#182129')
for i,(name,c) in enumerate(textures.items()):
    col,row=i%9,i//9
    for y in range(16):
        for x in range(16):
            rgba=c.pixels[y*16+x]
            if rgba[3]:
                for yy in range(3):
                    for xx in range(3):
                        preview.pixels[(row*64+y*3+8+yy)*preview.w+col*49+x*3+4+xx]=rgba
preview.save(ROOT/'build/art-preview/textures.png')
print(f'Generated {len(textures)} textures, 2 block models and 10 item models.')
