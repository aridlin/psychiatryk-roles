"""Generate bounded livery variants; only orange accent texels are repainted."""
from pathlib import Path
import re,zipfile,json
from PIL import Image
import numpy as np
root=Path(__file__).resolve().parent
jar=(Path.home() / '.gradle/caches/neoformruntime/intermediate_results/sourcesAndCompiledWithNeoForge_9d810e8ae5bf0f9a2cee7a3a46e9165d22438af9_output.jar')
with zipfile.ZipFile(jar) as archive:text=archive.read('net/minecraft/world/item/DyeColor.java').decode()
colors=re.findall(r'\w+\(\d+, "([a-z_]+)", (\d+), MapColor\.',text)
assert len(colors)==16
original=Image.open(root/'resources/assets/goplanska_kukirin/textures/model/private_sketchfab_kukirin.png').convert('RGBA')
image=np.asarray(original,dtype=float).copy();r,g,b=image[:,:,0],image[:,:,1],image[:,:,2]
mask=(r>100)&(g>35)&(g<r*.8)&(b<g*.45)
assert mask.sum()>1000
folder=root/'resources/assets/goplanska_kukirin/textures/model/dyes';folder.mkdir(parents=True,exist_ok=True)
original.resize((512,512),Image.Resampling.LANCZOS).save(folder/'default.png')
recipes=root/'resources/data/goplanska_kukirin/recipe';recipes.mkdir(parents=True,exist_ok=True)
for name,value in colors:
 rgb=np.array([(int(value)>>s)&255 for s in (16,8,0)],float)
 dyed=image.copy();dyed[:,:,:3][mask]=np.clip(rgb[None,:]*(r[mask]/255)[:,None],0,255)
 if name=='orange':dyed=image.copy()
 Image.fromarray(dyed.astype('uint8')).resize((512,512),Image.Resampling.LANCZOS).save(folder/(name+'.png'))
 (recipes/('scooter_dye_'+name+'.json')).write_text(json.dumps({'type':'goplanska_kukirin:scooter_dye','color':name})+'\n')
print('Generated 16 dye recipes and 17 shared-atlas liveries; accent pixels',mask.sum())
