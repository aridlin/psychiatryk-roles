from pathlib import Path
import struct,math,hashlib,json
root=Path(__file__).resolve().parent
source=root/'model-research/repaired_kukirin_G2.stl';b=source.read_bytes();count=struct.unpack_from('<I',b,80)[0]
scale=1.4/111
parts=[[],[],[],[]];seen=set();duplicates=0
for i in range(count):
 d=struct.unpack_from('<12f',b,84+i*50);raw=[d[j:j+3] for j in (3,6,9)]
 key=tuple(sorted(tuple(round(c,5) for c in v) for v in raw))
 if key in seen:duplicates+=1;continue
 seen.add(key)
 center=[sum(v[k] for v in raw)/3 for k in range(3)]
 x,y,z=center
 # The print mesh is fused. Partition the tire regions and front assembly for animation.
 group=0
 if (x+35)**2+(z-10)**2<10.8**2 and 5.4<y<17.8:group=2
 elif (x-55)**2+(z-10)**2<10.8**2 and 4<y<18:group=3
 elif x<-14 and z>19:group=1
 vertices=[((v[1]-11)*scale,-(v[2]+.5)*scale,-(v[0]-10.5)*scale) for v in raw]
 a,c,e=vertices;u=[c[k]-a[k] for k in range(3)];v=[e[k]-a[k] for k in range(3)];normal=[u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0]];length=math.sqrt(sum(n*n for n in normal))
 if length<1e-10:continue
 normal=[n/length for n in normal]
 color=0xff282b2d
 if group in (2,3):color=0xff151719
 elif (10<z<17 and -18<x<43) or (x<-16 and z>65 and (y<8 or y>14)):color=0xffff791f
 parts[group].append((vertices,normal,color))
r=root/'resources/assets/goplanska_kukirin/models';r.mkdir(parents=True,exist_ok=True)
with (r/'kukirin_g2.mesh').open('wb') as f:
 f.write(struct.pack('>4i',*[len(p) for p in parts]))
 for part in parts:
  for vertices,normal,color in part:
   f.write(struct.pack('>i',color-2**32));f.write(struct.pack('>3f',*normal));f.write(struct.pack('>9f',*[n for v in vertices for n in v]))
# Keep the original source available alongside the derivative geometry.
(r/'kukirin_g2-source.stl').write_bytes((root/'model-research/detailed_kukirin_G2.stl').read_bytes())
(root/'resources/LICENSE-KUKIRIN-MODEL.txt').write_text('KuKirin G2 model\nAuthor: Thingsiminterestedin\nSource: https://www.thingiverse.com/thing:7377244\nLicense: Creative Commons Attribution-ShareAlike 4.0\nhttps://creativecommons.org/licenses/by-sa/4.0/\nChanges by Aridlin: normalized for Minecraft, intersecting print shells remeshed and simplified, duplicate triangles removed, gray/orange materials and tire/steering partitions added. The adapted model is distributed under the same CC BY-SA 4.0 license.\nOriginal STL: assets/goplanska_kukirin/models/kukirin_g2-source.stl\nConversion source: work/kukirin/convert_g2_model.py in the source archive.\n')
(root/'model-research/conversion.json').write_text(json.dumps({'source_triangles':count,'duplicate_triangles_removed':duplicates,'part_triangles':[len(p) for p in parts],'source_sha256':hashlib.sha256(b).hexdigest(),'scale':scale},indent=2))
print('Adapted real G2 model:',[len(p) for p in parts], 'triangles; removed',duplicates,'duplicate triangles')
