"""Frame the original portrait paintings at their source aspect ratio, with the art facing +Z in glTF."""
from pathlib import Path
import json,struct
import bpy
from PIL import Image
from furniture_atelier import block,mat,BASE,MODELS
for path in (BASE/'res/drawable-nodpi').glob('painting_*.jpg'):
 bpy.ops.wm.read_factory_settings(use_empty=True)
 w,h=Image.open(path).size;hgt=.72;width=w/h*hgt;wood=mat('Ebonized walnut','504239');brass=mat('Inner brass fillet','B59A70',.4)
 block((0,.014,0),(width+.055,.030,hgt+.055),wood,.008);block((0,-.004,0),(width+.014,.014,hgt+.014),brass,.003)
 art=mat('Printed canvas','FFFFFF');p=art.node_tree.nodes.get('Principled BSDF');im=art.node_tree.nodes.new('ShaderNodeTexImage');im.image=bpy.data.images.load(str(path));im.image.pack();art.node_tree.links.new(im.outputs[0],p.inputs['Base Color']);art.node_tree.links.new(im.outputs[0],p.inputs['Emission Color']);p.inputs['Emission Strength'].default_value=.55
 mesh=bpy.data.meshes.new('Canvas');mesh.from_pydata([(-width/2,-.013,-hgt/2),(width/2,-.013,-hgt/2),(width/2,-.013,hgt/2),(-width/2,-.013,hgt/2)],[],[(0,1,2,3)]);mesh.update();uv=mesh.uv_layers.new()
 for i,v in enumerate([(0,0),(1,0),(1,1),(0,1)]):uv.data[i].uv=v
 ob=bpy.data.objects.new('Art',mesh);bpy.context.collection.objects.link(ob);mesh.materials.append(art)
 bpy.ops.export_scene.gltf(filepath=str(MODELS/(path.stem+'.glb')),export_format='GLB',export_image_format='JPEG',export_jpeg_quality=94)
