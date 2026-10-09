from pathlib import Path
import json, zipfile

OUT = Path(__file__).parent
C = dict(canvas='#17150f', surface='#302c20', terminal='#211e15', key='#403b2a', line='#625c47', text='#e2dcc5', muted='#aaa48b', accent='#ebc900', accent_bg='#645400', secondary='#c5ce8c', secondary_bg='#485016')
R = 10

def rect(x,y,w,h,fill,r=0,stroke=None):
    return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" fill="{fill}"'+(f' stroke="{stroke}" stroke-width="1"' if stroke else '')+'/>'
def text(x,y,value,size=12,color=None,anchor='start'):
    return f'<text x="{x}" y="{y}" fill="{color or C["text"]}" font-family="sans-serif" font-size="{size}" text-anchor="{anchor}">{value}</text>'
def path(d,color=None,width=1.8):
    return f'<path d="{d}" fill="none" stroke="{color or C["text"]}" stroke-width="{width}" stroke-linecap="round" stroke-linejoin="round"/>'
GLYPHS={
 'terminal':'M5 7l5 5-5 5M13 17h6',
 'phone':'M7 4l3 5-2 2c1.5 3 2.5 4 5 5l2-2 5 3c-1 4-4 4-7 2C7 16 4 12 4 8c0-2 1-3 3-4Z',
 'chat':'M5 5h14v11H10l-5 4V5ZM9 9h6M9 12h4',
 'globe':'M3 12h18M12 3c-5 5-5 13 0 18M12 3c5 5 5 13 0 18M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0',
 'play':'M9 6l10 6-10 6Z',
 'monitor':'M3 5h18v12H3ZM9 21h6M12 17v4',
 'grid':'M5 5h5v5H5ZM14 5h5v5h-5ZM5 14h5v5H5ZM14 14h5v5h-5Z',
 'keyboard':'M4 5h16q2 0 2 2v10q0 2-2 2H4q-2 0-2-2V7q0-2 2-2ZM6 9h1M10 9h1M14 9h1M18 9h1M6 12h1M10 12h1M14 12h1M18 12h1M7 16h10',
 'mouse':'M6 9a6 6 0 0 1 12 0v6a6 6 0 0 1-12 0ZM6 10h12M12 3v7',
 'home':'M3 11l9-8 9 8M6 9v11h4v-6h4v6h4V9',
 'list':'M9 6h11M9 12h11M9 18h11M4 6h.1M4 12h.1M4 18h.1',
}
def glyph(kind,cx,cy,size=18,color=None):
    return f'<g transform="translate({cx-size/2} {cy-size/2}) scale({size/24})">'+path(GLYPHS[kind],color)+'</g>'
def shell(w,h,mode):
    return rect(0,0,w,h,C['surface'],R if mode=='floating' else 0,C['line'] if mode=='floating' else None)
def status(w,h,mode):
    s=shell(w,h,mode)
    y=h-19
    s+=rect(10,y-12,18,18,C['key'],5)+text(19,y+1,'2',10,anchor='middle')
    s+=rect(33,y-12,38,18,C['accent_bg'],5)+text(52,y+1,'home',10,C['accent'],anchor='middle')+text(79,y+1,'+',15)
    s+=path(f'M{w-82} {y-5}h12',C['accent'])+path(f'M{w-61} {y-5}h10',C['secondary'])+text(w-39,y,'☾',12,C['secondary'])
    if h>50:
        s+=text(14,27,'08:24',22)+rect(121,10,w-134,28,C['key'],5)+glyph('play',w-30,24,16)
    return s
DOCK=['phone','chat','globe','terminal','play','monitor','grid']
TOOL=['keyboard','mouse','home','terminal','monitor','grid','list']
def symbols(w,h,mode,kind,vertical=False):
    s=shell(w,h,mode)
    items=DOCK if kind=='dock' else TOOL
    count=len(items)
    length=h if vertical else w
    pitch=(length-16)/count
    size=min(32 if kind=='dock' else 22, (w if vertical else h)-12,pitch-4)
    for i,item in enumerate(items):
        cx=w/2 if vertical else 8+(i+.5)*pitch
        cy=8+(i+.5)*pitch if vertical else h/2
        active=i==3
        if kind=='dock':
            fill=C['accent_bg'] if i in (0,3,4) else C['secondary_bg'] if i in (1,2) else C['key']
            s+=f'<circle cx="{cx}" cy="{cy}" r="{size/2}" fill="{fill}"/>'
        elif active:
            s+=rect(cx-size/2-3,cy-size/2-3,size+6,size+6,C['accent_bg'],5)
        s+=glyph(item,cx,cy,size*.62 if kind=='dock' else size*.72,C['accent'] if active or (kind=='dock' and i in (0,4)) else C['text'])
    return s

def alphabet(w,h,mode,vertical=False):
    s=shell(w,h,mode)
    for i,ch in enumerate('ADGJMPSVZ'):
        cx=w/2 if vertical else 12+i*(w-24)/8
        cy=14+i*(h-28)/8 if vertical else h/2
        if i==4:s+=rect(cx-9,cy-9,18,18,C['accent_bg'],5)
        s+=text(cx,cy+4,ch,11,C['accent'] if i==4 else C['text'],'middle')
    return s

def terminal(w,h,mode):
    s=rect(0,0,w,h,C['terminal'],R if mode=='floating' else 0,C['line'] if mode=='floating' else None)
    s+=glyph('terminal',22,26,16,C['secondary'])+rect(37,24,min(70,w-55),3,C['accent'],1.5)
    for i,width in enumerate((116,88,102)):
        s+=rect(18,46+i*14,min(width,w-36),3,C['key'],1.5)
    s+=glyph('terminal',22,h-27,16,C['secondary'])+rect(37,h-32,4,11,C['accent'],1)
    return s

def keyboard(w,h,mode,form='docked'):
    s=shell(w,h,mode)
    parts=[(0,w)] if form!='split' else [(0,w*.44),(w*.56,w*.44)]
    if form=='split':s=''
    for offset,pw in parts:
        if form=='split':s+=rect(offset,0,pw,h,C['surface'],R if mode=='floating' else 0,C['line'] if mode=='floating' else None)
        gap=4; margin=10; top=10; rowh=(h-top-margin)/4-gap
        counts=[10,9,9,5] if form!='split' else [5,4,4,3]
        for row,count in enumerate(counts):
            widths=[1]*count
            if row==3:widths=[1.2,.8,3.5,1.7,1.8] if count==5 else [1,2,1]
            unit=(pw-2*margin)/sum(widths)
            x=offset+margin+(unit*.3 if row==1 else 0)
            for col,weight in enumerate(widths):
                kw=unit*weight-gap
                if x+kw>offset+pw-margin:kw=offset+pw-margin-x
                color=C['accent_bg'] if row==3 and col==2 and count==5 else C['secondary_bg'] if row==3 and col==count-1 else C['key']
                y=top+row*(rowh+gap)
                s+=rect(x,y,kw,rowh,color,4)
                if row==2 and col==0:s+=text(x+kw/2,y+rowh/2+4,'⇧',15,anchor='middle')
                if row==2 and col==count-1:s+=text(x+kw/2,y+rowh/2+4,'‹',18,anchor='middle')
                if row==3 and col==count-1:s+=text(x+kw/2,y+rowh/2+4,'↵',17,C['secondary'],'middle')
                if row==3 and col==2 and count==5:s+=rect(x+kw*.35,y+rowh/2,kw*.3,2,C['accent'],1)
                x+=unit*weight
    return s

def svg(w,h,content):
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}">{content}</svg>\n'
def place(content,x,y):return f'<g transform="translate({x} {y})">{content}</g>'

def phone(mode,side=False):
    w=360; h=780
    s=rect(0,0,w,h,C['canvas'],22,C['line'])
    if mode=='floating':
        x=12; width=336
        s+=place(status(width,28,mode),x,12)
        s+=place(terminal(width,432,mode),x,48)
        bottom=symbols(width,56,mode,'dock')+place(alphabet(width,24,'docked'),0,56)+place(symbols(width,36,'docked','toolbar'),0,80)
        # One common shell for dock, alphabet and toolbar, with straight internal boundaries.
        bottom=rect(0,0,width,116,C['surface'],R,C['line'])+symbols(width,56,'docked','dock')+place(alphabet(width,24,'docked'),0,56)+place(symbols(width,36,'docked','toolbar'),0,80)
        bottom=f'<g clip-path="url(#floating-bottom)">{bottom}</g>'
        s+='<defs><clipPath id="floating-bottom">'+rect(0,0,width,116,'white',R)+'</clipPath></defs>'
        s+=place(bottom,x,488)+place(keyboard(width,156,mode),x,612)
    else:
        width=336; innerh=756
        s+=place(rect(0,0,width,innerh,C['surface'],R),12,12)
        inner=status(width,28,'docked')
        if side:
            inner+=place(symbols(42,488,'docked','dock',True),0,28)
            inner+=place(alphabet(28,488,'docked',True),308,28)
            inner+=place(terminal(266,488,'docked'),42,28)
            inner+=place(symbols(width,36,'docked','toolbar'),0,516)
            inner+=place(keyboard(width,204,'docked'),0,552)
        else:
            inner+=place(terminal(width,416,'docked'),0,28)
            inner+=place(symbols(width,56,'docked','dock'),0,444)
            inner+=place(alphabet(width,24,'docked'),0,500)
            inner+=place(symbols(width,36,'docked','toolbar'),0,524)
            inner+=place(keyboard(width,196,'docked'),0,560)
        ident='docked-side' if side else 'docked-bottom'
        s+=f'<defs><clipPath id="{ident}">'+rect(0,0,width,innerh,'white',R)+'</clipPath></defs>'
        s+=place(f'<g clip-path="url(#{ident})">{inner}</g>'+rect(0,0,width,innerh,'none',R,C['line']),12,12)
    return s

for mode in ['floating','docked']:
    folder=OUT/mode; folder.mkdir(exist_ok=True)
    specs={
        'status-compact':(336,28,status(336,28,mode)),
        'status-expanded':(336,80,status(336,80,mode)),
        'dock-horizontal':(336,56,symbols(336,56,mode,'dock')),
        'dock-vertical':(42,488,symbols(42,488,mode,'dock',True)),
        'alphabet-horizontal':(336,24,alphabet(336,24,mode)),
        'alphabet-vertical':(28,488,alphabet(28,488,mode,True)),
        'extra-keys-horizontal':(336,36,symbols(336,36,mode,'toolbar')),
        'extra-keys-vertical':(42,488,symbols(42,488,mode,'toolbar',True)),
        'terminal':(336,416,terminal(336,416,mode)),
        'keyboard-docked':(336,196,keyboard(336,196,mode)),
        'keyboard-floating':(260,156,keyboard(260,156,'floating')),
        'keyboard-split':(336,196,keyboard(336,196,mode,'split')),
    }
    for name,(w,h,content) in specs.items():(folder/(name+'.svg')).write_text(svg(w,h,content))
    for name,data in GLYPHS.items():
        folder=OUT/'glyphs';folder.mkdir(exist_ok=True)
        (folder/(name+'.svg')).write_text(svg(24,24,path(data)))

board=rect(0,0,1280,1060,'#15140f')
board+=text(48,48,'TERMUX LAUNCHER / LAYOUT ASSETS',13,C['muted'])
board+=text(48,82,'One shape language. Two surface modes.',27)
for x,title,caption,mode,side in [(48,'Floating','Separate cards · shared dock stack','floating',False),(460,'Docked · bottom bars','Flush joins · no terminal container','docked',False),(872,'Docked · side bars','Bars form the window · zero inner gaps','docked',True)]:
    board+=text(x,125,title,18)+place(phone(mode,side),x,148)+text(x,958,caption,13,C['muted'])
board+=text(48,1011,'No dot handles. Outer corners only at joined edges. Selection follows the visible surface.',15,C['text'])
(OUT/'preview.svg').write_text(svg(1280,1060,board))
(OUT/'tokens.json').write_text(json.dumps({'palette':C,'outer_radius':R,'floating_gap':8,'docked_gap':0,'docked_terminal_border':False,'docked_internal_corner_radius':0,'drag_handle':False,'note':'SVGs are design sources; derive bounds at runtime. Docked pieces compose under one outer clip. Theme roles must be resolved from the live scheme.'},indent=2)+'\n')
(OUT/'README.md').write_text('''# Termux layout assets — proposal\n\n24 surface SVGs plus 11 glyph SVGs. Transparent outside each surface.\n\nFloating: separate rounded status, terminal and keyboard cards; dock, alphabet and extra keys form one shared rounded card.\n\nDocked: edge pieces join without padding. Side bars touch top and bottom bars. The terminal is an unoutlined opening, with no separate glass container. Only exposed outer corners round; join edges stay square. Clip the assembled frame once, not each internal piece.\n\nThe same shape must drive fill, selection outline and drag preview. Symbols and keys retain their proportions when bounds change. Use runtime theme roles, not fixed preview colors. No dot-matrix grab handles.\n\nThis is a review pack, not an app implementation. Keyboard form (docked/floating/split) remains distinct from surface mode.\n''')
with zipfile.ZipFile('/tmp/termux-layout-assets-proposal.zip','w',zipfile.ZIP_DEFLATED) as archive:
    for p in OUT.rglob('*'):
        if p.is_file():archive.write(p,'termux-layout-assets/'+str(p.relative_to(OUT)))
print(OUT/'preview.svg')
