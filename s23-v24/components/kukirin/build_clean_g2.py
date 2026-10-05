"""Clean G2-style game asset: separate rigid assemblies, no slices of fused print geometry.
Run using work/model-render-research/mesh-tools/bin/python.
"""
from pathlib import Path
import trimesh,numpy as np,math,struct,json
r=Path(__file__).resolve().parent;scale=1.4/111
front=np.array([0,10.5*scale,-45.5*scale]);rear=np.array([0,10.5*scale,44.5*scale])
parts=[[],[],[],[]];DARK=0xff282b2d;BLACK=0xff151719;ORANGE=0xffff791f;SILVER=0xffaab0b5;SCREEN=0xff32606e
geometry=[]
def add(mesh,group,color):
 geometry.append(mesh)
 for tri,normal in zip(mesh.triangles,mesh.face_normals):
  # The fallback renderer uses the historic two-axis flip; GLB export reverses it.
  parts[group].append(([(float(v[0]),-float(v[1]),-float(v[2])) for v in tri],(float(normal[0]),-float(normal[1]),-float(normal[2])),color))
def box(center,size,group,color,axis=None,angle=0):
 m=trimesh.creation.box(extents=size)
 if axis is not None:m.apply_transform(trimesh.transformations.rotation_matrix(angle,axis))
 m.apply_translation(center);add(m,group,color)
def tube(a,b,radius,group,color,segments=20):
 a=np.array(a);b=np.array(b);d=b-a;m=trimesh.creation.cylinder(radius=radius,height=float(np.linalg.norm(d)),sections=segments)
 m.apply_transform(trimesh.geometry.align_vectors([0,0,1],d));m.apply_translation((a+b)/2);add(m,group,color)
def fender(center,group):
 # Non-overlapping radial segments with recessed joints above the tire.
 for i in range(18):
  theta=math.radians(20+(i+.5)*140/18);pos=center+np.array([0,math.sin(theta)*.163,math.cos(theta)*.163]);box(pos,[.105,.013,.163*math.radians(140/18)*.98],group,BLACK,[1,0,0],-theta-math.pi/2)
# Chassis and grippy deck. Insets are spaced from the supporting surfaces.
box([0,.185,.075],[.275,.075,.77],0,DARK)
box([0,.225,.075],[.252,.004,.72],0,BLACK)
for x in [-.14,.14]:box([x,.188,.075],[.008,.036,.70],0,ORANGE)
for z in np.linspace(-.24,.39,14):box([0,.229,z],[.245,.001,.004],0,DARK)
# Chassis neck and rear swing arms.
tube([0,.17,-.30],[0,.31,-.43],.027,0,DARK)
for x in [-.071,.071]:
 tube([x,.18,.39],[x,rear[1],rear[2]],.013,0,DARK)
 tube([x,.23,.40],[x,.18,.52],.013,0,ORANGE)
fender(rear,0)
# Complete steering assembly: fork, stem, grips, brakes and display.
for x in [-.063,.063]:tube([x,.34,-.445],[x,front[1],front[2]],.012,1,DARK)
tube([-.063,.34,-.445],[.063,.34,-.445],.016,1,DARK)
tube([0,.32,-.445],[0,1.02,-.38],.025,1,DARK)
tube([0,.40,-.438],[0,.88,-.393],.027,1,ORANGE)
# A front trim strip, set clear of the stem surface.
tube([0,.42,-.467],[0,.86,-.425],.007,1,BLACK)
tube([-.31,1.02,-.38],[.31,1.02,-.38],.014,1,DARK)
for sign in [-1,1]:
 tube([sign*.18,1.02,-.38],[sign*.31,1.02,-.38],.023,1,BLACK)
 tube([sign*.17,1.005,-.36],[sign*.27,1.005,-.32],.004,1,SILVER,12)
box([0,1.048,-.375],[.075,.023,.065],1,DARK,[1,0,0],-.25)
box([0,1.061,-.378],[.055,.003,.045],1,SCREEN,[1,0,0],-.25)
fender(front,1)
# Tires, rims and visible spokes. Every wheel is a complete independent assembly.
for group,center in [(2,front),(3,rear)]:
 tube(center+[-.047,0,0],center+[.047,0,0],.132,group,BLACK,32)
 tube(center+[-.050,0,0],center+[.050,0,0],.078,group,DARK,24)
 tube(center+[-.053,0,0],center+[.053,0,0],.021,group,SILVER,20)
 for side in [-1,1]:
  for angle in np.linspace(0,math.tau,6,endpoint=False):
   pos=center+[side*.051,math.cos(angle)*.044,math.sin(angle)*.044];box(pos,[.002,.052,.009],group,SILVER,[1,0,0],angle)
meshdir=r/'resources/assets/goplanska_kukirin/models';meshdir.mkdir(parents=True,exist_ok=True)
with (meshdir/'kukirin_g2.mesh').open('wb') as f:
 f.write(struct.pack('>4i',*[len(p) for p in parts]))
 for part in parts:
  for vertices,normal,color in part:
   f.write(struct.pack('>i',color-2**32));f.write(struct.pack('>3f',*normal));f.write(struct.pack('>9f',*[n for v in vertices for n in v]))
print('Clean game scooter parts:',[len(p) for p in parts], 'triangles')
(meshdir/'clean-model-info.json').write_text(json.dumps({'model':'G2-style original game model','author':'Aridlin project','triangles':sum(map(len,parts)),'license':'GPL-3.0-or-later','source':'work/kukirin/build_clean_g2.py'},indent=2))
