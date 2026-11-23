"""Project the shipped meshes into their local placement planes, in metres.

Built-in loft group pivots, the machine's authored yaw and wall-framing planes match the game.
The Android renderer sweeps one narrow, closed stroke around each polygon.
"""
from pathlib import Path
import json
import math
import struct
import numpy as np

ASSETS = Path(__file__).resolve().parents[1] / 'app/src/main/assets'
BACK_WALL = {'unboxing-print', 'slop-print', 'capybara-print', 'calendar', 'convex-mirror'}


class Glb:
    def __init__(self, path):
        raw = path.read_bytes()
        length = struct.unpack_from('<I', raw, 12)[0]
        self.doc = json.loads(raw[20:20+length])
        self.buffer = raw[28+length:]

    def positions(self, accessor_id):
        accessor = self.doc['accessors'][accessor_id]
        view = self.doc['bufferViews'][accessor['bufferView']]
        assert accessor['componentType'] == 5126 and accessor['type'] == 'VEC3'
        return np.ndarray((accessor['count'], 3), '<f4', self.buffer,
                          offset=view.get('byteOffset', 0)+accessor.get('byteOffset', 0),
                          strides=(view.get('byteStride', 12), 4))

    def points(self, nodes=None):
        def walk(index, parent):
            node = self.doc['nodes'][index]
            if 'matrix' in node:
                local = np.array(node['matrix']).reshape(4, 4).T
            else:
                x,y,z,w = node.get('rotation', [0,0,0,1])
                rotation = np.array([
                    [1-2*(y*y+z*z), 2*(x*y-z*w), 2*(x*z+y*w)],
                    [2*(x*y+z*w), 1-2*(x*x+z*z), 2*(y*z-x*w)],
                    [2*(x*z-y*w), 2*(y*z+x*w), 1-2*(x*x+y*y)]
                ])
                local = np.eye(4)
                local[:3,:3] = rotation @ np.diag(node.get('scale', [1,1,1]))
                local[:3,3] = node.get('translation', [0,0,0])
            transform = parent @ local
            if 'mesh' in node:
                for primitive in self.doc['meshes'][node['mesh']]['primitives']:
                    vertices = self.positions(primitive['attributes']['POSITION'])
                    yield vertices @ transform[:3,:3].T + transform[:3,3]
            for child in node.get('children', []):
                yield from walk(child, transform)
        if nodes is None:
            nodes = self.doc['scenes'][self.doc.get('scene', 0)]['nodes']
        return np.concatenate([points for node in nodes for points in walk(node, np.eye(4))])


def boundary(vertices):
    """Counterclockwise convex footprint, with submillimetre details simplified at game scale."""
    points = list(map(tuple, np.unique(np.round(vertices, 5), axis=0)))
    def half(points):
        result = []
        for p in points:
            while len(result) >= 2:
                a,b = result[-2:]
                cross = (b[0]-a[0])*(p[1]-a[1]) - (b[1]-a[1])*(p[0]-a[0])
                if cross > 0:
                    break
                result.pop()
            result.append(p)
        return result[:-1]
    hull = half(points) + half(reversed(points))
    # Preserve min/max extent even when it occurs on a finely tessellated curve.
    coords = np.array(hull)
    extremes = set(hull[i] for i in list(coords.argmin(axis=0)) + list(coords.argmax(axis=0)))
    tolerance = max(.0004, np.ptp(coords, axis=0).max()*.0013)
    while len(hull) > 4:
        candidates = []
        for i,b in enumerate(hull):
            if b in extremes:
                continue
            a,c = hull[i-1],hull[(i+1)%len(hull)]
            area = abs((c[0]-a[0])*(b[1]-a[1]) - (c[1]-a[1])*(b[0]-a[0]))
            candidates.append((area / max(math.dist(a,c), 1e-12), i))
        if not candidates:
            break
        error,index = min(candidates)
        if len(hull) <= 64 and error > tolerance:
            break
        hull.pop(index)
    return [[round(float(x), 5), round(float(y), 5)] for x,y in hull]


def main():
    outlines = {}
    loft = Glb(ASSETS/'collection-room.glb')
    for node in loft.doc['nodes']:
        name = node.get('name', '')
        if not name.startswith('furnishing_'):
            continue
        key = name.removeprefix('furnishing_')
        vertices = loft.points(node['children'])
        axes = (2,1) if key == 'ochre-canvas' else (0,1) if key in BACK_WALL else (0,2)
        outlines[key] = boundary(vertices[:,axes])

    vertices = Glb(ASSETS/'props/gachapon.glb').points()[:,[0,2]]
    yaw = math.atan2(3.62, 3.70)
    outlines['machine'] = boundary(vertices @ np.array([[math.cos(yaw), -math.sin(yaw)],
                                                       [math.sin(yaw), math.cos(yaw)]]))
    catalogue = json.loads((ASSETS/'furniture/catalogue.json').read_text())
    for key,_,category,*_ in catalogue:
        vertices = Glb(ASSETS/'furniture'/f'{key}.glb').points()
        outlines[key] = boundary(vertices[:,[0,1] if category == 'frame' else [0,2]])
    for path in sorted((ASSETS/'furniture').glob('painting_*.glb')):
        figure = path.stem.removeprefix('painting_').replace('_','-')
        outlines['art:'+figure] = boundary(Glb(path).points()[:,[0,1]])
    for path in sorted((ASSETS/'figures').glob('*.glb')):
        outlines['figure:'+path.stem] = boundary(Glb(path).points()[:,[0,2]])
    target = ASSETS/'furniture/footprints.json'
    target.write_text('{\n' + ',\n'.join('  '+json.dumps(k)+':'+json.dumps(v,separators=(',',':'))
                                         for k,v in sorted(outlines.items())) + '\n}\n')
    print(f'{len(outlines)} footprints, {sum(map(len,outlines.values()))} boundary vertices')

if __name__ == '__main__':
    main()
