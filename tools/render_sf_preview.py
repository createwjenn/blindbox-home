"""Render the actual runtime GLB/light studies from the room's window bank."""
from pathlib import Path
import bpy,sys,json,struct,math
from mathutils import Vector
root=Path(__file__).resolve().parents[1];assets=root/'app/src/main/assets';out=root/'design/reference-library'
base=(assets/'collection-room.glb').read_bytes();n=struct.unpack_from('<I',base,12)[0];body=base[28+n:]
bridge='--bridge' in sys.argv
for phase in (['day'] if bridge else ['day','sunrise','sunset','night']):
 look=(assets/f'atmosphere/{phase}.look').read_bytes();size=struct.unpack_from('<I',look)[0];header=look[4:4+size];binary=body+look[4+size:]
 path=Path('/tmp')/f'sf-{phase}.glb';path.write_bytes(struct.pack('<III',0x46546c67,2,28+len(header)+len(binary))+struct.pack('<II',len(header),0x4e4f534a)+header+struct.pack('<II',len(binary),0x004e4942)+binary)
 bpy.ops.wm.read_factory_settings(use_empty=True);bpy.ops.import_scene.gltf(filepath=str(path))
 sc=bpy.context.scene;sc.render.engine='CYCLES';sc.cycles.samples=10;sc.cycles.use_denoising=True;sc.render.threads_mode='FIXED';sc.render.threads=5
 sc.world=bpy.data.worlds.new('Minimal runtime ambient');sc.world.use_nodes=True;sc.world.node_tree.nodes['Background'].inputs[1].default_value=.08
 cam=bpy.data.cameras.new('Window review');ob=bpy.data.objects.new('Window review',cam);sc.collection.objects.link(ob);sc.camera=ob
 ob.location=(-3.6,2.3,1.65) if bridge else (-3.45,.1,1.65)
 ob.rotation_euler=(Vector((-34,36,-3) if bridge else (-25,9,1.2))-ob.location).to_track_quat('-Z','Y').to_euler();cam.lens=24 if bridge else 17;cam.clip_start=.05;cam.clip_end=200
 sc.render.resolution_x=1000;sc.render.resolution_y=650;sc.render.resolution_percentage=100;sc.render.image_settings.file_format='PNG';sc.render.filepath=str(out/f'sf-{"bridge" if bridge else "window"}-{phase}.png');bpy.ops.render.render(write_still=True)
 print('RENDERED',phase,flush=True)
