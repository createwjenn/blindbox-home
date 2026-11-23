"""Import the website's full Marble captures and playable character rigs.

Usage: python import_world_models.py <website dist directory>. Requires NumPy.
Run import_world_navigation.mjs against the same dist directory.
"""
import argparse
import gzip
import json
import math
import struct
import subprocess
from pathlib import Path

import numpy as np

ASSETS = Path(__file__).resolve().parents[1] / "app/src/main/assets"
WORLDS = {"niu-lai": "world-niu-v2", "strawberlina": "world-strawberlina",
          "bananito": "world-bananito", "tung-tung-tung": "world-tung-v3",
          "tralalero-tralala": "world-tralalero", "ballerina-capuccina": "world-ballerina",
          "chill-guy": "world-chill-guy", "secret-capybara-toilet": "world-capybara"}


def export_sections(source, destination, eye):
    raw = gzip.decompress(source.read_bytes())
    magic, version, count, degree, fraction, flags, reserved = struct.unpack_from("<IIIBBBB", raw)
    assert (magic, version, degree) == (0x5053474e, 2, 0)
    columns, offset = [], 16
    for width in (9, 1, 3, 3, 3):
        columns.append(np.frombuffer(raw, np.uint8, count * width, offset).reshape(count, width))
        offset += count * width
    assert offset == len(raw)
    # Far bands first to preserve transparency across the SDK's one-million-point entity limit.
    xyz = columns[0].reshape(count, 3, 3)
    distance = np.zeros(count, dtype=np.float32)
    for axis in range(3):
        a = xyz[:, axis].astype(np.int32)
        signed = ((a[:, 0] | a[:, 1] << 8 | a[:, 2] << 16) ^ 0x800000) - 0x800000
        distance += (signed.astype(np.float32) / (1 << fraction) - eye[axis]) ** 2
    groups = np.array_split(np.argsort(distance), math.ceil(count / 1_000_000))
    for index, batch in enumerate(reversed(groups)):
        indices = np.sort(batch)
        header = struct.pack("<IIIBBBB", magic, version, len(indices), degree, fraction, flags, reserved)
        data = header + b"".join(column[indices].tobytes() for column in columns)
        output = destination / f"section-{index}.spz"
        output.write_bytes(gzip.compress(data, compresslevel=4, mtime=0))
        assert gzip.decompress(output.read_bytes()) == data
    print(f"{source.parent.name}: {count:,} splats across {len(groups)} sections", flush=True)


def write_glb(destination, doc, binary):
    binary += bytes(-len(binary) % 4)
    doc['buffers'] = [{'byteLength': len(binary)}]
    metadata = json.dumps(doc, separators=(',', ':')).encode()
    metadata += b' ' * (-len(metadata) % 4)
    header = struct.pack('<4sIIII', b'glTF', 2, 28 + len(metadata) + len(binary), len(metadata), 0x4E4F534A)
    destination.write_bytes(header + metadata + struct.pack('<II', len(binary), 0x004E4942) + binary)


def add_swim_height_mask(doc, binary):
    # The SDK PBR mask threshold clips the skinned body independently of the
    # transparent water. Encode rest-pose height in vertex alpha; RGB stays white.
    for mesh in doc['meshes']:
        for primitive in mesh['primitives']:
            attrs = primitive['attributes']
            pos = doc['accessors'][attrs['POSITION']]
            view = doc['bufferViews'][pos['bufferView']]
            coords = np.ndarray((pos['count'], 3), '<f4', buffer=binary,
                                offset=view.get('byteOffset', 0) + pos.get('byteOffset', 0),
                                strides=(view.get('byteStride', 12), 4))
            rgba = np.full((pos['count'], 4), 255, dtype=np.uint8)
            rgba[:, 3] = np.clip(1 + np.rint(254 * coords[:, 1] / pos['max'][1]), 1, 255)
            del coords
            binary.extend(bytes(-len(binary) % 4))
            view_id = len(doc['bufferViews'])
            doc['bufferViews'].append({'buffer':0, 'byteOffset':len(binary),
                                       'byteLength':rgba.nbytes, 'target':34962})
            binary.extend(rgba.tobytes())
            attrs['COLOR_0'] = len(doc['accessors'])
            doc['accessors'].append({'bufferView':view_id, 'componentType':5121, 'normalized':True,
                                     'count':pos['count'], 'type':'VEC4'})
            material = doc['materials'][primitive['material']]
            material.update(alphaMode='MASK', alphaCutoff=0.0)


def export_actor(source, destination):
    raw = source.read_bytes()
    size, = struct.unpack_from('<I', raw, 12)
    doc = json.loads(raw[20:20 + size])
    binary = bytearray(raw[28 + size:])
    # A baked splat contains its own light but does not illuminate the rig. Give
    # moving characters the same texture-coloured ambient term as Tung's rig.
    for material in doc['materials']:
        pbr = material['pbrMetallicRoughness']
        pbr['metallicFactor'] = 0.0
        material['emissiveTexture'] = pbr['baseColorTexture']
        material['emissiveFactor'] = [.60 * c for c in pbr.get('baseColorFactor', [1, 1, 1])[:3]]
    for clip in doc['animations']:
        clip['name'] = 'Walk' if 'walk' in clip['name'].lower() else 'Run'
        for channel in clip['channels']:
            target = channel['target']
            node = doc['nodes'][target['node']]
            if target['path'] != 'translation' or not node['name'].lower().endswith('hips'):
                continue
            # As on the website, navigation owns X/Z and the rig retains its vertical gait.
            accessor = doc['accessors'][clip['samplers'][channel['sampler']]['output']]
            view = doc['bufferViews'][accessor['bufferView']]
            offset = view.get('byteOffset', 0) + accessor.get('byteOffset', 0)
            assert accessor['componentType'] == 5126 and accessor['type'] == 'VEC3'
            hip = node['translation']
            values = np.ndarray((accessor['count'], 3), '<f4', buffer=binary, offset=offset,
                                strides=(view.get('byteStride', 12), 4))
            values[:, 0], values[:, 2] = hip[0], hip[2]
            accessor['min'], accessor['max'] = values.min(0).tolist(), values.max(0).tolist()
            del values
    if destination.parent.name in ('secret-capybara-toilet', 'niu-lai', 'tralalero-tralala'):
        add_swim_height_mask(doc, binary)
    write_glb(destination, doc, binary)


def export_pond_mask(nav, destination, subdivisions=4):
    # Same shoreline and Gaussian falloff as the website's 512 px water mask.
    cell = nav['cell']
    points = np.array([list(map(int, key.split(','))) for key in nav['ground']])
    low, high = points.min(0), points.max(0) + 1
    span = high - low
    # Extend the lattice past the outermost water cells so the final edge fades.
    origin = low - 4
    cols, rows = (span + 8) * subdivisions + 1
    data = np.zeros((rows, cols))
    for key in nav.get('waterCells', nav['ground']):
        if nav['ground'].get(key, nav['waterLevel']) >= nav['waterLevel']:
            continue
        x, z = (np.array(list(map(int, key.split(',')))) - origin) * subdivisions
        data[z:z+subdivisions, x:x+subdivisions] = 1
    for axis, length in ((0, span[1]), (1, span[0])):
        sigma = 7 * length * subdivisions / 512
        radius = math.ceil(3 * sigma)
        kernel = np.exp(-.5 * (np.arange(-radius, radius+1) / sigma)**2)
        kernel /= kernel.sum()
        data = np.apply_along_axis(lambda line: np.convolve(line, kernel, mode='same'), axis, data)
    header = struct.pack('>IIffff', cols, rows, *(origin * cell), cell / subdivisions, nav['waterLevel'])
    destination.write_bytes(header + np.round(data * 255).astype(np.uint8).tobytes())


def export_shorelines(nav, destination):
    regions = nav.get('waterRegions')
    if regions:
        for i, region in enumerate(regions):
            water = dict(nav, waterLevel=region['level'], waterCells=region['cells'])
            name = 'shoreline.bin' if i == 0 else f'shoreline-{i}.bin'
            export_pond_mask(water, destination / name, subdivisions=2)
    else:
        export_pond_mask(nav, destination / 'shoreline.bin')


def export_water(nav, destination):
    if 'waterLevel' not in nav:
        return
    positions, triangles, shared = [], [], {}
    cell = nav['cell']
    for key, ground in nav['ground'].items():
        level = nav.get('waterLevels', {}).get(key, nav['waterLevel'])
        if ground >= level or ('waterCells' in nav and key not in nav['waterCells']):
            continue
        ix, iz = map(int, key.split(','))
        face = []
        for x, z in [(ix, iz), (ix, iz+1), (ix+1, iz), (ix+1, iz+1)]:
            vertex = (x, z, level)
            if vertex not in shared:
                shared[vertex] = len(positions)
                positions.append([x * cell, level, z * cell])
            face.append(shared[vertex])
        a,b,c,d = face
        triangles.extend((a,b,c,c,b,d))
    vertices = np.array(positions, dtype='<f4')
    indices = np.array(triangles, dtype='<u2')
    assert len(vertices) < 65536
    pos, idx = vertices.tobytes(), indices.tobytes()
    doc = dict(asset={'version':'2.0'}, scene=0, scenes=[{'nodes':[0]}], nodes=[{'mesh':0}],
        meshes=[{'primitives':[{'attributes':{'POSITION':0}, 'indices':1, 'material':0}]}],
        extensionsUsed=['KHR_materials_unlit'],
        materials=[{'name':'Water surface', 'doubleSided':True,'alphaMode':'BLEND',
                    'extensions':{'KHR_materials_unlit':{}},'pbrMetallicRoughness':{
                    'baseColorFactor':[.08,.43,.48,.58],'metallicFactor':0,'roughnessFactor':.25}}],
        bufferViews=[{'buffer':0,'byteLength':len(pos)}, {'buffer':0,'byteOffset':len(pos),'byteLength':len(idx)}],
        accessors=[{'bufferView':0,'componentType':5126,'count':len(vertices),'type':'VEC3',
                   'min':vertices.min(0).tolist(),'max':vertices.max(0).tolist()},
                   {'bufferView':1,'componentType':5123,'count':len(indices),'type':'SCALAR'}])
    write_glb(destination, doc, pos + idx)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('dist', type=Path)
    args = parser.parse_args()
    for slug, folder in WORLDS.items():
        source = args.dist / 'assets' / folder
        destination = ASSETS / 'worlds' / slug
        destination.mkdir(parents=True, exist_ok=True)
        nav = (json.loads(subprocess.check_output(['node', Path(__file__).with_name('capybara_bank.mjs'), source]))
               if slug == 'secret-capybara-toilet' else json.loads((source / 'navigation-playable.json').read_text()))
        if slug != 'tung-tung-tung':  # The existing courtyard and its three-clip rig are already full detail.
            sx, sy, sz = nav['spawn']
            export_sections(source / 'ceramic.spz', destination, (sx, -sy-.66, -sz-1.08))
            export_actor(args.dist / 'assets' / 'world-characters' / f'{slug}.glb', destination / 'actor.glb')
        if slug in ('secret-capybara-toilet', 'tralalero-tralala', 'niu-lai'):
            export_shorelines(nav, destination)
        else:
            export_water(nav, destination / 'water.glb')
