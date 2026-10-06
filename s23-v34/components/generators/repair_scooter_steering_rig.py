"""Repair the authored scooter rig without remeshing or changing its UV atlas.

Input must be the v33 baseline asset directory; output is a separate directory.
Requires numpy. No downloaded model, texture or third-party source is embedded.
"""
from pathlib import Path
import argparse, hashlib, json, math, struct
import numpy as np


def read_glb(path):
    raw=path.read_bytes()
    magic,version,total=struct.unpack_from('<III',raw)
    assert magic==0x46546c67 and version==2 and total==len(raw)
    size,kind=struct.unpack_from('<II',raw,12);assert kind==0x4e4f534a
    doc=json.loads(raw[20:20+size]);offset=20+size
    size,kind=struct.unpack_from('<II',raw,offset);assert kind==0x004e4942
    return doc,bytearray(raw[offset+8:offset+8+size])


def array(doc,data,index):
    accessor=doc['accessors'][index];view=doc['bufferViews'][accessor['bufferView']]
    width={'SCALAR':1,'VEC2':2,'VEC3':3,'VEC4':4}[accessor['type']]
    assert accessor['componentType']==5126
    stride=view.get('byteStride',width*4);offset=view.get('byteOffset',0)+accessor.get('byteOffset',0)
    return np.array([struct.unpack_from('<'+'f'*width,data,offset+i*stride) for i in range(accessor['count'])],dtype=np.float64)


def connected_faces(points):
    _,ids=np.unique(np.round(points,6),axis=0,return_inverse=True)
    parents=list(range(int(ids.max())+1))
    def find(i):
        while parents[i]!=i:
            parents[i]=parents[parents[i]];i=parents[i]
        return i
    for x,y,z in ids.reshape(-1,3):
        for a,b in ((x,y),(y,z)):parents[find(a)]=find(b)
    groups={}
    for face,vertices in enumerate(ids.reshape(-1,3)):
        groups.setdefault(find(vertices[0]),[]).append(face)
    return list(groups.values())


def repair(source,destination):
    doc,data=read_glb(source/'kukirin_g2.glb')
    front=np.array(doc['nodes'][2]['translation'])
    primitives=[m['primitives'][0] for m in doc['meshes']]
    assert all(len(m['primitives'])==1 and p['mode']==4 and 'indices' not in p for m,p in zip(doc['meshes'],primitives))
    attrs=[{key:array(doc,data,index) for key,index in p['attributes'].items()} for p in primitives]
    steering_world=attrs[1]['POSITION']+front
    groups=connected_faces(steering_world)
    # Identify the one fixed deck-to-headset brace by topology and exact rest bounds.
    candidates=[]
    for faces in groups:
        points=steering_world.reshape(-1,3,3)[faces].reshape(-1,3)
        if len(faces)==108 and points[:,1].min()<.17 and .47<points[:,1].max()<.48:
            candidates.append(faces)
    assert len(candidates)==1,'Unexpected brace geometry; inspect rather than guessing'
    brace=np.array(candidates[0],dtype=int)
    moving=np.array([i for i in range(len(steering_world)//3) if i not in set(brace)],dtype=int)
    tube=[]
    for faces in groups:
        points=np.unique(steering_world.reshape(-1,3,3)[faces].reshape(-1,3),axis=0)
        if len(faces)==512 and points[:,1].min()<.36 and points[:,1].max()>1.19:
            tube.append(points)
    assert len(tube)==1
    lower=tube[0][(tube[0][:,1]>.35)&(tube[0][:,1]<.45)].mean(axis=0)
    upper=tube[0][(tube[0][:,1]>.45)&(tube[0][:,1]<.65)].mean(axis=0)
    # The minute x discrepancy is tessellation noise, not a sideways steering axis.
    axis=np.array([0.,upper[1]-lower[1],upper[2]-lower[2]])
    axis/=np.linalg.norm(axis);pivot=lower
    maximum=math.atan(math.tan(math.radians(28))/axis[1])
    old_front=front.copy()
    doc['nodes'][2]['translation']=pivot.tolist()
    doc['nodes'][5]['translation']=(old_front-pivot).tolist()
    assert doc['nodes'][5]['mesh']==2 and doc['nodes'][4]['mesh']==3
    for key in attrs[1]:
        width=attrs[1][key].shape[1]
        stationary=attrs[1][key].reshape(-1,3,width)[brace].reshape(-1,width)
        keep=attrs[1][key].reshape(-1,3,width)[moving].reshape(-1,width)
        if key=='POSITION':
            stationary=stationary+old_front
            keep=keep+old_front-pivot
        attrs[0][key]=np.concatenate([attrs[0][key],stationary])
        attrs[1][key]=keep
    def replace(index,values):
        while len(data)%4:data.append(0)
        offset=len(data);packed=np.asarray(values,dtype='<f4').tobytes();data.extend(packed)
        view=len(doc['bufferViews']);doc['bufferViews'].append({'buffer':0,'byteOffset':offset,'byteLength':len(packed)})
        accessor=doc['accessors'][index];accessor['bufferView']=view;accessor.pop('byteOffset',None)
        accessor['count']=len(values);accessor['min']=values.min(axis=0).tolist();accessor['max']=values.max(axis=0).tolist()
    for mesh in (0,1):
        for key,index in primitives[mesh]['attributes'].items():replace(index,attrs[mesh][key])
    rotation_index=doc['animations'][0]['samplers'][0]['output']
    quaternions=np.array([np.r_[axis*math.sin(a/2),math.cos(a/2)] for a in (-maximum,maximum)])
    replace(rotation_index,quaternions)
    doc['buffers'][0]['byteLength']=len(data)
    doc['asset']['generator']='Aridlin authored UV-preserving fixed-brace and raked-axis rig repair'
    encoded=json.dumps(doc,separators=(',',':')).encode()
    while len(encoded)%4:encoded+=b' '
    while len(data)%4:data.append(0)
    glb=struct.pack('<III',0x46546c67,2,28+len(encoded)+len(data))+struct.pack('<II',len(encoded),0x4e4f534a)+encoded+struct.pack('<II',len(data),0x004e4942)+data
    mesh=(source/'kukirin_g2.mesh').read_bytes();counts=struct.unpack_from('>4i',mesh)
    assert counts==(1236,6618,636,636)
    chunks=[];offset=16
    for count in counts:
        chunks.append([mesh[offset+i*52:offset+(i+1)*52] for i in range(count)])
        offset+=count*52
    assert offset==len(mesh)
    # Fallback triangles use the identical global triangle order and flipped Y/Z.
    for i,record in enumerate(chunks[1]):
        xyz=np.array(struct.unpack_from('>9f',record,16)).reshape(3,3)*[1,-1,-1]
        assert np.allclose(xyz,steering_world.reshape(-1,3,3)[i],atol=2e-7,rtol=0)
    chunks[0]+= [chunks[1][i] for i in brace]
    chunks[1]=[chunks[1][i] for i in moving]
    fallback=struct.pack('>4i',*[len(part) for part in chunks])+b''.join(b''.join(part) for part in chunks)
    rig=json.loads((source/'scooter-rig.json').read_text())
    rig.update(steeringPivot=pivot.tolist(),steeringAxis=axis.tolist(),steeringLimitDegrees=28,steeringAxisLimitDegrees=math.degrees(maximum))
    destination.mkdir(parents=True,exist_ok=True)
    (destination/'kukirin_g2.glb').write_bytes(glb)
    (destination/'kukirin_g2.mesh').write_bytes(fallback)
    (destination/'scooter-rig.json').write_text(json.dumps(rig,separators=(',',':'))+'\n')
    return {'fixed_brace_triangles':len(brace),'triangles_unchanged':sum(counts),'pivot':pivot.tolist(),'axis':axis.tolist(),'axis_limit_degrees':math.degrees(maximum),'projected_limit_degrees':28,'files':{name:hashlib.sha256((destination/name).read_bytes()).hexdigest() for name in ('kukirin_g2.glb','kukirin_g2.mesh','scooter-rig.json')}}

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('source',type=Path);parser.add_argument('destination',type=Path);options=parser.parse_args()
    print(json.dumps(repair(options.source,options.destination),indent=2))
