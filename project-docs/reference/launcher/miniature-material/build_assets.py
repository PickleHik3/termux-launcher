"""Rebuild path-only SVG / Android pairs and PNG contact sheets using rsvg-convert."""
from pathlib import Path
import math, subprocess, xml.etree.ElementTree as ET

OUT = Path(__file__).resolve().parent
ACC, DIM, HIGH, SURF = '#FF00FF', '#808080', '#404040', '#202020'
assets = {}

def n(v): return f'{v:.3f}'.rstrip('0').rstrip('.') if isinstance(v, float) else str(v)
def rr(x,y,w,h,r):
    r=min(r,w/2,h/2)
    return f'M {n(x+r)},{n(y)} H {n(x+w-r)} Q {n(x+w)},{n(y)} {n(x+w)},{n(y+r)} V {n(y+h-r)} Q {n(x+w)},{n(y+h)} {n(x+w-r)},{n(y+h)} H {n(x+r)} Q {n(x)},{n(y+h)} {n(x)},{n(y+h-r)} V {n(y+r)} Q {n(x)},{n(y)} {n(x+r)},{n(y)} Z'
class Art:
    def __init__(self,w,h): self.w,self.h,self.paths=w,h,[]
    def path(self,d,fill=None,stroke=None,sw=1,alpha=None):
        p={'pathData':d}
        if fill:
            p['fillColor']=fill
            if fill==DIM or alpha is not None: p['fillAlpha']=n(.45 if fill==DIM else alpha)
        if stroke:
            p.update(strokeColor=stroke,strokeWidth=n(sw))
            if stroke==DIM: p['strokeAlpha']='0.45'
        self.paths.append(p)
    def box(self,x,y,w,h,r,fill=None,stroke=None,sw=1,alpha=None): self.path(rr(x,y,w,h,r),fill,stroke,sw,alpha)
    def dot(self,x,y,r,fill=DIM):
        self.path(f'M {n(x-r)},{n(y)} a {r},{r} 0 1,0 {2*r},0 a {r},{r} 0 1,0 {-2*r},0',fill)
    def line(self,x,y,x2,y2,c=DIM,sw=1): self.path(f'M {n(x)},{n(y)} L {n(x2)},{n(y2)}',stroke=c,sw=sw)

# Compact outlined lettering, baked into paths so Android needs no font or text node.
glyphs={
 'A':[(0,7,2.5,0),(2.5,0,5,7),(1,4.5,4,4.5)],
 'F':[(0,7,0,0),(0,0,5,0),(0,3,4,3)],
 'M':[(0,7,0,0),(0,0,2.5,4),(2.5,4,5,0),(5,0,5,7)],
 'S':[(5,0,0,0),(0,0,0,3.5),(0,3.5,5,3.5),(5,3.5,5,7),(5,7,0,7)],
 'Z':[(0,0,5,0),(5,0,0,7),(0,7,5,7)],
 'H':[(0,0,0,7),(5,0,5,7),(0,3.5,5,3.5)],
 'i':[(2.5,0,2.5,.8),(2.5,2.4,2.5,7)],
 'd':[(5,0,5,7),(5,7,0,7),(0,7,0,2.5),(0,2.5,5,2.5)],
 'e':[(0,4.5,5,4.5),(5,4.5,5,2.5),(5,2.5,0,2.5),(0,2.5,0,7),(0,7,5,7)],
 'n':[(0,7,0,2.5),(0,2.5,5,2.5),(5,2.5,5,7)],
 '1':[(1,1,3,0),(3,0,3,7)],
 '2':[(0,0,5,0),(5,0,5,3),(5,3,0,7),(0,7,5,7)],
 '4':[(0,0,0,4),(0,4,5,4),(4,0,4,7)],
 '0':[(0,0,5,0),(5,0,5,7),(5,7,0,7),(0,7,0,0)],
 ':':[(2.5,2,2.5,2.8),(2.5,5,2.5,5.8)]}
def lettering(a,s,x,y,size=7,color=DIM):
    scale=size/7
    for ch in s:
        for x1,y1,x2,y2 in glyphs[ch]: a.line(x+x1*scale,y+y1*scale,x+x2*scale,y+y2*scale,color,max(.8,scale))
        x+=7.5*scale
def grip(a,active=False,x=None,y=None):
    x=a.w-13 if x is None else x; y=min(16,a.h/2) if y is None else y
    for dx in (-3,3):
        for dy in (-5,0,5): a.dot(x+dx,y+dy,1.6,ACC if active else DIM)
def surface(a,active=False,r=12,fill=HIGH,handle=True):
    if active:
        a.box(2,5,a.w-4,a.h-6,r,SURF)
        a.box(2,1,a.w-4,a.h-6,r,HIGH,ACC,1.5)
        a.box(2,1,a.w-4,a.h-6,r,ACC,alpha=.09)
    else: a.box(1,1,a.w-2,a.h-2,r,fill,DIM,1)
    if handle: grip(a,active)
def status(w=216,h=32,active=False,vertical=False):
    a=Art(w,h); surface(a,active,10,handle=not vertical)
    lettering(a,'12:40',8,12,7 if not vertical else 5)
    x,y=(w-56,12) if not vertical else (8,h-38)
    for i in range(3): a.box(x+i*4,y+6-i*2,2,2+i*2,1,DIM)
    a.box(x+16,y,9,7,1,None,DIM); a.box(x+18,y+2,4,3,.5,DIM)
    if vertical:
        # Relocate grip below the clock to keep the clock unobscured.
        grip(a,active,y=36)
    return a
def pane(kind,w=216,h=304,active=False):
    a=Art(w,h); surface(a,active,20,SURF)
    if kind=='widgets':
        # Three quiet, asymmetric widget tiles leave the pane itself dominant.
        tw=(w-52)/2; th=min(72,(h-44)/2)
        a.box(12,32,tw,th,12,HIGH,alpha=.55)
        a.box(20+tw,32,tw,th,12,HIGH,alpha=.55)
        a.box(12,40+th,tw*.78,min(40,h-52-th),10,HIGH,alpha=.55)
        cx,cy=12+tw/2,32+th/2
        a.path(f'M {n(cx-15)},{n(cy)} a 15,15 0 1,0 30,0 a 15,15 0 1,0 -30,0',stroke=DIM)
        a.line(cx,cy,cx,cy-10); a.line(cx,cy,cx+8,cy+5)
        for i,l in enumerate([tw-20,tw-32,tw-26]): a.box(30+tw,44+i*8,l,2,1,DIM)
        a.box(22,52+th,tw*.78-20,2,1,DIM)
    else:
        for row,count in enumerate([15,11,17,8]):
            for col in range(count): a.box(14+col*7,36+row*13,4 if col%3 else 5,2,1,DIM)
        a.path('M 14,99 L 19,103 L 14,107 M 23,107 H 30',stroke=DIM,sw=1.5)
        a.box(36,98,6,11,1,DIM)
    return a
def apps(column=False,active=False):
    a=Art(48,152) if column else Art(216,48); surface(a,active,16)
    for i in range(5):
        x,y=(23,37+i*24) if column else (22+i*35,25)
        a.dot(x,y,9,ACC if i==4 else DIM)
        # Surface-coloured inset marks distinguish app-like discs from grip dots.
        if i%2: a.box(x-3,y-3,6,6,1,SURF)
        else: a.line(x-4,y,x+4,y,SURF,1.5)
    return a
def alph(w=216,h=36,active=False,tab=False):
    if tab:
        a=Art(48,28); surface(a,active,10); lettering(a,'A',10,10,8); return a
    a=Art(w,h); surface(a,active,10)
    for i,ch in enumerate('AFMSZ'): lettering(a,ch,16+i*(w-58)/4,(h-7)/2)
    return a
def extra(w=216,h=56,active=False):
    a=Art(w,h); surface(a,active,12)
    for i in range(5):
        x=14+i*(w-52)/5; y=h/2-7
        a.box(x-4,y-5,24,24,6,SURF)
        if i==0:
            a.box(x,y,15,11,2,None,DIM)
            for k in range(3):
                for j in range(2): a.box(x+3+k*4,y+3+j*3,2,1,.5,DIM)
        elif i==1:
            for j in range(3): a.dot(x+1,y+2+j*4,.8); a.line(x+5,y+2+j*4,x+14,y+2+j*4)
        elif i==2: a.path(f'M {x},{y+6} L {x+7},{y} L {x+14},{y+6} L {x+12},{y+6} V {y+13} H {x+9} V {y+8} H {x+5} V {y+13} H {x+2} V {y+6} Z',DIM)
        elif i==3: a.path(f'M {x},{y+1} L {x+5},{y+6} L {x},{y+11} M {x+7},{y+12} H {x+14}',stroke=DIM,sw=1.5)
        else:
            a.box(x,y,15,10,1,None,DIM,1.2); a.line(x+7.5,y+10,x+7.5,y+14); a.line(x+3,y+14,x+12,y+14)
    return a
def keyboard(active=False):
    a=Art(216,128); surface(a,active,16,fill=SURF,handle=False)
    a.box(3,3,210,122,14,HIGH,alpha=.6)
    a.box(90,8,36,3,1.5,DIM)
    for row,count in enumerate([10,9,8]):
        start=(216-(count*18-3))/2
        for i in range(count): a.box(start+i*18,24+row*23,15,18,4,DIM)
    for x,w in [(24,26),(54,108),(166,26)]: a.box(x,93,w,21,5,DIM)
    grip(a,active)
    return a
def tray(w=240,active=False):
    a=Art(w,52)
    a.box(1,1,w-2,50,14,HIGH if active else SURF)
    if active: a.box(1,1,w-2,50,14,ACC,alpha=.09)
    # Explicit path segments for portable dashes (VectorDrawable has no dash array).
    c=ACC if active else DIM
    for x in range(18,w-18,10):
        for y in (1,51): a.line(x,y,min(x+5,w-18),y,c,1.5)
    for y in (20,30):
        for x in (1,w-1): a.line(x,y,x,y+5,c,1.5)
    for d in [f'M 1,14 Q 1,1 14,1',f'M {w-14},1 Q {w-1},1 {w-1},14',f'M 1,38 Q 1,51 14,51',f'M {w-14},51 Q {w-1},51 {w-1},38']: a.path(d,stroke=c,sw=1.5)
    lettering(a,'Hidden',(w-62)/2,20,10,c)
    return a
def frame(w,h):
    a=Art(w,h); a.box(1,1,w-2,h-2,24,SURF,DIM,1.5); return a

for active in (False,True):
    suffix='_active' if active else ''
    for name,art in [('status_bar',status(active=active)),('status_bar_vertical',status(40,176,active,True)),('pane_widgets',pane('widgets',active=active)),('pane_terminal',pane('terminal',active=active)),('apps_row',apps(active=active)),('apps_column',apps(True,active)),('alphabets_row',alph(active=active)),('alphabets_tab',alph(active=active,tab=True)),('extra_keys',extra(active=active)),('keyboard',keyboard(active)),('hidden_tray',tray(active=active))]: assets[name+suffix]=art
    a=Art(24,24); grip(a,active,12,12); assets['drag_handle'+suffix]=a
assets['phone_frame_portrait']=frame(240,520)
assets['phone_frame_landscape']=frame(520,240)
# Native landscape geometry; no non-uniform scaling of grips, circles or glyphs.
assets.update(status_bar_landscape=status(496,24),pane_widgets_landscape=pane('widgets',440,128),alphabets_row_landscape=alph(440,24),extra_keys_landscape=extra(496,28))

def paths_svg(a):
    mapping={'pathData':'d','fillColor':'fill','strokeColor':'stroke','strokeWidth':'stroke-width','fillAlpha':'fill-opacity','strokeAlpha':'stroke-opacity'}
    return ''.join('<path '+ ' '.join(f'{mapping[k]}="{v}"' for k,v in p.items())+(' fill="none"' if 'fillColor' not in p else '')+'/>' for p in a.paths)
def svg(w,h,body): return f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}">{body}</svg>'
for name,a in assets.items():
    (OUT/f'{name}.svg').write_text(svg(a.w,a.h,paths_svg(a)))
    root=ET.Element('vector',{'xmlns:android':'http://schemas.android.com/apk/res/android','android:width':f'{a.w}dp','android:height':f'{a.h}dp','android:viewportWidth':str(a.w),'android:viewportHeight':str(a.h)})
    for p in a.paths: ET.SubElement(root,'path',{'android:'+k:v for k,v in p.items()})
    ET.indent(root)
    (OUT/f'{name}.xml').write_text('<?xml version="1.0" encoding="utf-8"?>\n'+ET.tostring(root,encoding='unicode')+'\n')
def put(a,x,y,scale=1): return f'<g transform="translate({x} {y}) scale({scale})">{paths_svg(a)}</g>'
def compose(terminal=False,landscape=False):
    if landscape:
        body=put(assets['phone_frame_landscape'],0,0)+put(assets['status_bar_landscape'],12,8)+put(assets['apps_column'],12,40)+put(assets['pane_widgets_landscape'],68,40)+put(assets['alphabets_row_landscape'],68,172)+put(assets['extra_keys_landscape'],12,204)+put(tray(520),0,256)
        return svg(520,308,body)
    body=put(assets['phone_frame_portrait'],0,0)+put(assets['status_bar'],12,8)
    if terminal:
        body+=put(pane('terminal',216,236),12,44)+put(assets['alphabets_tab'],12,244)+put(assets['apps_row'],12,288)+put(extra(216,32),12,344)+put(assets['keyboard'],12,380)
    else:
        body+=put(assets['pane_widgets'],12,44)+put(assets['apps_row'],12,356)+put(assets['alphabets_row'],12,408)+put(assets['extra_keys'],12,448)
    return svg(240,588,body+put(assets['hidden_tray'],0,536))
def render(name): subprocess.run(['rsvg-convert',str(OUT/f'{name}.svg'),'-o',str(OUT/f'{name}.png')],check=True)
for name,body in [('miniature_portrait',compose()),('miniature_landscape',compose(landscape=True)),('miniature_portrait_terminal',compose(terminal=True))]:
    (OUT/f'{name}.svg').write_text(body); render(name)

# Human review palette is intentionally confined to this contact sheet.
items=list(assets.items()); columns=4; cw,ch=304,236
height=104+math.ceil(len(items)/columns)*ch+650
body=f'<rect width="1216" height="{height}" fill="#121212"/><g font-family="sans-serif" fill="#E2E2E2"><text x="24" y="36" font-size="24">Layout miniature · Material surfaces</text><text x="24" y="66" font-size="14" fill="#ACACAC">Path-only assets / teal = selected or lifted / six-dot grip = drag</text></g>'
for i,(name,a) in enumerate(items):
    x,y=(i%columns)*cw,(i//columns)*ch+96
    body+=f'<rect x="{x+8}" y="{y}" width="288" height="220" rx="16" fill="#202020"/><text x="{x+20}" y="{y+27}" font-family="sans-serif" font-size="12" fill="#E2E2E2">{name}</text>'
    s=min(1,260/a.w,168/a.h)
    body+=put(a,x+(cw-a.w*s)/2,y+44+(168-a.h*s)/2,s)
y=104+math.ceil(len(items)/columns)*ch
for name,x in [('miniature_portrait',24),('miniature_portrait_terminal',304),('miniature_landscape',592)]:
    body+=f'<text x="{x}" y="{y+18}" font-family="sans-serif" font-size="14" fill="#E2E2E2">{name.replace("miniature_", "").replace("_", " · ")}</text>'
    data=(OUT/f'{name}.svg').read_text(); inner=data[data.index('>')+1:data.rindex('</svg>')]
    body+=f'<g transform="translate({x} {y+40})">{inner}</g>'
body=body.replace(ACC,'#7FD0E0').replace(DIM,'#D8DFE1').replace(HIGH,'#343C3F').replace(SURF,'#202629')
(OUT/'preview.svg').write_text(svg(1216,height,body)); render('preview')
print(f'Generated {len(assets)} matched SVG/VectorDrawable pairs, 3 compositions and preview.')
