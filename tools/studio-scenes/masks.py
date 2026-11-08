import sys; from PIL import Image, ImageFilter; import numpy as np
src, out, prev = sys.argv[1:4]
im=np.asarray(Image.open(src).convert('RGB')).astype(int); H,W,_=im.shape
r,g,b=im[...,0],im[...,1],im[...,2]
# horizon: first row where the right-edge column turns to sea
xr=W-6
hz=next(y for y in range(640,H) if r[y,xr]<125 and b[y,xr]>r[y,xr]+40 and g[y,xr]>r[y,xr]+30)
ref=im[:,xr:xr+1,:]                      # the right edge above the horizon is open sky
sky=np.zeros((H,W),bool); sky[:640]=True
near=np.abs(im-ref).sum(-1)<36
sky[640:hz]=near[640:hz]
# water: blue-dominant or foam, below the horizon, above the meadow
blue=(b>r+20)&(g>r+15)
foam=(r>200)&(g>200)&(b>195)&(np.abs(r-b)<30)
water=(blue|foam); water[:hz]=False; water[1150:]=False
# sway: everything below the meadow's top edge; per column, climb up from the bottom while
# a 24-row window above is still mostly grass (olive: almost no blue), so flowers stay inside
veg=(b<80)&(g>b+40)
top=np.zeros(W,int)
for x in range(W):
    y=1300
    while y>hz+10 and veg[y-24:y,x].mean()>=0.45: y-=1
    top[x]=y
# the meadow edge is a smooth curve: a wide running median removes stalk/flower stripes
pad=np.pad(top,60,mode='edge'); top=np.array([np.percentile(pad[i:i+121],12) for i in range(W)])
pad=np.pad(top,30,mode='edge'); top=np.array([pad[i:i+61].mean() for i in range(W)]).astype(int)
top=np.clip(top,980,1300)
sway=np.zeros((H,W),bool)
stalk=(b<90)&(g>b+40)&(r>120)            # golden stalks and grass, incl. tips above the edge
sandc=(r>180)&(b>=90)&(b<=205)&(g>160)&(r-b>40)
for x in range(W):
    sway[top[x]:,x]=True
    sway[max(top[x]-90,0):top[x],x]=stalk[max(top[x]-90,0):top[x],x]
sway&=~sandc
# flowers read as sand by colour: close small holes, but only well inside the meadow
m=Image.fromarray((sway*255).astype(np.uint8)).filter(ImageFilter.MaxFilter(25)).filter(ImageFilter.MinFilter(25))
closed=np.asarray(m)>127
inner=np.zeros((H,W),bool)
for x in range(W): inner[top[x]+30:,x]=True
sway|=closed&inner
sway&=~water
print('horizon',hz)
ys=np.arange(H)[:,None]
ramp=np.clip((1380-ys)/200,0,1)          # the plain ground at the very bottom barely moves
def soft(m,k=1.5): return np.asarray(Image.fromarray((m*255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(k)))
# sway spills ~8px past each blade so displaced tips move instead of getting clipped
spill=np.asarray(Image.fromarray((sway*255).astype(np.uint8)).filter(ImageFilter.MaxFilter(13)).filter(ImageFilter.GaussianBlur(4)))
R=soft(sky); G=soft(water); B=(spill*ramp).astype(np.uint8)
Image.fromarray(np.dstack([R,G,B,np.full_like(R,255)]),'RGBA').save(out)
# preview: tint the art by region
p=im.copy().astype(float)
for m,c in ((R,(255,60,60)),(G,(60,255,255)),(B,(255,255,0))):
    a=(m/255.0)[...,None]*0.55; p=p*(1-a)+np.array(c)*a
Image.fromarray(p.astype(np.uint8)).resize((W//2,H//2)).save(prev)
print('sky',sky.mean().round(3),'water',water.mean().round(3),'sway',(B>40).mean().round(3))
