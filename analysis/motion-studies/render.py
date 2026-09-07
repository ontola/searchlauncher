from PIL import Image, ImageDraw, ImageFont
import math, subprocess
from pathlib import Path
OUT=Path(__file__).parent
W,H=900,1050
FONT='/System/Library/Fonts/Supplemental/Arial.ttf'
BOLD='/System/Library/Fonts/Supplemental/Arial Bold.ttf'
def font(n,b=False): return ImageFont.truetype(BOLD if b else FONT,n)
def text(d,xy,s,n=16,fill='#e6e9dd',b=False): d.text(xy,s,font=font(n,b),fill=fill)
def ease(v): return 1-(1-max(0,min(1,v)))**3
def rr(d,box,c,r=12): d.rounded_rectangle(box,r,fill=c)
def mix(a,b,t): return tuple(round(x+(y-x)*t) for x,y in zip(a,b))
PW,PH=394,840
def base(query='',press=False):
 im=Image.new('RGB',(PW,PH),'#171b14');d=ImageDraw.Draw(im)
 # A restrained abstract wallpaper, deliberately separate from the user's photos.
 for y in range(590):
  c=mix((83,100,70),(32,45,33),y/590);d.line((0,y,PW,y),fill=c)
 d.ellipse((-140,-130,305,380),fill='#637658');d.ellipse((170,50,540,430),fill='#526b4b')
 text(d,(20,15),'20:33',13,b=True);text(d,(309,15),'Wi-Fi  72%',11)
 # Favorites are fixed throughout results animation.
 for i,(label,col) in enumerate([('G','#4285f4'),('Y','#be4545'),('S','#409c62'),('P','#548f6e'),('AI','#688276'),('DD','#be7053'),('Cal','#6681bf')]):
  x=22+i*51;rr(d,(x,502,x+34,536),col,8);text(d,(x+7,511),label,12,b=True)
 rr(d,(13,550,381,596),'#232d1e',22);text(d,(29,565),query or 'Type to search',18,fill='#dae3ca' if query else '#a4b299')
 if query:text(d,(351,563),'×',23)
 else:
  d.ellipse((348,567,364,583),outline='#a4b299',width=2)
  d.ellipse((353,572,359,578),fill='#a4b299')
 d.rectangle((0,608,PW,PH),fill='#151a12')
 rows=['qwertyuiop','asdfghjkl','zxcvbnm']
 for row,letters in enumerate(rows):
  kw=34;gap=4;offset=(PW-(len(letters)*(kw+gap)-gap))/2
  for i,c in enumerate(letters):
   x=offset+i*(kw+gap);y=616+row*53
   rr(d,(x,y,x+kw,y+48),'#778b54' if press and c=='g' else '#283020',5)
   text(d,(x+10,y+13),c,19)
 for x,w,label in [(7,56,'?123'),(67,38,','),(110,174,'space'),(289,36,'.'),(330,57,'Go')]:
  rr(d,(x,776,x+w,817),'#283020',5);text(d,(x+w/2-(len(label)*4),789),label,14)
 # OS navigation intentionally separate.
 return im

def result_layer(kind,elapsed,exit=False):
 layer=Image.new('RGBA',(PW,PH));d=ImageDraw.Draw(layer)
 duration=.14 if kind=='a' else .18
 p=ease(elapsed/duration)
 if exit:p=1-p
 if p<=0:return layer
 # whole panel softens in; only row content translates, viewport stays fixed.
 panel=Image.new('RGBA',(PW,PH));q=ImageDraw.Draw(panel)
 rr(q,(13,192,381,486),(26,34,22,round(244*p)),20)
 layer.alpha_composite(panel)
 rows=[('GitHub','App','#333c44','GH'),('Google','Search the web','#4285f4','G'),('Gmail','App','#b75947','M')]
 for i,(title,sub,col,icon) in enumerate(rows):
  delay=(2-i)*.016 if kind=='b' else 0
  pr=ease((elapsed-delay)/duration)
  if exit:pr=1-ease(elapsed/duration)
  row=Image.new('RGBA',(PW,PH));r=ImageDraw.Draw(row)
  y=211+i*84+round((1-pr)*(8 if kind=='b' else 4))
  if i==2:rr(r,(23,y-2,371,y+72),'#39472c',12)
  rr(r,(34,y+11,81,y+58),col,11);text(r,(43,y+25),icon,18,b=True)
  text(r,(96,y+13),title,20,b=True);text(r,(96,y+42),sub,14,fill='#aab99b');[r.ellipse((351,y+24+j*6,354,y+27+j*6),fill='#aab99b') for j in range(3)]
  row.putalpha(row.getchannel('A').point(lambda a:round(a*pr)))
  layer.alpha_composite(row)
 return layer

def phone(kind,t):
 if kind in ['a','b']:
  active=1<=t<4.2;exiting=4.2<=t<4.5
  im=base('g' if active else '',1<=t<1.07).convert('RGBA')
  if active: im.alpha_composite(result_layer(kind,t-1))
  elif exiting:im.alpha_composite(result_layer(kind,t-4.2,True))
 else:
  im=base().convert('RGBA')
  if t>=1 and t<4.8:
   p=ease((t-1)/.16) if t<3.8 else 1-ease((t-3.8)/.14)
   panel=Image.new('RGBA',(PW,PH),'#171d14');d=ImageDraw.Draw(panel)
   text(d,(20,15),'20:33',13,b=True);text(d,(23,67),'Downloads',29,b=True);text(d,(335,75),'Done',15,fill='#b3cf8d')
   for i,(name,status,meta,action) in enumerate([('SearchLauncher.apk','Downloading · 64%','61 MB of 96 MB','Cancel'),('Trip itinerary.pdf','Complete','284 KB · Today','Open file')]):
    y=132+i*185;rr(d,(16,y,378,y+167),'#293023',18)
    rr(d,(33,y+19,80,y+68),'#4d6138',12);text(d,(47,y+29),'↓',26)
    text(d,(95,y+23),name,18,b=True);text(d,(95,y+49),status,14,fill='#b3c1a2');text(d,(34,y+87),meta,13,fill='#b3c1a2')
    if i==0:
     rr(d,(34,y+113,358,y+117),'#46503d',2);rr(d,(34,y+113,242,y+117),'#adc789',2)
    else:rr(d,(33,y+113,151,y+151),'#465a32',19);text(d,(51,y+123),action,14)
   panel.putalpha(round(p*255));im.alpha_composite(panel,(0,round((1-p)*10)))
 return im.convert('RGB')

studies=[('a','A · Quiet results','140 ms · 4 px rise + fade','A restrained default for every query.'),('b','B · Gentle sequence','180 ms · 8 px rise · 16 ms stagger','First match appears first; only new rows animate.'),('c','C · Panels & feedback','160 ms enter · 140 ms exit','Full-screen surface, tiny lift, no spring or bounce.')]
for kind,title,spec,note in studies:
 path=OUT/f'{kind}-motion.mp4'
 proc=subprocess.Popen(['/opt/homebrew/bin/ffmpeg','-y','-loglevel','error','-f','rawvideo','-pix_fmt','rgb24','-s',f'{W}x{H}','-r','30','-i','-','-an','-c:v','libx264','-preset','fast','-crf','20','-pix_fmt','yuv420p','-movflags','+faststart',str(path)],stdin=subprocess.PIPE)
 # Same event starts together. Slow view holds transitions four times longer, without stretching the pauses.
 for frame in range(240):
  t=frame/30
  canvas=Image.new('RGB',(W,H),'#0d100d');d=ImageDraw.Draw(canvas)
  text(d,(36,23),title,29,b=True);text(d,(36,64),spec,17,fill='#aab69f')
  for x,slow,label in [(36,False,'NORMAL SPEED'),(470,True,'4× SLOW MOTION')]:
   text(d,(x,108),label,12,fill='#b9cd9f',b=True)
   # run two events: appearance and dismissal; leave enough time to inspect both
   if t<1:vt=t
   elif t<2.2:vt=1+(t-1)/(4 if slow else 1)
   elif t<4.2:vt=2.2
   else:
    start=3.8 if kind=='c' else 4.2
    vt=start+(t-4.2)/(4 if slow else 1)
   pic=phone(kind,vt)
   mask=Image.new('L',(PW,PH));ImageDraw.Draw(mask).rounded_rectangle((0,0,PW-1,PH-1),23,fill=255)
   canvas.paste(pic,(x,139),mask)
  text(d,(36,1000),note,16,fill='#aab69f')
  if frame==42:canvas.save(OUT/f'{kind}-poster.png')
  proc.stdin.write(canvas.tobytes())
 proc.stdin.close();assert proc.wait()==0
 print(path,flush=True)
