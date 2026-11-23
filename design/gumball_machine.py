"""Build the daily-draw props from the supplied Tripo GLB and the game's box.glb.

Run with Python, NumPy and Pillow: python gumball_machine.py <machine.glb> <font.ttf>
Coordinates are glTF Y-up, with the outlet facing +Z. Lengths in exported assets are metres.
"""
import argparse
import io
import json
import struct
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ASSETS = Path(__file__).resolve().parents[1] / "app/src/main/assets"
SCALE = 1.8


class Source:
    def __init__(self, path):
        data = path.read_bytes()
        size = struct.unpack_from("<I", data, 12)[0]
        self.doc = json.loads(data[20:20 + size])
        self.data = data[28 + size:]

    def array(self, index):
        a = self.doc["accessors"][index]
        view = self.doc["bufferViews"][a["bufferView"]]
        width = {"SCALAR": 1, "VEC2": 2, "VEC3": 3}[a["type"]]
        dt = np.dtype({5123: "<u2", 5125: "<u4", 5126: "<f4"}[a["componentType"]])
        return np.ndarray((a["count"], width), dt, self.data,
                          view.get("byteOffset", 0) + a.get("byteOffset", 0),
                          (view.get("byteStride", width * dt.itemsize), dt.itemsize))

    def texture(self, primitive):
        pbr = self.doc["materials"][primitive["material"]]["pbrMetallicRoughness"]
        img = self.doc["textures"][pbr["baseColorTexture"]["index"]]["source"]
        view = self.doc["bufferViews"][self.doc["images"][img]["bufferView"]]
        start = view.get("byteOffset", 0)
        return np.array(Image.open(io.BytesIO(self.data[start:start + view["byteLength"]])).convert("RGB"))


class Prop:
    def __init__(self, name):
        self.data = bytearray()
        self.doc = dict(asset={"version": "2.0"}, scene=0, scenes=[{"nodes": [0]}],
                        nodes=[{"name": name, "mesh": 0}], meshes=[{"primitives": []}],
                        materials=[], accessors=[], bufferViews=[])

    def view(self, raw):
        view = dict(buffer=0, byteOffset=len(self.data), byteLength=len(raw))
        self.data.extend(raw)
        self.data.extend(b'\0' * (-len(self.data) % 4))
        self.doc["bufferViews"].append(view)
        return len(self.doc["bufferViews"]) - 1

    def array(self, data, index=False):
        a = np.ascontiguousarray(data, dtype="<u2" if index else "<f4")
        kind = "SCALAR" if index else {2: "VEC2", 3: "VEC3", 4: "VEC4"}[a.shape[1]]
        self.doc["accessors"].append(dict(bufferView=self.view(a.tobytes()), count=len(a), type=kind,
                                          componentType=5123 if index else 5126,
                                          min=np.atleast_1d(a.min(axis=0)).tolist(),
                                          max=np.atleast_1d(a.max(axis=0)).tolist()))
        return len(self.doc["accessors"]) - 1

    def material(self, name, rgb, rough, metal=0, alpha=1, ambient=.24):
        pbr = dict(baseColorFactor=[*rgb, alpha], roughnessFactor=rough, metallicFactor=metal)
        mat = dict(name=name, pbrMetallicRoughness=pbr, emissiveFactor=[c * ambient for c in rgb])
        if alpha < 1:
            mat["alphaMode"] = "BLEND"
        self.doc["materials"].append(mat)
        return len(self.doc["materials"]) - 1

    def surface(self, vertices, normals, faces, material, **attributes):
        used, indices = np.unique(np.asarray(faces).ravel(), return_inverse=True)
        assert len(used) <= 65535
        arrays = dict(POSITION=vertices, NORMAL=normals, **attributes)
        self.doc["meshes"][0]["primitives"].append(dict(
            attributes={key: self.array(np.asarray(a)[used]) for key, a in arrays.items()},
            indices=self.array(indices, index=True), material=material))

    def save(self, name):
        self.doc["buffers"] = [dict(byteLength=len(self.data))]
        payload = json.dumps(self.doc, separators=(",", ":")).encode()
        payload += b" " * (-len(payload) % 4)
        size = 28 + len(payload) + len(self.data)
        (ASSETS / "props" / name).write_bytes(struct.pack("<4sIIII", b"glTF", 2, size, len(payload), 0x4e4f534a)
                                              + payload + struct.pack("<II", len(self.data), 0x004e4942) + self.data)


def components(vertices, faces):
    # UV seams duplicate vertex indices in the supplied single-mesh export.
    points, welded = np.unique(vertices, axis=0, return_inverse=True)
    parent = list(range(len(points)))

    def root(i):
        while i != parent[i]:
            parent[i] = parent[parent[i]]
            i = parent[i]
        return i

    for a, b, c in welded[faces]:
        lead = root(a)
        parent[root(b)] = parent[root(c)] = lead
    labels = np.array([root(i) for i in range(len(points))])[welded[faces[:, 0]]]
    for label in np.unique(labels):
        mask = labels == label
        pts = vertices[faces[mask]]
        yield mask, pts.min(axis=(0, 1)), pts.max(axis=(0, 1))


def grid(u_count, v_count):
    faces = []
    for i in range(v_count - 1):
        for j in range(u_count - 1):
            a = i * u_count + j
            faces.extend(((a, a + 1, a + u_count), (a + 1, a + u_count + 1, a + u_count)))
    return np.array(faces)


def patch(latitudes, longitudes, radius, center):
    lon, lat = np.meshgrid(longitudes, latitudes)
    normal = np.stack((np.cos(lat) * np.sin(lon), np.sin(lat), np.cos(lat) * np.cos(lon)), axis=-1).reshape(-1, 3)
    return normal * radius + center, normal, grid(len(longitudes), len(latitudes))


def machine(path, font_path):
    source = Source(path)
    p = source.doc["meshes"][0]["primitives"][0]
    v = source.array(p["attributes"]["POSITION"]).copy()
    n = source.array(p["attributes"]["NORMAL"])
    faces = source.array(p["indices"]).reshape(-1, 3)
    prop = Prop("Slop gumball machine")
    red = prop.material("Cherry enamel", [.83, .012, .027], .23, .24, ambient=.53)
    silver = prop.material("Brushed nickel", [.60, .63, .66], .31, .68)
    black = prop.material("Outlet interior", [.012, .008, .009], .72)
    glass = prop.material("Clear polished glass", [.85, .95, 1], .075, .05, 1, .65)
    prop.doc["materials"][glass]["alphaMode"] = "BLEND"
    uv = source.array(p["attributes"]["TEXCOORD_0"])
    texture = source.texture(p)
    tu, tv = ((uv[faces].mean(axis=1) % 1) * [texture.shape[1]-1, texture.shape[0]-1]).astype(int).T
    pigment = texture[tv,tu].astype(float)
    neutral = (pigment[:,0]-pigment[:,1]) < 66
    centers = v[faces].mean(axis=1)
    x, y, z = centers.T
    body = np.zeros(len(faces), bool)
    plate = body.copy()
    cavity = body.copy()
    for mask, lo, hi in components(v, faces):
        if lo[1] < .001:  # cabinet, outlet surround, mounting plate and original opaque bowl
            body |= mask & (y < .446)
            plate |= mask & (y > .234) & (y < .435) & (abs(x) < .082) & (z > .13) & neutral
            aperture = (np.abs(v[:,0]+.0017) < .062) & (v[:,1] > .056) & (v[:,1] < .18) & (v[:,2] > .074)
            cavity |= mask & aperture[faces].all(axis=1)
        elif lo[1] > .923:  # lid, rim and crown
            body |= mask
        elif lo[1] > .235 and hi[1] < .437 and lo[2] > .148:
            body |= mask
            plate |= mask
        elif .06 < lo[1] < .07 and hi[2] < .10:  # recessed back of the outlet
            body |= mask
            cavity |= mask
    # The scan's lever points out like a flap. Keep its faceplate and flatten its protrusion
    # into the boss behind the separate working crank.
    trim = (v[:, 1] > .236) & (v[:, 1] < .44) & (v[:, 2] > .176)
    v[trim, 2] = .176 + .08 * (v[trim, 2] - .176)
    v[:, 0] += .0017
    for mask, mat in ((body & ~plate & ~cavity, red), (body & plate, silver), (body & cavity, black)):
        prop.surface(v * SCALE, n, faces[mask], mat)
    # Fit a smooth sphere between the original rolled collar and cap.
    radius, cy = .311, .699
    low, high = np.arcsin((np.array([.443, .931]) - cy) / radius)
    pos, normal, idx = patch(np.linspace(low, high, 41), np.linspace(-np.pi, np.pi, 97), radius, [0, cy, 0])
    # A feathered window reflection and grazing-angle tint give the clear surface a readable
    # silhouette in the loft at night. Only a few percent of its front face reflects light.
    nx, ny, nz = normal.T
    grazing = (1-np.abs(nz))**3
    azimuth = np.arctan2(nx,nz)
    elevation = np.arcsin(ny)
    def softbox(lon, lat, width, height):
        return np.exp(-((azimuth-lon)/width)**4-((elevation-lat)/height)**4)
    reflect = .14*softbox(-.82,.44,.11,.27) + .07*softbox(.91,.04,.055,.32)
    alpha = .038 + .34*grazing + reflect
    rgba = np.column_stack((np.ones((len(normal),3)),alpha))
    prop.surface(pos * SCALE, normal, idx, glass, COLOR_0=rgba)
    # Interior seat and underside of the lid close the clipped glass when seen from above or below.
    for height, r, up, mat in ((.446, .18, 1, red), (.927, .206, -1, red)):
        a = np.linspace(0, 2*np.pi, 65)
        rim = np.stack((r*np.sin(a), np.full_like(a, height), r*np.cos(a)), axis=1)
        pts = np.vstack(([0, height, 0], rim))
        fan = np.array([(0, i, i+1) for i in range(1,65)])
        prop.surface(pts*SCALE, np.tile([0,up,0],(len(pts),1)), fan if up==1 else fan[:,::-1], mat)
    # Small curved ink transfer. Transparent pixels expose the glass, even in a side view.
    font = ImageFont.truetype(str(font_path), 196)
    bounds = font.getbbox("SLOP")
    logo = Image.new("RGBA", (bounds[2]-bounds[0]+14, bounds[3]-bounds[1]+14))
    draw = ImageDraw.Draw(logo)
    draw.text((7-bounds[0], 7-bounds[1]), "SLOP", font=font, fill=(0,0,0,0),
              stroke_width=6, stroke_fill=(228,214,209,255))
    draw.text((7-bounds[0], 7-bounds[1]), "SLOP", font=font, fill=(0,0,0,0),
              stroke_width=4, stroke_fill=(30,19,24,255))
    buf = io.BytesIO()
    logo.save(buf, format="PNG")
    prop.doc["images"] = [dict(mimeType="image/png", bufferView=prop.view(buf.getvalue()))]
    prop.doc["textures"] = [dict(source=0)]
    ink = prop.material("SLOP screen print", [1,1,1], .4, ambient=.42)
    prop.doc["materials"][ink]["emissiveTexture"] = {"index": 0}
    prop.doc["materials"][ink].update(alphaMode="MASK", alphaCutoff=.30)
    prop.doc["materials"][ink]["pbrMetallicRoughness"]["baseColorTexture"] = {"index": 0}
    u, w = 33, 9
    lon_span = .56
    lat_span = lon_span * logo.height/logo.width
    pos, normal, idx = patch(np.linspace(.015-lat_span, .015+lat_span, w),
                             np.linspace(-lon_span, lon_span, u), radius+.0008, [0,cy,0])
    uv = np.stack(np.meshgrid(np.linspace(0,1,u),np.linspace(1,0,w)),axis=-1).reshape(-1,2)
    prop.surface(pos*SCALE,normal,idx,ink,TEXCOORD_0=uv)
    # Coin slit above the axle, on the flattening of the original nickel mechanism.
    slit = np.array([[-.023,.402,.184],[.023,.402,.184],[-.023,.408,.184],[.023,.408,.184]])
    prop.surface(slit*SCALE,np.tile([0,0,1],(4,1)),grid(2,2),black)
    # Recess a flat black liner immediately behind the mouth; it covers the round imprint
    # left in the scan when the static prize was removed.
    liner = np.array([[-.052,.065,.131],[.052,.065,.131],[-.052,.169,.131],[.052,.169,.131]])
    prop.surface(liner*SCALE,np.tile([0,0,1],(4,1)),grid(2,2),black)
    prop.save("gachapon.glb")


def capsule(name, top, bottom):
    prop = Prop(name)
    materials = [prop.material(label,c,.19,.08,ambient=.37) for label,c in
                 (("Lower shell",bottom),("Equator lip",[v*.65 for v in bottom]),("Upper shell",top))]
    # Unit radius centered at zero for both loose stock and the prize in the outlet.
    for a,b,mat in ((-np.pi/2,-.025,0),(-.025,.018,1),(.018,np.pi/2,2)):
        pos,n,faces = patch(np.linspace(a,b,2 if mat==1 else 11),np.linspace(-np.pi,np.pi,33),1,[0,0,0])
        prop.surface(pos,n,faces,materials[mat])
    prop.save(name+".glb")


def box_atlas(points, rgb, vertices, normals, faces):
    # Orthographic bakes retain the small box's lettering, hinge and lid even when its
    # geometry is simplified. Use a single six-face atlas for the whole miniature.
    side = 192
    atlas = np.zeros((side*2,side*3,3),dtype=np.uint8)
    lo, hi = points.min(axis=0), points.max(axis=0)
    unit = (points-lo)/(hi-lo)
    unit_vertices = (vertices-lo)/(hi-lo)
    avg_normals = normals[faces].mean(axis=1)
    axes = abs(avg_normals).argmax(axis=1)
    signs = np.sign(avg_normals[np.arange(len(faces)),axes])
    out_v, out_n, out_uv, out_f = [], [], [], []
    offset = 0
    for tile, (axis,sign,horizontal,vertical) in enumerate(
            [(2,1,0,1),(2,-1,0,1),(0,1,2,1),(0,-1,2,1),(1,1,0,2),(1,-1,0,2)]):
        def project(positions):
            return np.clip(2 + positions[:,[horizontal,vertical]]*(side-5),0,side-1)
        x,y = project(unit).round().astype(int).T
        pixels = (side-1-y)*side+x
        closest = np.full(side*side,-np.inf)
        depth = sign*points[:,axis]
        np.maximum.at(closest,pixels,depth)
        visible = depth >= closest[pixels]-.00001
        color = np.zeros((side,side,3),np.uint8)
        color.reshape(-1,3)[pixels[visible]] = rgb[visible]
        known = np.isfinite(closest).reshape(side,side)
        # Dilate into holes and beyond the UV islands to cover mip/filter footprints.
        for _ in range(8):
            valid = known.copy()
            for dim, shift in ((0,1),(1,1),(0,-1),(1,-1)):
                fill = ~known & np.roll(valid,shift,axis=dim)
                color[fill] = np.roll(color,shift,axis=dim)[fill]
                known |= fill
        ox,oy = (tile%3)*side, (tile//3)*side
        atlas[oy:oy+side,ox:ox+side] = color
        used, idx = np.unique(faces[(axes==axis) & (signs==sign)],return_inverse=True)
        u,v = project(unit_vertices[used]).T
        out_uv.append(np.stack(((ox+u+.5)/(3*side),(oy+side-.5-v)/(2*side)),axis=1))
        out_v.append(vertices[used]); out_n.append(normals[used])
        out_f.append(idx.reshape(-1,3)+offset)
        offset += len(used)
    return (np.concatenate(out_v),np.concatenate(out_n),np.concatenate(out_f),
            np.concatenate(out_uv),Image.fromarray(atlas))


def miniature():
    # Keep the source geometry in each original part, then batch all 26 pieces into one material.
    source = Source(ASSETS/"box.glb")
    positions, normals, triangles = [], [], []
    samples, paint = [], []
    offset = 0
    for mesh in source.doc["meshes"]:
        p = mesh["primitives"][0]
        attrs = p["attributes"]
        v, n, uv = [source.array(attrs[k]) for k in ("POSITION","NORMAL","TEXCOORD_0")]
        tex = source.texture(p)
        ix, iy = ((uv % 1) * [tex.shape[1]-1,tex.shape[0]-1]).astype(int).T
        source_faces = source.array(p["indices"]).reshape(-1,3)
        tx,ty = ((uv[source_faces].mean(axis=1)%1)*[tex.shape[1]-1,tex.shape[0]-1]).astype(int).T
        samples.extend((v,v[source_faces].mean(axis=1)))
        paint.extend((tex[iy,ix],tex[ty,tx]))
        cells, inv = np.unique(np.round(v/.038).astype(int),axis=0,return_inverse=True)
        counts = np.bincount(inv)
        def mean(a):
            return np.stack([np.bincount(inv,weights=dim)/counts for dim in a.T],axis=1)
        pos, norm = mean(v), mean(n)
        norm /= np.maximum(np.linalg.norm(norm,axis=1,keepdims=True),1e-6)
        faces = inv[source.array(p["indices"]).reshape(-1,3)]
        faces = faces[(faces[:,0]!=faces[:,1]) & (faces[:,0]!=faces[:,2]) & (faces[:,1]!=faces[:,2])]
        _, unique = np.unique(np.sort(faces,axis=1),axis=0,return_index=True)
        triangles.append(faces[unique]+offset)
        positions.append(pos); normals.append(norm)
        offset += len(pos)
    prop = Prop("Miniature Slop blind box")
    mat = prop.material("Printed red box", [1,1,1], .45, ambient=.52)
    pos,norm,faces,uv,atlas = box_atlas(np.concatenate(samples),np.concatenate(paint),
        np.concatenate(positions),np.concatenate(normals),np.concatenate(triangles))
    buf = io.BytesIO()
    atlas.save(buf,format="PNG")
    prop.doc["images"] = [dict(mimeType="image/png",bufferView=prop.view(buf.getvalue()))]
    prop.doc["textures"] = [dict(source=0)]
    prop.doc["materials"][mat]["pbrMetallicRoughness"]["baseColorTexture"] = {"index": 0}
    prop.doc["materials"][mat]["emissiveTexture"] = {"index": 0}
    pos -= (pos.max(axis=0)+pos.min(axis=0))*.5
    prop.surface(pos,norm,faces,mat,TEXCOORD_0=uv)
    prop.save("mini-slop.glb")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("model",type=Path)
    parser.add_argument("font",type=Path)
    args = parser.parse_args()
    machine(args.model,args.font)
    capsule("gumball-red",[.98,.045,.062],[.62,.006,.015])
    capsule("gumball-pink",[1,.50,.60],[.97,.19,.31])
    miniature()
