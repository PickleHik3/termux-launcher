#!/usr/bin/env python3
"""Regenerate from the supplied tokens and glyphs. Run: python project-docs/reference/help-diagrams/generate.py.
All diagram geometry is shared by SVG and VectorDrawable; rsvg-convert is the
stdlib-only raster fallback. Topic labels occur only on the review sheet.
Descriptions marked unchanged in the proposal are interpreted conservatively.
"""
from pathlib import Path
import json, math, re, subprocess, shutil, xml.etree.ElementTree as ET
from xml.sax.saxutils import escape
ROOT=Path(__file__).resolve().parent  # project-docs/reference/help-diagrams
OUT=ROOT/'out'  # generated; not committed (see README.md for the copy into res/drawable)
PACK=ROOT.parent/'layout-assets'  # tokens.json and glyphs/, shared with the Layout editor
C=json.loads((PACK/'tokens.json').read_text())['palette']
G={p.stem:ET.parse(p).getroot().find('{http://www.w3.org/2000/svg}path').get('d') for p in (PACK/'glyphs').glob('*.svg')}
G.update({
 'plus':'M12 5v14M5 12h14', 'close':'M6 6l12 12M18 6L6 18',
 'check':'M5 12l5 5L20 6', 'search':'M17 10a7 7 0 1 1-14 0a7 7 0 1 1 14 0M15 15l6 6',
 'copy':'M8 8h12v13H8ZM4 16V3h12', 'paste':'M9 5H5v16h14V5h-4M9 3h6v5H9Z',
 'palette':'M21 12a9 9 0 1 0-9 9h2q3 0 1-3q-2-3 2-3h2q2 0 2-3ZM7 8h.1M12 6h.1M17 9h.1M6 13h.1',
 'layout':'M4 4h16v16H4ZM4 10h16M12 10v10',
 'picture':'M3 4h18v16H3ZM4 17l5-6 4 4 3-3 4 5M15 8h.1',
 'minimal':'M4 9V4h5M15 4h5v5M20 15v5h-5M9 20H4v-5',
 'gear':'M9 3h6l1 4 4 2v6l-4 2-1 4H9l-1-4-4-2V9l4-2ZM16 12a4 4 0 1 1-8 0a4 4 0 1 1 8 0',
 'help':'M8 8q0-5 5-4q5 1 2 5l-3 3v2M12 18h.1',
 'move':'M12 3v18M3 12h18M9 6l3-3 3 3M9 18l3 3 3-3M6 9l-3 3 3 3M18 9l3 3-3 3',
 'mic':'M9 5a3 3 0 0 1 6 0v7a3 3 0 0 1-6 0ZM5 11v1a7 7 0 0 0 14 0v-1M12 19v3M8 22h8',
 'enter':'M19 5v9H5M9 10l-4 4 4 4', 'folder':'M3 7h7l2 3h9v10H3ZM3 7V4h7l3 3',
 'download':'M12 3v12M7 10l5 5 5-5M4 17v4h16v-4',
 'chip':'M6 6h12v12H6ZM9 9h6v6H9ZM9 3v3M15 3v3M9 18v3M15 18v3M3 9h3M3 15h3M18 9h3M18 15h3',
 'undo':'M8 5L3 10l5 5M3 10h10q8 0 7 9',
 'eye_off':'M3 12q9-12 18 0q-9 12-18 0M3 3l18 18',
 'lock':'M7 11V7a5 5 0 0 1 10 0v4M5 11h14v10H5ZM12 15v3',
 'scale':'M4 9V4h5M15 4h5v5M20 15v5h-5M9 20H4v-5M8 16l8-8',
})

def f(n):return f'{n:.3f}'.rstrip('0').rstrip('.') if isinstance(n,float) else str(n)
class Drawing:
 def __init__(self,h=220):self.h=h;self.items=[];self.focus='';self.gesture='none'
 def path(self,d,fill=None,stroke='line',width=1.8,alpha=1):
  self.items.append(dict(d=d,fill=fill,stroke=stroke,width=width,alpha=alpha))
 def rect(self,x,y,w,h,r=8,fill='surface',hi=False,stroke=None):
  r=min(r,w/2,h/2);d=f'M{f(x+r)} {f(y)}H{f(x+w-r)}Q{f(x+w)} {f(y)} {f(x+w)} {f(y+r)}V{f(y+h-r)}Q{f(x+w)} {f(y+h)} {f(x+w-r)} {f(y+h)}H{f(x+r)}Q{f(x)} {f(y+h)} {f(x)} {f(y+h-r)}V{f(y+r)}Q{f(x)} {f(y)} {f(x+r)} {f(y)}Z'
  self.path(d,fill,'accent' if hi else stroke,2 if hi else 1.8)
 def circle(self,x,y,r,fill=None,stroke=None,width=2,alpha=1):
  self.path(f'M{f(x-r)} {f(y)}a{r} {r} 0 1 0 {2*r} 0a{r} {r} 0 1 0 {-2*r} 0',fill,stroke,width,alpha)
 def line(self,x,y,w,color='key',width=3):self.path(f'M{x} {y}h{w}',None,color,width)
 def glyph(self,name,x,y,size=24,hi=False):
  self.items.append(dict(group=(x-size/2,y-size/2,size/24),d=G[name],fill=None,stroke='accent' if hi else 'line',width=1.8*24/size,alpha=1))
 def finger(self,x,y,hold=False):
  self.circle(x,y,7,'accent',None,alpha=.85);self.circle(x,y,11,None,'accent')
  if hold:self.circle(x,y,16,None,'accent',alpha=.65)
 def motion(self,points,hold=False,kind='drag'):
  self.gesture=kind; x,y=points[0];self.finger(x,y,hold)
  self.path('M'+' L'.join(f'{f(a)} {f(b)}' for a,b in points),None,'accent',2.4)
  a,b=points[-2:];dx=b[0]-a[0];dy=b[1]-a[1];n=math.hypot(dx,dy); ux,uy=dx/n,dy/n
  self.path(f'M{f(b[0]-8*ux+4*uy)} {f(b[1]-8*uy-4*ux)}L{b[0]} {b[1]}L{f(b[0]-8*ux-4*uy)} {f(b[1]-8*uy+4*ux)}',None,'accent',2.4)
 def touch(self,x,y,hold=False):self.gesture='hold' if hold else 'tap';self.finger(x,y,hold)
 def step(self,x,y,num):
  self.circle(x,y,10,'accent_bg');self.path(f'M{x-2} {y-3}l3-3v12' if num==1 else f'M{x-4} {y-4}q5-5 8 0q0 3-8 9h8',None,'accent',1.8)
 def svg(self,bg=False):
  parts=[f'<svg xmlns="http://www.w3.org/2000/svg" version="1.1" width="360" height="{self.h}" viewBox="0 0 360 {self.h}">']
  if bg:parts.append(f'<path d="M0 0H360V{self.h}H0Z" fill="{C["canvas"]}"/>')
  for p in self.items:
   if 'group' in p:
    x,y,s=p['group'];parts.append(f'<g transform="translate({f(x)} {f(y)}) scale({f(s)})">')
   parts.append(f'<path d="{p["d"]}" fill="{C[p["fill"]] if p["fill"] else "none"}" stroke="{C[p["stroke"]] if p["stroke"] else "none"}" stroke-width="{f(p["width"])}" fill-opacity="{p["alpha"]}" stroke-opacity="{p["alpha"]}" stroke-linecap="round" stroke-linejoin="round"/>')
   if 'group' in p:parts.append('</g>')
  return ''.join(parts)+'</svg>'
 def vector(self):
  parts=[f'<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="360dp" android:height="{self.h}dp" android:viewportWidth="360" android:viewportHeight="{self.h}">']
  for p in self.items:
   if 'group' in p:
    x,y,s=p['group'];parts.append(f'<group android:translateX="{f(x)}" android:translateY="{f(y)}" android:scaleX="{f(s)}" android:scaleY="{f(s)}">')
   attrs=f'android:pathData="{p["d"]}"'
   if 'name' in p:attrs+=f' android:name="{p["name"]}"'
   if p['fill']:attrs+=f' android:fillColor="{C[p["fill"]]}" android:fillAlpha="{p["alpha"]}"'
   if p['stroke']:attrs+=f' android:strokeColor="{C[p["stroke"]]}" android:strokeAlpha="{p["alpha"]}" android:strokeWidth="{f(p["width"])}" android:strokeLineCap="round" android:strokeLineJoin="round"'
   parts.append('<path '+attrs+'/>')
   if 'group' in p:parts.append('</group>')
  return '\n'.join(parts)+'\n</vector>\n'

def lines(d,x,y,w=110,count=4,gap=13):
 for i in range(count):d.line(x,y+i*gap,w*[1,.72,.88,.55][i%4])
def terminal(d,x,y,w,h,hi=False):
 d.rect(x,y,w,h,10,'terminal',hi,stroke=None if hi else 'line');d.glyph('terminal',x+17,y+20,18);lines(d,x+30,y+20,min(w-45,110),3)
 if h>95:d.glyph('terminal',x+17,y+h-18,18);d.rect(x+30,y+h-23,3,10,1,'key')
def keyboard(d,x=20,y=100,w=320,h=100,focus=None,split=False,card=False):
 if card:d.rect(x,y,w,h,10,'surface',stroke='line')
 for row,n in enumerate([5,4,4] if split else [10,9,9]):
  kw=(w-16)/(5 if split else 10);off=kw*.4 if row==1 else 0
  for col in range(n):d.rect(x+8+off+col*kw,y+7+row*(h-14)/4,kw-3,(h-14)/4-3,4,'key')
 yy=y+7+3*(h-14)/4;hh=(h-14)/4-3
 boxes=[(x+8,w*.15,'ctrl'),(x+10+w*.15,w*.15,'alt'),(x+12+w*.30,w*.40-12,'space'),(x+w*.72,w*.11,'arrows'),(x+w*.85,w*.12,'enter')]
 for xx,ww,name in boxes:
  d.rect(xx,yy,ww,hh,4,'accent_bg' if name==focus else 'key',name==focus)
  if name=='space':d.line(xx+ww*.35,yy+hh/2,ww*.3,'accent' if focus=='space' else 'line',1.8)
  if name=='enter':d.glyph('enter',xx+ww/2,yy+hh/2,18,focus=='enter')
  if name=='arrows':d.glyph('move',xx+ww/2,yy+hh/2,18,focus=='arrows')
  if name in ['ctrl','alt']:d.line(xx+9,yy+hh/2,ww-18,'accent' if name==focus else 'line',1.8)
 return {name:(xx+ww/2,yy+hh/2) for xx,ww,name in boxes}
def tools(d,x=28,y=78,w=304,focus=None):
 names=['keyboard','mouse','home','terminal','monitor','grid','list']
 for i,name in enumerate(names):
  xx=x+(i+.5)*w/7
  if name==focus:d.rect(xx-15,y-15,30,30,5,'accent_bg',True)
  d.glyph(name,xx,y,22,name==focus)
def apps(d,x,y,w=304,hi=False):
 for i,name in enumerate(['phone','chat','globe','terminal','play','monitor','grid']):
  xx=x+(i+.5)*w/7;d.circle(xx,y,14,'key','accent' if hi else None);d.glyph(name,xx,y,19,hi)
def bottom(d,focus=None):
 d.rect(16,24,328,184,18,'surface',stroke='line');lines(d,24,32,75,1)
 apps(d,28,43);d.line(33,62,280,'key',2);tools(d,focus=focus);return keyboard(d)
def status(d,expanded=False,hi=False):
 d.rect(20,24,320,86 if expanded else 38,12,'surface',hi,stroke='line')
 if expanded:
  d.glyph('terminal',40,49,24);lines(d,61,43,61,2,12);d.rect(155,35,170,31,6,'key');lines(d,167,46,120,2,10)
 yy=90 if expanded else 43
 for i in range(3):d.rect(30+i*43,yy-10,35,20,5,'key');d.line(38+i*43,yy,18,'line',1.8)
 for i in range(3):d.line(272+i*18,yy,9,'line',1.8)
def phone(d,x=120,y=10,style='docked',place='terminal',hi=False,minimal=False,style_focus=False):
 w=120;h=260;d.rect(x,y,w,h,18,None,stroke='line')
 if style=='docked':d.rect(x+4,y+4,w-8,h-8,14,'surface',style_focus)
 if not minimal:
  if style=='floating':d.rect(x+8,y+8,104,19,8,'surface',style_focus,stroke='line')
  d.line(x+15,y+18,23,'line',2);d.line(x+90,y+18,13,'line',2)
 top=y+9 if minimal else y+33;ph=238 if minimal else 148
 d.rect(x+8,top,104,ph,10,'terminal',hi,stroke='line')
 if place=='terminal':d.glyph('terminal',x+24,top+17,18);lines(d,x+37,top+17,55,3,12)
 elif place=='home':
  for a,b in [(0,0),(1,0),(0,1),(1,1)]:d.rect(x+17+a*46,top+15+b*50,38,41,7,'key');d.glyph('grid',x+36+a*46,top+35+b*50,22)
 else:
  d.rect(x+17,top+16,85,81,8,'key');d.glyph('monitor',x+60,top+50,32);lines(d,x+30,top+74,52,2,10)
 if not minimal:
  if style=='floating':d.rect(x+8,y+189,104,22,8,'surface',style_focus,stroke='line');d.rect(x+8,y+218,104,32,8,'surface',style_focus,stroke='line')
  for i,name in enumerate(['keyboard','terminal','grid']):d.glyph(name,x+26+i*34,y+201,18)
  for i in range(6):d.rect(x+12+i*16,y+225,13,10,3,'key')
  d.rect(x+32,y+240,45,7,3,'key')
 return (x+8,top,104,ph)
def strip(d,x=26,y=43,split=False,focus=None):
 names=['close','move','minimal'] if split else ['palette','layout','picture','minimal','gear','help']
 w=44*len(names)+12;d.rect(x,y,w,42,12,'surface',True)
 for i,n in enumerate(names):d.glyph(n,x+28+i*44,y+21,24,focus is None or focus==n)
def panel(d,x=35,y=35,w=290,h=145,icon=None,hi=False):
 d.rect(x,y,w,h,14,'surface',hi,stroke='line')
 if icon:d.glyph(icon,x+24,y+25,25,hi)
 lines(d,x+47,y+25,w-70,2,14)
 for i in range(2):d.rect(x+15,y+66+i*34,w-30,26,6,'key');lines(d,x+28,y+79+i*34,w-90,1)
def chip(d,x,y,name,hi=True):d.rect(x-18,y-18,36,36,8,'accent_bg' if hi else 'key',hi);d.glyph(name,x,y,24,hi)
def focus(d,s):d.focus=s;return d

TOPICS='places corners minimal palette status stats pinned_notifications dock az organize_apps widget pages copy_paste mouse_mode hold_terminal find_text hierarchy pictures text_size keyboard_swipe keyboard keyboard_layouts keys shortcuts space settings voice clipboard panes float_pane move_panes windows workspaces appearance_editor layout_editor themes tlstore on_device_ai scale display_apps display_keys distro_apps start setup touchpad fix_keyboard fix_dock fix_shortcuts fix_stats fix_support fix_display fix_action'.split()

def draw_original(t):
 d=Drawing(220)
 if t=='places':
  d.h=290
  # Home, Terminal, Display identified by glyphs, in their actual order.
  for x,p,g in [(14,'home','home'),(120,'terminal','terminal'),(226,'display','monitor')]:
   # Narrow companions are place slabs; the central phone locates the border.
   if p=='terminal':phone(d,x,18,hi=True)
   else:d.rect(x+8,51,92,148,10,'surface',stroke='line');d.glyph(g,x+54,110,32)
  d.motion([(128,132),(204,132)],True);d.step(112,105,1);d.step(209,105,2)
  return focus(d,'Terminal page border: hold until it sinks, then drag sideways; Home / Terminal / Display')
 if t=='corners':
  d.h=240;terminal(d,20,32,320,186);strip(d,27,43);d.touch(25,36,True)
  return focus(d,'Lone-pane corner and six-action corner tab: Appearance, Layout, Wallpaper, Minimal, Settings, help')
 if t=='minimal':
  d.h=290;phone(d,24,12);phone(d,216,12,hi=True,minimal=True);chip(d,179,127,'minimal');d.touch(179,127)
  return focus(d,'Minimal button and expanded place content')
 if t=='palette':
  terminal(d,20,20,320,180);panel(d,75,46,215,142,'terminal',True);d.touch(98,71)
  return focus(d,'Terminal command palette')
 if t in ['status','stats','pinned_notifications','windows','workspaces','fix_stats']:
  status(d,True,t=='status');terminal(d,20,122,320,76)
  if t=='status':d.motion([(180,117),(180,168)],kind='swipe');d.rect(160,113,40,4,2,'accent',True)
  elif t=='pinned_notifications':d.rect(155,35,170,31,6,'accent_bg',True);d.glyph('chat',174,50,20,True);lines(d,193,46,102,2,9);d.touch(281,51)
  elif t in ['stats','fix_stats']:
   d.rect(251,78,77,25,5,'accent_bg',True)
   for i,h in enumerate([6,15,11,20]):d.rect(262+i*15,99-h,6,h,2,'accent')
   if t=='fix_stats':d.touch(281,90,True);chip(d,292,153,'gear')
  elif t=='windows':d.rect(30,80,35,20,5,'accent_bg',True);d.rect(73,80,35,20,5,'accent_bg',True);d.touch(89,90)
  else:d.rect(116,80,35,20,5,'accent_bg',True);d.glyph('plus',133,90,18,True);d.touch(133,90)
  return focus(d,{'status':'Top border grabber and unfolded status bar','stats':'Status resource readings','fix_stats':'Resource readings and their settings','pinned_notifications':'Notification card beside clock','windows':'Window pills in status bar','workspaces':'Workspace control beside window pills'}[t])
 if t in ['dock','az','organize_apps']:
  d.rect(16,70,328,134,16,'surface',stroke='line');terminal(d,24,12,312,50);apps(d,28,95);tools(d,y=181)
  if t=='dock':d.rect(24,76,312,38,8,None,True);d.motion([(180,99),(180,150)],kind='drag');d.rect(76,125,208,40,10,'key');apps(d,84,145,192)
  elif t=='organize_apps':d.rect(24,76,312,38,8,None,True);d.touch(281,95,True);chip(d,282,139,'folder')
  else:
   d.rect(24,122,312,30,6,'accent_bg',True)
   # Stroke lettering shared with Android, no font dependency.
   letters=['A','D','G','J','M','P','S','V','Z']
   alphabet={'A':'M0 12L4 0l4 12M2 7h4','D':'M0 0v12h3q7-6 0-12Z','G':'M8 2Q0-4 0 6q0 9 8 4V6H4','J':'M8 0v9q0 6-7 1','M':'M0 12V0l4 6 4-6v12','P':'M0 12V0h4q8 6 0 6H0','S':'M8 1Q0-3 0 3q0 3 4 3q8 0 4 6H0','V':'M0 0l4 12L8 0','Z':'M0 0h8L0 12h8'}
   for i,l in enumerate(letters):d.items.append(dict(group=(34+i*34,131,1),d=alphabet[l],fill=None,stroke='accent',width=1.8,alpha=1))
   d.motion([(58,146),(174,146),(174,93)])
  return focus(d,{'dock':'Apps row pulled away from dock edge to reveal drawer','az':'A–Z index: scrub then drag up to matching app','organize_apps':'Hold apps row to choose apps or organize pins'}[t])
 if t in ['widget','pages']:
  d.h=280;phone(d,120,10,place='home')
  if t=='widget':d.rect(137,58,84,88,8,'key',True);chip(d,221,58,'close');d.touch(221,58)
  else:strip(d,35,35,focus='layout');chip(d,286,103,'plus');d.touch(286,103);d.rect(137,124,84,56,7,None,True);d.glyph('grid',179,150,28,True)
  return focus(d,'Selected widget remove chip' if t=='widget' else 'Home corner tab add-page and grid controls')
 if t in ['copy_paste','hold_terminal','find_text','hierarchy','pictures','text_size']:
  terminal(d,20,20,320,180)
  if t=='copy_paste':
   d.rect(49,65,135,30,4,'accent_bg',True);lines(d,57,76,115,2,10);d.circle(49,97,5,'accent');d.circle(184,97,5,'accent');chip(d,233,80,'copy');chip(d,285,80,'paste');d.touch(107,79,True)
  elif t=='hold_terminal':panel(d,84,58,222,132,'list',True);d.touch(51,63,True)
  elif t=='find_text':d.rect(35,34,290,33,7,'key',True);d.glyph('search',55,50,22,True);lines(d,76,50,142,1);d.rect(68,98,81,19,4,'accent_bg',True);d.touch(289,50)
  elif t=='hierarchy':
   d.glyph('folder',60,70,26,True);d.path('M60 88v64h33M60 113h33',None,'accent',2)
   for x,y in [(110,113),(110,152)]:d.glyph('folder',x,y,24,True);d.line(x+20,y,94,'key')
   d.rect(40,50,240,122,8,None,True)
  elif t=='pictures':d.rect(63,68,198,105,10,'key',True);d.glyph('picture',162,114,64,True)
  else:
   lines(d,40,70,95,3,11);lines(d,191,71,114,3,21);d.rect(179,48,143,95,8,None,True);chip(d,115,155,'plus');chip(d,169,155,'undo');d.touch(115,155)
  return focus(d,{'copy_paste':'Selected terminal text and copy / paste actions','hold_terminal':'Terminal hold opens action list','find_text':'Search field and matching terminal text','hierarchy':'Indented terminal file hierarchy','pictures':'Inline terminal image','text_size':'Larger terminal lines and palette font actions'}[t])
 if t in ['keyboard_swipe','fix_keyboard']:
  d.h=240;d.rect(16,20,328,209,18,'surface',stroke='line');terminal(d,24,28,312,105);keyboard(d,y=140,h=80);d.rect(159,130,42,4,2,'accent',True);d.motion([(181,132),(181,75)],kind='swipe')
  if t=='fix_keyboard':chip(d,290,73,'gear',False)
  return focus(d,'Bottom page border swipe raises keyboard'+('; keyboard settings if wrong keyboard opens' if t=='fix_keyboard' else ''))
 if t in ['keyboard','mouse_mode','keys','shortcuts','space','settings','voice','clipboard','display_keys','fix_shortcuts']:
  k=bottom(d,{'keyboard':'keyboard','mouse_mode':'mouse','display_keys':'monitor'}.get(t))
  if t in ['keyboard','mouse_mode']:d.touch(28+(.5 if t=='keyboard' else 1.5)*304/7,78)
  elif t=='keys':
   for name in ['ctrl','alt']:
    x,y=k[name];d.rect(x-21,y-9,42,18,4,'accent_bg',True);d.line(x-9,y,18,'accent',1.8)
   d.touch(*k['ctrl']);d.glyph('lock',131,160,17,True)
  elif t in ['shortcuts','fix_shortcuts']:
   d.rect(27,171,99,21,4,'accent_bg',True);d.touch(77,181,True);d.rect(42,24,271,107,10,'surface',True)
   for i in range(3):d.rect(54,35+i*30,42,21,4,'key');lines(d,109,46+i*30,177,1)
  elif t=='space':d.rect(128,171,116,21,4,'accent_bg',True);d.motion([k['space'],(225,181)])
  elif t=='settings':chip(d,118,157,'gear');d.motion([k['alt'],(118,157)],kind='swipe')
  elif t=='voice':
   d.motion([k['enter'],(311,122)],kind='swipe');d.rect(85,24,242,96,12,'surface',True);d.glyph('mic',105,45,23,True);lines(d,128,44,157,3,12)
   for x,g in [(195,'undo'),(243,'copy'),(291,'check')]:d.glyph(g,x,99,24,g=='check')
   d.glyph('close',309,40,19)
  elif t=='clipboard':
   d.motion([k['ctrl'],(22,207)],kind='swipe');d.rect(82,20,231,112,10,'surface',True)
   for i in range(3):d.glyph('copy',103,41+i*32,19,True);lines(d,123,41+i*32,167,1)
  else:d.rect(27,171,297,21,5,None,True);d.touch(*k['arrows'])
  return focus(d,{'keyboard':'Keyboard toggle key','mouse_mode':'Mouse-mode extra key','keys':'Ctrl and Alt tap-latched modifier keys','shortcuts':'Modifier keys and shortcut hints','fix_shortcuts':'Modifier keys and shortcut hints','space':'Space key directional actions','settings':'Alt swipe toward settings cog','voice':'Enter upward dictation swipe and pending transcript with insert action','clipboard':'Ctrl down-left swipe and clipboard history','display_keys':'Display key and navigation keys'}[t])
 if t=='keyboard_layouts':
  d.h=260;tools(d,y=32,focus='keyboard');d.motion([(50,32),(50,8)],kind='swipe')
  for x,y,w,h in [(20,75,145,75),(192,76,125,75),(20,166,143,76),(196,166,143,76)]:keyboard(d,x,y,w,h,card=True,split=y>160)
  d.rect(18,73,149,79,12,None,True);d.rect(190,74,129,79,12,None,True);d.rect(18,164,147,80,12,None,True);d.rect(194,164,147,80,12,None,True)
  return focus(d,'Docked, floating and split keyboard forms')
 if t in ['panes','float_pane','move_panes']:
  d.h=260;d.rect(20,20,320,220,18,'surface',stroke='line');terminal(d,28,28,148,150,t!='float_pane');terminal(d,184,28,148,150)
  tools(d,y=213,focus='grid' if t=='panes' else None)
  if t=='panes':d.touch(28+5.5*304/7,213)
  elif t=='float_pane':terminal(d,122,97,168,99,True)
  else:d.motion([(105,68),(246,113)],True);d.glyph('move',105,68,24,True)
  return focus(d,{'panes':'Split extra key and two rounded terminal inserts','float_pane':'Floating terminal pane over split','move_panes':'Pane move action and destination'}[t])
 if t=='layout_editor':
  d.h=300;phone(d,35,8,style='docked',hi=True,style_focus=True);phone(d,205,8,style='floating',hi=True,style_focus=True)
  d.rect(35,277,120,17,6,'accent_bg',True);d.glyph('layout',51,285,17,True);d.line(67,285,72,'accent',1.8)
  d.rect(205,277,120,17,6,'accent_bg',True);d.glyph('layout',221,285,17,True);d.line(237,285,72,'accent',1.8)
  return focus(d,'Layout Style: Docked shared glass frame / Floating separated cards; rounded panes in both')
 if t=='appearance_editor':
  d.h=300;phone(d,120,8);d.rect(20,235,320,55,12,'surface',stroke='line');d.line(43,263,274,'accent_bg',6)
  for i in range(5):d.circle(43+i*68.5,263,4,'line')
  d.circle(317,263,7,'accent_bg','accent');d.motion([(43,263),(317,263)])
  return focus(d,'Appearance five-stop slider ending at Custom')
 if t=='themes':
  panel(d,26,22,308,178,'gear');d.rect(41,79,278,104,10,None,True)
  for x,c in [(72,'accent'),(120,'secondary'),(168,'key')]:d.circle(x,105,15,c,'accent')
  d.glyph('picture',224,104,32,True);d.glyph('terminal',279,104,32,True);lines(d,59,149,230,2)
  return focus(d,'Look settings: palette, wallpaper, fonts and icons')
 if t=='tlstore':
  terminal(d,20,20,320,180);d.rect(40,43,277,37,6,'accent_bg',True);d.glyph('terminal',57,62,24,True);lines(d,79,62,211,1)
  d.glyph('download',179,115,30,True);chip(d,179,165,'chip')
  return focus(d,'Terminal install command and downloaded extra tool')
 if t=='on_device_ai':
  d.h=280;phone(d,120,10);d.rect(136,66,88,90,10,'accent_bg',True);d.glyph('chip',180,94,37,True);d.glyph('terminal',180,136,27,True);d.path('M180 111v9',None,'accent',2)
  chip(d,286,94,'gear');d.path('M224 94h42',None,'line',1.8)
  return focus(d,'AI model and terminal client inside phone; model settings')
 if t in ['scale','display_apps','distro_apps','start','setup','touchpad','fix_display']:
  d.rect(20,20,320,180,15,'surface',stroke='line');d.glyph('monitor',43,41,24);d.rect(35,58,290,125,10,'key',t in ['scale','fix_display'],stroke='line')
  if t=='scale':d.glyph('scale',180,119,65,True)
  elif t=='display_apps':
   for i,n in enumerate(['globe','folder','terminal']):chip(d,89+i*89,119,n,i==1)
   d.touch(178,119)
  elif t=='distro_apps':d.glyph('terminal',79,99,28,True);lines(d,104,100,171,2);d.glyph('download',180,146,28,True);d.rect(51,79,260,43,8,None,True)
  elif t=='start':chip(d,180,118,'play');d.touch(180,118)
  elif t=='setup':
   panel(d,62,58,237,125,'download',True);d.rect(76,148,144,5,2,'line');d.rect(76,148,95,5,2,'accent');d.glyph('check',268,148,24,True)
  elif t=='touchpad':
   d.rect(69,77,222,90,12,'surface',True);d.motion([(136,124),(216,106)]);d.path('M260 65l-7 25 11-4 6 12 5-3-6-12 9-2Z','line',None)
  else:chip(d,180,119,'gear');d.touch(180,119)
  return focus(d,{'scale':'Display scaling control','display_apps':'Linux desktop app launcher','distro_apps':'Install Linux apps from terminal','start':'Start Linux display','setup':'Display setup progress and completion','touchpad':'Touchpad region moves desktop pointer','fix_display':'Display settings and recovery'}[t])
 if t=='fix_dock':
  d.h=280;phone(d,120,8);d.rect(33,205,294,62,12,'surface',True)
  for i,n in enumerate(['terminal','grid','keyboard','list']):chip(d,68+i*75,235,n,i==1)
  d.touch(143,235);d.glyph('eye_off',143,208,19)
  return focus(d,'Layout restore tray: tap missing bar chip')
 if t=='fix_support':
  panel(d,35,24,290,177,'help',True);d.glyph('chat',86,126,39,True);d.glyph('copy',161,126,35);d.glyph('terminal',235,126,35);d.touch(86,126)
  return focus(d,'Help / support and diagnostic report')
 if t=='fix_action':
  terminal(d,20,20,320,180);strip(d,28,37,focus='help');d.touch(277,58);d.rect(48,108,261,67,10,'key',True);d.glyph('help',71,133,24,True);lines(d,94,132,186,2,15)
  return focus(d,'Corner-tab help to identify an unfamiliar action')
 raise ValueError(t)

# Second-pass geometry. Unlisted topics still use the original drawing verbatim.
REMOVED={'hold_terminal','start','setup'}
TOPICS=[('gui_apps' if t=='distro_apps' else t) for t in TOPICS if t not in REMOVED]
HELP_STRINGS=dict(line.split(': ',1) for line in (ROOT/'help-strings.txt').read_text().splitlines() if ': ' in line)
REVISED=set('appearance_editor hierarchy space palette float_pane scale text_size display_apps display_keys touchpad fix_stats fix_support fix_display fix_action mouse_mode shortcuts clipboard move_panes windows workspaces tlstore gui_apps'.split())

def arrow(d,a,b,hi=False):
 color='accent' if hi else 'line'
 dx=b[0]-a[0];dy=b[1]-a[1];n=math.hypot(dx,dy);ux,uy=dx/n,dy/n
 d.path(f'M{a[0]} {a[1]}L{b[0]} {b[1]}M{b[0]-7*ux+3.5*uy} {b[1]-7*uy-3.5*ux}L{b[0]} {b[1]}L{b[0]-7*ux-3.5*uy} {b[1]-7*uy+3.5*ux}',None,color,2.4 if hi else 1.8)

def desktop(d,x=20,y=20,w=320,h=145,hi=False):
 d.rect(x,y,w,h,14,'surface',hi,stroke='line');d.glyph('monitor',x+20,y+19,22)
 d.rect(x+12,y+34,w-24,h-46,9,'key',stroke='line')
 browser(d,x+32,y+46,w-75,h-69)

def browser(d,x,y,w,h,hi=False):
 d.rect(x,y,w,h,8,'surface',hi,stroke='line');d.glyph('globe',x+14,y+13,18,hi)
 d.line(x+31,y+13,w-47,'line',2);d.line(x+9,y+27,w-18,'line',1.8)
 lines(d,x+13,y+42,w-30,2,12)

def pointer(d,x,y,hi=True):
 d.path(f'M{x} {y}l-3 22 7-6 5 10 5-3-5-9 9-1Z','accent' if hi else 'line',None)

def search_sheet(d,x=70,y=25,w=250,h=143,disabled=False):
 d.rect(x,y,w,h,12,'surface',True);d.rect(x+12,y+12,w-24,28,6,'key',True)
 d.glyph('search',x+26,y+26,20,True);d.line(x+45,y+26,w-72,'line')
 for i in range(3):
  yy=y+60+i*29;d.rect(x+12,yy-11,w-24,23,5,'key')
  d.line(x+24,yy,w-70,'line' if disabled and i==1 else 'muted',2)
  if disabled and i==1:d.line(x+28,yy+7,w-111,'line',1.8);d.glyph('lock',x+w-29,yy,17)

def sessions(d,x=78,y=35,w=245,h=163):
 d.rect(x,y,w,h,12,'surface',True)
 # Two sessions, each containing a window with two pane rows; lines only.
 scale=(h-20)/148
 for base in [y+12,y+12+74*scale]:
  d.line(x+16,base,w-45,'muted',3)
  d.path(f'M{x+23} {base+9*scale}v{17*scale}h12M{x+43} {base+30*scale}v{27*scale}M{x+43} {base+41*scale}h12M{x+43} {base+57*scale}h12',None,'line',1.8)
  d.line(x+39,base+23*scale,w-82,'line',3)
  for yy in [base+41*scale,base+57*scale]:d.line(x+60,yy,w-106,'line',2)

def compact(d,session=False):
 d.rect(20,20,320,36,10,'surface',stroke='line')
 d.rect(30,28,18,20,4,'accent_bg' if session else 'key',session);d.line(36,38,6,'accent' if session else 'line',1.8)
 for i in range(3):
  x=55+i*67;d.rect(x,28,59,20,5,'key' if session else 'accent_bg',not session);d.line(x+9,38,24,'line' if session else 'accent',1.8)
 d.glyph('plus',274,38,18);d.line(304,38,18,'line',1.8)

def missing(d,icon='monitor'):
 d.rect(20,45,78,102,10,'terminal',stroke='line');d.glyph(icon,59,73,29)
 d.rect(33,101,52,26,5,None,stroke='line');d.glyph('close',59,114,18)
 arrow(d,(108,97),(139,97),True)

def recovery(d,kind):
 missing(d,'chip' if kind=='fix_stats' else 'help' if kind=='fix_support' else 'monitor')
 d.rect(153,20,187,181,12,'surface',True)
 d.glyph('download' if kind=='fix_display' else 'gear',174,40,23,True);lines(d,194,40,122,1)
 d.rect(164,62,164,32,6,'key',True)
 d.glyph('list' if kind=='fix_stats' else 'help' if kind=='fix_support' else 'monitor',181,78,21,True);d.line(201,78,105,'accent',2)
 if kind=='fix_stats':
  for i,icon in enumerate(['chip','list','globe']):
   yy=116+i*29;d.glyph(icon,181,yy,20);d.line(200,yy,57,'line',2);d.rect(286,yy-8,29,16,8,'accent_bg',True);d.circle(307,yy,5,'accent')
  d.glyph('lock',268,174,17)
 elif kind=='fix_support':
  for i,icon in enumerate(['terminal','list','chat']):d.glyph(icon,181,116+i*29,21,True);d.line(201,116+i*29,111,'line',2)
 else:
  for i,icon in enumerate(['download','play']):d.glyph(icon,182,118+i*42,23,True);d.line(203,118+i*42,82,'line',2);d.glyph('check',311,118+i*42,18,True)
 d.touch(307,78)

def mouse_frame(end=False):
 d=Drawing(240);d.rect(20,20,320,200,15,'surface',stroke='line')
 d.rect(29,29,302,24,7,'key');lines(d,40,41,92,1);d.line(288,41,28,'line',2)
 border=218 if end else 153
 d.rect(29,61,border-35,149,9,'terminal',True);d.items[-1]['name']='left_pane'
 d.rect(border+6,61,325-border,149,9,'terminal',stroke='line');d.items[-1]['name']='right_pane'
 lines(d,42,80,85,6,17);lines(d,239,80,62,6,17)
 d.path(f'M{border} 65V206',None,'accent',2.4);d.items[-1]['name']='divider'
 d.circle(border,133,7,'accent',None,alpha=.85);d.items[-1]['name']='finger'
 d.circle(border,133,13,None,'accent');d.items[-1]['name']='pulse'
 arrow(d,(153,176),(218,176),True)
 d.gesture='drag';return focus(d,'Brief press on TUI pane border sends mouse press; drag resizes the two panes')

def animated_mouse(first,last):
 # Inline vector and animators: synchronized 2.5-second cycle, including press,
 # drag, release and reset at the cycle boundary. Static vector is exactly the first keyframe.
 ns='http://schemas.android.com/apk/res/android'
 aapt='http://schemas.android.com/aapt'
 parts=[f'<animated-vector xmlns:android="{ns}" xmlns:aapt="{aapt}"><aapt:attr name="android:drawable">',first.vector(),'</aapt:attr>']
 starts={p['name']:p for p in first.items if 'name' in p};ends={p['name']:p for p in last.items if 'name' in p}
 for name,p in starts.items():
  parts.append(f'<target android:name="{name}"><aapt:attr name="android:animation"><set android:ordering="together">')
  parts.append(f'<objectAnimator android:propertyName="pathData" android:valueType="pathType" android:valueFrom="{p["d"]}" android:valueTo="{ends[name]["d"]}" android:duration="2500" android:repeatCount="-1" android:repeatMode="restart"><aapt:attr name="android:interpolator"><pathInterpolator android:pathData="M0 0L0.22 0L0.72 1L1 1"/></aapt:attr></objectAnimator>')
  if name=='pulse':
   parts.append('<objectAnimator android:duration="2500" android:repeatCount="-1"><propertyValuesHolder android:propertyName="strokeAlpha" android:valueType="floatType">')
   for fraction,val in [(0,1),(.12,.3),(.22,1),(.72,.35),(.9,0),(1,1)]:parts.append(f'<keyframe android:fraction="{fraction}" android:value="{val}"/>')
   parts.append('</propertyValuesHolder></objectAnimator>')
  parts.append('</set></aapt:attr></target>')
 parts.append('</animated-vector>');return '\n'.join(parts)

def draw(t):
 if t not in REVISED:return draw_original(t)
 if t=='mouse_mode':return mouse_frame()
 d=Drawing(220)
 if t=='appearance_editor':
  d.h=300;phone(d,120,8,hi=True,style_focus=True)
  # Same 120 x 260 editor frame as layout_editor; control sheet at its bottom.
  d.rect(20,226,320,68,12,'surface',stroke='line')
  d.rect(31,234,256,20,8,'key');d.rect(31,234,128,20,8,'accent_bg',True)
  d.glyph('palette',47,244,17,True);d.line(63,244,78,'accent',1.8)
  d.glyph('layout',175,244,17);d.line(192,244,77,'line',1.8);d.glyph('check',315,244,20)
  d.line(43,275,274,'accent_bg',5)
  for i in range(5):d.circle(43+i*68.5,275,4,'line')
  d.circle(317,275,7,'accent_bg','accent');d.motion([(43,275),(317,275)])
  return focus(d,'Appearance segment and Look slider: Clear / Mist / Tint / Solid / Custom')
 if t=='hierarchy':
  terminal(d,20,20,320,180);sessions(d)
  return focus(d,'Sessions browser sheet: sessions contain windows, windows contain panes')
 if t in ['palette','clipboard','space','shortcuts']:
  if t=='shortcuts':
   d.h=290;d.rect(20,10,320,270,16,'surface',stroke='line');terminal(d,28,18,304,144)
   k=keyboard(d,y=174,h=96)
   for name in ['ctrl','alt']:
    x,y=k[name];d.rect(x-22,y-9,44,18,4,'accent_bg',True);d.line(x-11,y,22,'accent',1.8)
   d.rect(314,24,16,107,4,'surface',True)
   for i in range(8):d.line(318,34+i*12,8,'accent',1.8)
   return focus(d,'Ctrl and Alt latched; narrow shortcut hints at top-right, 5% of screen width')
  k=bottom(d)
  if t=='palette':
   search_sheet(d,75,15,247,124);d.motion([k['space'],(k['space'][0],149)],kind='swipe')
   return focus(d,'Space upward swipe opens searchable command palette')
  if t=='clipboard':
   d.rect(98,16,233,125,12,'surface',True)
   for i in range(3):d.glyph('copy',119,40+i*35,20,True);lines(d,140,40+i*35,169,2,9)
   d.motion([k['ctrl'],(88,145)],kind='swipe')
   return focus(d,'Ctrl northeast swipe opens clipboard history')
  # Isolate a broad space key so every direction reads clearly.
  d=Drawing(240);d.rect(55,89,250,78,12,'key',True);d.line(146,128,68,'line',2)
  for dest in [(85,44),(275,44),(85,210),(275,210)]:arrow(d,(180,128),dest,dest==(275,44))
  arrow(d,(140,128),(98,128));arrow(d,(220,128),(262,128))
  arrow(d,(180,88),(180,29));d.finger(180,128)
  for x,y,icon in [(70,25,'terminal'),(290,25,'terminal'),(70,224,'list'),(290,224,'list'),(180,17,'search')]:d.glyph(icon,x,y,22,(x,y)==(290,25))
  d.gesture='swipe';return focus(d,'Space swipes: horizontal cursor; NW left window, NE right window (highlighted), SW previous session, SE next session, up palette')
 if t=='text_size':
  terminal(d,20,20,320,166,True);lines(d,48,69,101,3,17)
  d.motion([(172,111),(117,87)]);d.motion([(196,111),(251,135)])
  d.rect(103,193,70,23,5,'key',True);d.line(112,205,21,'accent',1.8);d.path('M153 199v12M147 205h12',None,'accent',1.8)
  d.rect(187,193,70,23,5,'key',True);d.line(196,205,21,'accent',1.8);d.path('M231 205h12',None,'accent',1.8)
  return focus(d,'Pinch apart / together changes pane terminal font; Ctrl plus / minus key combinations')
 if t in ['float_pane','move_panes']:
  d.h=260;d.rect(20,20,320,220,18,'surface',stroke='line');terminal(d,28,28,148,179);terminal(d,184,28,148,179)
  if t=='float_pane':
   terminal(d,116,100,169,112,True);d.glyph('move',274,110,18,True);d.motion([(274,110),(308,72)])
   return focus(d,'Pane lifted over split and moved by its corner handle; no help string supplied')
  strip(d,45,53,True,'move');d.touch(29,29,True);d.step(49,24,1)
  d.motion([(117,74),(252,144)]);d.step(154,103,2);d.rect(193,113,130,80,9,None,True)
  return focus(d,'1 hold split-pane corner; 2 drag move icon from revealed strip to another pane')
 if t in ['windows','workspaces']:
  compact(d,t=='workspaces');terminal(d,20,65,320,137)
  if t=='workspaces':sessions(d,76,70,247,134);d.touch(39,38)
  else:d.touch(151,38)
  return focus(d,'Small session indicator opens sessions manager' if t=='workspaces' else 'Window chips after small session indicator in compact status bar')
 if t in ['fix_stats','fix_support','fix_display']:
  recovery(d,t);return focus(d,{'fix_stats':'Missing status widgets → Settings / Status bar: enable widgets, permissions if needed','fix_support':'Still stuck → Settings / About and support: documentation, diagnostics, support','fix_display':'Display will not start → Set up the display: install packages and run server'}[t])
 if t=='fix_action':
  terminal(d,20,20,320,180);d.rect(31,54,51,32,5,'key');d.glyph('lock',56,70,22)
  arrow(d,(91,70),(123,70),True);search_sheet(d,135,27,195,159,True);d.touch(279,112)
  return focus(d,'Unavailable action → command palette: greyed-out action and prerequisite detail')
 if t=='scale':
  desktop(d,20,20,320,180);d.rect(305,63,14,119,7,'accent_bg',True);d.circle(312,135,5,'accent');d.motion([(312,135),(312,87)])
  return focus(d,'Display edge scale rail; drag along rail to resize desktop')
 if t=='touchpad':
  d.h=280;desktop(d,20,16,320,148);pointer(d,200,88);arrow(d,(213,97),(252,79),True)
  d.rect(35,178,290,87,12,'surface',True);d.motion([(128,228),(201,205)])
  return focus(d,'X11 browser above touchpad: one-finger drag moves pointer in matching direction')
 if t=='display_apps':
  desktop(d,20,66,320,136);d.rect(20,20,320,37,10,'surface',stroke='line')
  for i,icon in enumerate(['globe','terminal','folder']):
   x=29+i*101;d.rect(x,28,94,22,6,'accent_bg' if i==0 else 'key',i==0);d.glyph(icon,x+13,39,18,i==0);d.line(x+28,39,38,'line',1.8);d.glyph('close',x+80,39,15,i==0)
  d.touch(61,39);d.step(39,64,1);d.step(117,64,2)
  return focus(d,'Running Display app chips: tap to bring forward, then × to close')
 if t=='display_keys':
  d.h=280;desktop(d,20,12,320,126,True);k=keyboard(d,y=168,h=100)
  d.rect(20,143,320,25,7,'surface',stroke='line');d.rect(130,145,100,21,5,'accent_bg',True);d.glyph('terminal',146,155,19,True);d.line(162,155,51,'accent',1.8);d.touch(212,155)
  arrow(d,(80,187),(80,108));d.glyph('enter',80,94,22)
  return focus(d,'Focused desktop app receives typing; tap Terminal on extra keys to return to launcher')
 if t=='gui_apps':
  d.h=260;terminal(d,20,20,320,73);d.rect(30,31,300,38,7,'accent_bg',True);d.glyph('terminal',47,50,23,True);lines(d,68,46,234,2,11)
  arrow(d,(180,94),(180,118),True);desktop(d,20,124,320,122,True)
  return focus(d,'Terminal install from termux-x11 repository → GUI app window on Display')
 if t=='tlstore':
  d.h=260;terminal(d,20,20,320,67);d.rect(30,31,300,35,7,'accent_bg',True);d.glyph('terminal',47,48,22,True);d.line(68,48,234,'accent',2)
  d.rect(42,104,276,140,12,'surface',stroke='line')
  for i in range(3):
   yy=128+i*44;d.rect(54,yy-14,252,34,6,'accent_bg' if i==1 else 'key',i==1);d.glyph('chip',73,yy+1,22,i==1);d.line(94,yy,147,'line',2);d.glyph('download',283,yy,23,i==1)
   if i==1:d.line(95,yy+10,100,'line',3);d.line(95,yy+10,61,'accent',3)
  arrow(d,(180,87),(180,103),True)
  return focus(d,'tlstore terminal command installs prebuilt launcher binaries from package list with downloads')
 raise ValueError(t)

def render(svg,path,width,height):
 try:
  import cairosvg
  cairosvg.svg2png(bytestring=svg.encode(),write_to=str(path),output_width=width,output_height=height)
 except ImportError:
  if not shutil.which('rsvg-convert'):raise RuntimeError('Install cairosvg or rsvg-convert to rasterize')
  subprocess.run(['rsvg-convert','-w',str(width),'-h',str(height),'-o',str(path)],input=svg.encode(),check=True)
def validate(drawings):
 import struct
 def png_size(path):
  data=path.read_bytes();assert data[:8]==b"\x89PNG\r\n\x1a\n"
  return struct.unpack(">II",data[16:24])
 android='{http://schemas.android.com/apk/res/android}'
 palette=set(C.values())
 assert len(drawings)==49 and len(set(TOPICS))==49
 assert len(list(OUT.glob('*.svg')))==50  # includes sheet.svg
 assert len(list((OUT/'png').glob('*.png')))==50
 assert len(list((OUT/'drawable').glob('*.xml')))==50
 for t,d in drawings.items():
  assert 140<=d.h<=300
  svg=ET.parse(OUT/(t+'.svg')).getroot()
  assert svg.get('viewBox')==f'0 0 360 {d.h}'
  assert all(e.tag.split('}')[-1] in {'svg','g','path'} for e in svg.iter())
  vector=ET.parse(OUT/'drawable'/('help_diagram_'+t+'.xml')).getroot()
  assert vector.get(android+'viewportWidth')=='360'
  assert vector.get(android+'viewportHeight')==str(d.h)
  assert all(e.tag in {'vector','group','path'} for e in vector.iter())
  for root in [svg,vector]:
   for e in root.iter():
    for key,value in e.attrib.items():
     if key in {'fill','stroke',android+'fillColor',android+'strokeColor'}:assert value=='none' or value in palette
  for item in d.items:
   if item['stroke']:assert item['width']*(item.get('group',(0,0,1))[2])>=1.5
  assert png_size(OUT/'png'/(t+'.png'))==(720,d.h*2)
 for t in REMOVED|{'distro_apps'}:
  assert not (OUT/(t+'.svg')).exists()
  assert not (OUT/'png'/(t+'.png')).exists()
  assert not (OUT/'drawable'/('help_diagram_'+t+'.xml')).exists()
 anim=ET.parse(OUT/'drawable/help_anim_mouse_mode.xml').getroot()
 assert anim.tag=='animated-vector'
 vector=anim.find('./{http://schemas.android.com/aapt}attr/vector')
 static=ET.parse(OUT/'drawable/help_diagram_mouse_mode.xml').getroot()
 vector.tail=None;static.tail=None
 assert ET.tostring(vector)==ET.tostring(static)
 names={e.get(android+'name') for e in vector.iter() if e.get(android+'name')}
 targets=anim.findall('target');assert len(targets)==5
 for target in targets:
  assert target.get(android+'name') in names
  for animator in target.iter('objectAnimator'):
   assert animator.get(android+'duration')=='2500' and animator.get(android+'repeatCount')=='-1'
  for animator in target.iter('objectAnimator'):
   if animator.get(android+'valueType')=='pathType':
    start=animator.get(android+'valueFrom');end=animator.get(android+'valueTo')
    assert re.findall('[A-Za-z]',start)==re.findall('[A-Za-z]',end)
    assert start==next(e.get(android+'pathData') for e in vector.iter('path') if e.get(android+'name')==target.get(android+'name'))
  for holder in target.iter('propertyValuesHolder'):
   frames=list(holder);assert frames[0].get(android+'value')==frames[-1].get(android+'value')
   if holder.get(android+'valueType')=='pathType':
    commands=[re.findall('[A-Za-z]',f.get(android+'value')) for f in frames]
    assert all(c==commands[0] for c in commands)  # morph-compatible path commands
 assert png_size(OUT/'png/mouse_mode_end.png')==(720,480)
 assert len(json.loads((OUT/'index.json').read_text()))==49
 print('Validation passed: 49 diagrams, 50 previews, palette, no text, dimensions, paths, removals, inline animation and morph compatibility.')

REVIEW="""# Review notes — second pass

Regenerate and validate with `python out/generate.py`. This script is the single
source for diagrams, the animation, previews, index, sheet and these notes.

The 49 topics follow PROMPT.md, the real help strings, and PROMPT2.md overrides.
Removed hold_terminal, start and setup; distro_apps is now gui_apps. The sheet
contains all 49 static first frames plus mouse_mode_end (50 cells).

Reviewed every changed diagram on the rebuilt sheet:
- Appearance uses layout_editor's 120×260 phone frame, with editor above the
  lower Appearance segment and five-stop Look rail.
- Hierarchy and workspaces show session → window → pane trees inside sheets.
- Space shows all four diagonals, horizontal cursor motion and upward palette;
  NE is highlighted. Palette opens by an upward space swipe; clipboard uses NE Ctrl.
- Text size shows two fingers moving apart and Ctrl plus/minus path keycaps.
  Scale shows the Display edge rail, separate from terminal font sizing.
- Display apps shows running chips and close glyphs; Display keys routes typing
  to Linux with the extra-row Terminal return key. Touchpad mirrors finger and
  pointer directions over a browser mockup. GUI apps shows install → Display.
- Missing widgets points to Settings / Status bar toggles and permission;
  support points to Settings / About and support; failed Display points to
  package/server setup; unavailable actions point to palette prerequisites.
- Mouse mode shows a brief press and border drag in a two-pane TUI. The inline
  AnimatedVectorDrawable pulses the ring and morphs both panes, divider and
  finger, looping every 2500 ms. Static vector and PNG are the first frame;
  mouse_mode_end previews the resized panes. The optional editor beat is omitted
  to keep the pane-resizing gesture readable.
- Shortcuts uses a top-right hint strip at 5% of screen width and two latched keys.
- Move panes shows hold corner (1), then drag the move icon in its strip (2).
- Windows uses compact status chips; workspaces highlights the smaller session
  chip. tlstore shows a binary package list, downloads and installation progress.

No float_pane string exists in the supplied input. It uses the requested fallback:
a lifted floating card over a split, dragged by its corner handle. No other
second-pass interpretation remains uncertain. Existing unlisted diagrams retain
their first-pass interpretations and are byte-for-byte unchanged (81 artifacts
across 27 topics verified against the pre-edit hashes).

Validation checks output counts, dimensions, palette, path-only artwork, minimum
strokes, removals, index, animation targets, identical inline/static first frames,
and morph command compatibility. Android runtime playback was not available;
animation structure was validated as XML, not on a device.
"""

def main():
 for sub in ['drawable','png']:(OUT/sub).mkdir(parents=True,exist_ok=True)
 for t in REMOVED|{'distro_apps'}:
  for path in [OUT/(t+'.svg'),OUT/'png'/(t+'.png'),OUT/'drawable'/('help_diagram_'+t+'.xml')]:path.unlink(missing_ok=True)
 drawings={t:draw(t) for t in TOPICS};index=[]
 for t,d in drawings.items():
  (OUT/(t+'.svg')).write_text(d.svg());(OUT/'drawable'/('help_diagram_'+t+'.xml')).write_text(d.vector());render(d.svg(True),OUT/'png'/(t+'.png'),720,d.h*2)
  index.append(dict(topic=t,height=d.h,focus=d.focus,gesture=d.gesture))
 (OUT/'index.json').write_text(json.dumps(index,indent=2)+'\n')
 end=mouse_frame(True)
 render(end.svg(True),OUT/'png/mouse_mode_end.png',720,end.h*2)
 (OUT/'drawable/help_anim_mouse_mode.xml').write_text(animated_mouse(drawings['mouse_mode'],end))
 previews={**drawings,'mouse_mode_end':end}
 # Sheet embeds precisely the same vector geometry that produces the previews.
 cols=5;cellw=360;cellh=335;rows=math.ceil(len(previews)/cols)
 sheet=[f'<svg xmlns="http://www.w3.org/2000/svg" width="{cols*cellw}" height="{rows*cellh}"><path d="M0 0H{cols*cellw}V{rows*cellh}H0Z" fill="{C["canvas"]}"/>']
 for i,(t,d) in enumerate(previews.items()):
  x=(i%cols)*cellw;y=(i//cols)*cellh
  sheet.append(f'<text x="{x+20}" y="{y+24}" font-family="sans-serif" font-size="16" fill="{C["text"]}">{escape(t)}</text>')
  body=d.svg().split('>',1)[1].rsplit('</svg>',1)[0]
  sheet.append(f'<g transform="translate({x} {y+32})">{body}</g>')
 sheet.append('</svg>');s=''.join(sheet);(OUT/'sheet.svg').write_text(s);render(s,OUT/'sheet.png',cols*cellw,rows*cellh)
 (OUT/'REVIEW.md').write_text(REVIEW)
 validate(drawings)
 print(f'Generated {len(drawings)} diagrams, vectors, 2x previews, and contact sheet.')
if __name__=='__main__':main()
