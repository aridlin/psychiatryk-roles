from pathlib import Path
import trimesh, numpy as np, json
r=Path(__file__).resolve().parent
mesh=trimesh.load(r/'model-research/detailed_kukirin_G2.stl',force='mesh')
# The print export contains intersecting shells. Reconstruct their union, then simplify it.
voxels=mesh.voxelized(pitch=.4).fill()
clean=voxels.marching_cubes
clean.apply_transform(voxels.transform)
clean=clean.simplify_quadric_decimation(face_count=6000)
clean.fix_normals()
clean.export(r/'model-research/repaired_kukirin_G2.stl')
print(json.dumps({'original_faces':len(mesh.faces),'repaired_faces':len(clean.faces),'bounds':clean.bounds.tolist(),'watertight':bool(clean.is_watertight)}))
