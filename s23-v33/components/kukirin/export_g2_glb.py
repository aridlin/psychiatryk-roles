from pathlib import Path
import runpy,struct,json,math,zlib
r=Path(__file__).resolve().parent
parts=runpy.run_path(str(r/'build_clean_g2.py'))['parts'];scale=1.4/111
binary=bytearray();views=[];accessors=[];meshes=[]
def accessor(data,components,kind):
 while len(binary)%4:binary.append(0)
 start=len(binary);flat=[v for row in data for v in row];binary.extend(struct.pack('<'+'f'*len(flat),*flat));view=len(views);views.append({'buffer':0,'byteOffset':start,'byteLength':len(binary)-start});index=len(accessors);entry={'bufferView':view,'componentType':5126,'count':len(data),'type':kind}
 if kind in ('VEC3','SCALAR'):entry.update(min=[min(row[i] for row in data) for i in range(components)],max=[max(row[i] for row in data) for i in range(components)])
 accessors.append(entry);return index
front=(0,10.5*scale,-45.5*scale);rear=(0,10.5*scale,44.5*scale)
paints=[0xff282b2d,0xff151719,0xffff791f,0xffaab0b5,0xff32606e]
for n,part in enumerate(parts):
 primitives=[];pivot=(0,0,0) if n==0 else rear if n==3 else front
 for paint in paints:
  positions=[];normals=[]
  for vertices,normal,color in part:
   if color!=paint:continue
   normal=(normal[0],-normal[1],-normal[2])
   for v in vertices:
    positions.append((v[0]-pivot[0],-v[1]-pivot[1],-v[2]-pivot[2]));normals.append(normal)
  if positions:primitives.append({'attributes':{'POSITION':accessor(positions,3,'VEC3'),'NORMAL':accessor(normals,3,'VEC3'),'TEXCOORD_0':accessor([(.5,.5)]*len(positions),2,'VEC2')},'material':paints.index(paint),'mode':4})
 meshes.append({'name':('body','steering','front_wheel','rear_wheel')[n],'primitives':primitives})

nodes=[{'name':'root','children':[1,2,4]},{'name':'body','mesh':0},{'name':'steering_pivot','translation':front,'children':[3,5]},{'name':'stem','mesh':1},{'name':'rear_wheel','translation':rear,'mesh':3},{'name':'front_wheel','mesh':2}]
animations=[]
def quaternion(axis,angle):
 a=math.radians(angle)/2;s=math.sin(a);return (s if axis==0 else 0,s if axis==1 else 0,0,math.cos(a))
for name,times,rotations,targets in [('steering',[0,1],[quaternion(1,-28),quaternion(1,28)],[2]),('wheels',[0,.25,.5,.75,1],[quaternion(0,x) for x in (0,90,180,270,360)],[4,5])]:
 inp=accessor([(v,) for v in times],1,'SCALAR');out=accessor(rotations,4,'VEC4');animations.append({'name':name,'samplers':[{'input':inp,'output':out,'interpolation':'LINEAR'}],'channels':[{'sampler':0,'target':{'node':node,'path':'rotation'}} for node in targets]})
images=[]
def chunk(kind,data):return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
for paint in paints:
 rgba=bytes([(paint>>shift)&255 for shift in (16,8,0)]+[255]);png=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',2,2,8,6,0,0,0))+chunk(b'IDAT',zlib.compress((b'\0'+rgba*2)*2))+chunk(b'IEND',b'')
 while len(binary)%4:binary.append(0)
 start=len(binary);binary.extend(png);view=len(views);views.append({'buffer':0,'byteOffset':start,'byteLength':len(png)});images.append({'bufferView':view,'mimeType':'image/png','extras':{'resourceLocation':f'goplanska_kukirin:textures/model/paint_{len(images)}.png'}})
 texture=r/f'resources/assets/goplanska_kukirin/textures/model/paint_{len(images)-1}.png';texture.parent.mkdir(parents=True,exist_ok=True);texture.write_bytes(png)
doc={'asset':{'version':'2.0','generator':'Aridlin licensed G2 repair/export'},'scene':0,'scenes':[{'nodes':[0]}],'nodes':nodes,'meshes':meshes,'materials':[{'name':hex(paint),'pbrMetallicRoughness':{'baseColorFactor':[1,1,1,1],'baseColorTexture':{'index':paints.index(paint)},'metallicFactor':.1,'roughnessFactor':.8},'doubleSided':False} for paint in paints],'animations':animations,'images':images,'textures':[{'source':i,'sampler':0} for i in range(len(images))],'samplers':[{'magFilter':9728,'minFilter':9728,'wrapS':33071,'wrapT':33071}],'bufferViews':views,'accessors':accessors,'buffers':[{'byteLength':len(binary)}]}
j=json.dumps(doc,separators=(',',':')).encode();j+=b' '*((-len(j))%4);binary+=b'\0'*((-len(binary))%4)
path=r/'resources/assets/goplanska_kukirin/models/kukirin_g2.glb';path.write_bytes(struct.pack('<III',0x46546c67,2,28+len(j)+len(binary))+struct.pack('<II',len(j),0x4e4f534a)+j+struct.pack('<II',len(binary),0x004e4942)+binary)
print('GLB',path.stat().st_size,'bytes')
