"""Bake the playable rigs' head and neck skin transforms for world-space wearables.

The input GLBs contain authored skinning and walking clips. Sample their head/neck
joint matrices from the original animations so rigid, separately equipped models
can share the same gait. Requires NumPy.
"""
import json
import math
import struct
from pathlib import Path

import numpy as np

ASSETS = Path(__file__).resolve().parents[1] / 'app/src/main/assets'


def transform(translation, rotation, scale):
    x, y, z, w = rotation / np.linalg.norm(rotation)
    out = np.eye(4)
    out[:3, :3] = np.array([
        [1-2*(y*y+z*z), 2*(x*y-z*w), 2*(x*z+y*w)],
        [2*(x*y+z*w), 1-2*(x*x+z*z), 2*(y*z-x*w)],
        [2*(x*z-y*w), 2*(y*z+x*w), 1-2*(x*x+y*y)],
    ]) @ np.diag(scale)
    out[:3, 3] = translation
    return out


def bake(path, output):
    data = path.read_bytes()
    size, = struct.unpack_from('<I', data, 12)
    doc = json.loads(data[20:20+size])
    binary = data[28+size:]

    def read(i):
        a = doc['accessors'][i]
        assert a['componentType'] == 5126
        v = doc['bufferViews'][a['bufferView']]
        width = {'VEC3': 3, 'VEC4': 4, 'SCALAR': 1, 'MAT4': 16}[a['type']]
        return np.ndarray((a['count'], width), '<f4', buffer=binary,
                          offset=v.get('byteOffset', 0) + a.get('byteOffset', 0),
                          strides=(v.get('byteStride', width*4), 4)).astype(np.float64)

    nodes = doc['nodes']
    parents = {child: parent for parent, node in enumerate(nodes) for child in node.get('children', [])}
    skin = doc['skins'][0]
    binds = read(skin['inverseBindMatrices']).reshape(-1, 4, 4).transpose(0, 2, 1)
    joints = [next(i for i in skin['joints'] if nodes[i]['name'].endswith(':'+name))
              for name in ('Head', 'Neck')]

    def pose(i, time, channels):
        def sample(key, default):
            if (i, key) not in channels:
                return np.asarray(nodes[i].get(key, default))
            times, values, interpolation = channels[i, key]
            if time <= times[0]:
                return values[0]
            if time >= times[-1]:
                return values[-1]
            right = int(np.searchsorted(times, time, side='right'))
            left = right - 1
            if interpolation == 'STEP':
                return values[left]
            weight = (time-times[left]) / (times[right]-times[left])
            a, b = values[left], values[right]
            if key == 'rotation' and np.dot(a, b) < 0:
                b = -b
            return a + weight*(b-a)
        local = transform(sample('translation', [0, 0, 0]), sample('rotation', [0, 0, 0, 1]),
                          sample('scale', [1, 1, 1]))
        return pose(parents[i], time, channels) @ local if i in parents else local

    out = bytearray(struct.pack('>B', len(doc['animations'])))
    for clip in doc['animations']:
        channels = {}
        for ch in clip['channels']:
            s = clip['samplers'][ch['sampler']]
            interpolation = s.get('interpolation', 'LINEAR')
            assert interpolation in ('LINEAR', 'STEP')
            channels[ch['target']['node'], ch['target']['path']] = (
                read(s['input'])[:, 0], read(s['output']), interpolation)
        duration = max(times[-1] for times, _, _ in channels.values())
        frames = math.ceil(duration * 30) + 1
        name = clip['name'].encode('ascii')
        out.extend(struct.pack('>H', len(name)) + name + struct.pack('>fH', duration, frames))
        for time in np.linspace(0, duration, frames):
            for joint in joints:
                # Shifts a point from the original bind mesh into the current animated mesh.
                m = pose(joint, time, channels) @ binds[skin['joints'].index(joint)]
                pitch = math.asin(np.clip(-m[1, 2], -1, 1))
                yaw = math.atan2(m[0, 2], m[2, 2])
                roll = math.atan2(m[1, 0], m[1, 1])
                out.extend(struct.pack('>6f', *m[:3, 3], *np.degrees([yaw, pitch, roll])))
    output.write_bytes(out)


if __name__ == '__main__':
    dest = ASSETS / 'accessory-motion'
    dest.mkdir(exist_ok=True)
    for source in sorted((ASSETS/'worlds').glob('*/actor.glb')):
        bake(source, dest / (source.parent.name + '.bin'))
    bake(ASSETS/'tung-walking.glb', dest/'tung-tung-tung.bin')
