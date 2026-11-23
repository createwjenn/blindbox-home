// Usage: node import_world_navigation.mjs <website dist directory> [slug]
// Import only bidirectionally traversable, spawn-connected paths. A separate node
// for each elevation keeps the library landings and stairs on their correct floors.
import assert from 'node:assert/strict';
import {gardenNavigation} from './capybara_bank.mjs';
import {readFileSync, writeFileSync, mkdirSync} from 'node:fs';
import path from 'node:path';
import {pathToFileURL, fileURLToPath} from 'node:url';
const dist = path.resolve(process.argv[2]);
const dest = fileURLToPath(new URL('../app/src/main/assets/worlds/', import.meta.url));
const source = (name) => pathToFileURL(path.join(dist, name)).href;
const {worldConfig} = await import(source('world-config.js'));
const {canTravel, canStand, surfaceAt, waterDepth, waterLevelAt} = await import(source('world-movement.js'));
const {reachablePoints} = await import(source('world-coin-state.js'));
const slugs = ['niu-lai','strawberlina','bananito','tung-tung-tung','tralalero-tralala',
               'ballerina-capuccina','chill-guy','secret-capybara-toilet'];

const moduleUrl = code => 'data:text/javascript;base64,' + Buffer.from(code).toString('base64');
const three = source('vendor/three.core.js');
const useThree = name => readFileSync(path.join(dist, name), 'utf8').replaceAll("'three'", JSON.stringify(three));
const utils = moduleUrl(useThree('vendor/BufferGeometryUtils.js'));
const pickups = useThree('world-pickups.js').replace("'./vendor/BufferGeometryUtils.js'", JSON.stringify(utils));
const {createPickupGeometry, pickupThemes} = await import(moduleUrl(pickups));

function writePickup(slug, output) {
    const g = createPickupGeometry(slug);
    const {position, normal, color} = g.attributes;
    // Splat captures cannot light the meshes. Bake a hemisphere fill and the
    // website's elevated key into the original linear vertex colours. A small
    // satin highlight keeps dark forms such as coffee beans readable in motion.
    for (let i = 0; i < color.count; i++) {
        const x = normal.getX(i), y = normal.getY(i), z = normal.getZ(i);
        const key = Math.max(0, -.324*x + .811*y + .487*z);
        const fill = 1.22 + .28*y;
        const highlight = .095 * Math.pow(Math.max(0, -.188*x + .470*y + .863*z), 12);
        const shade = channel => Math.min(1, channel * (fill + 1.35*key) + highlight);
        color.setXYZ(i, shade(color.getX(i)), shade(color.getY(i)), shade(color.getZ(i)));
    }
    writeColoredMesh(g, output);
}

function writeColoredMesh(g, output, doubleSided = false) {
    const {position, normal, color} = g.attributes;
    g.computeBoundingBox();
    let size = 0;
    const arrays = [position, normal, color].map(a => Buffer.from(a.array.buffer, a.array.byteOffset, a.array.byteLength));
    const bufferViews = arrays.map(a => {
        const view = {buffer:0, byteOffset:size, byteLength:a.length, target:34962};
        size += a.length;
        return view;
    });
    const doc = {
        asset:{version:'2.0'}, extensionsUsed:['KHR_materials_unlit'], scene:0, scenes:[{nodes:[0]}],
        nodes:[{mesh:0}], meshes:[{primitives:[{attributes:{POSITION:0,NORMAL:1,COLOR_0:2},material:0}]}],
        materials:[{...(doubleSided ? {doubleSided:true} : {}),extensions:{KHR_materials_unlit:{}},pbrMetallicRoughness:{roughnessFactor:1,metallicFactor:0}}],
        buffers:[{byteLength:size}], bufferViews,
        accessors:bufferViews.map((_,i) => ({bufferView:i, componentType:5126, type:'VEC3', count:position.count,
            ...(i === 0 ? {min:g.boundingBox.min.toArray(),max:g.boundingBox.max.toArray()} : {})}))
    };
    const json = Buffer.from(JSON.stringify(doc));
    const padded = (json.length + 3) & ~3;
    const header = Buffer.alloc(20);
    header.write('glTF'); header.writeUInt32LE(2,4); header.writeUInt32LE(28+padded+size,8);
    header.writeUInt32LE(padded,12); header.write('JSON',16);
    const bin = Buffer.alloc(8); bin.writeUInt32LE(size); bin.write('BIN\0',4);
    writeFileSync(output, Buffer.concat([header,json,Buffer.alloc(padded-json.length,32),bin,...arrays]));
}

// The website builds its koi procedurally. Export those same patch colours and tail surfaces.
async function writeKoi(nav, folder) {
    const pathsModule = source('world-koi-paths.js');
    const {createKoiPaths} = await import(pathsModule);
    const koiModule = useThree('world-koi.js').replace("'./world-koi-paths.js'", JSON.stringify(pathsModule));
    const {createWorldKoi} = await import(moduleUrl(koiModule));
    const {Scene, Float32BufferAttribute} = await import(three);
    const scene = new Scene();
    const school = createWorldKoi(scene, nav);
    for (const [i, instance] of scene.children.entries()) {
        const g = instance.geometry.index ? instance.geometry.toNonIndexed() : instance.geometry;
        const color = instance.material.color;
        if (!g.hasAttribute('color')) {
            const colors = Array.from({length:g.attributes.position.count}, () => color.toArray()).flat();
            g.setAttribute('color', new Float32BufferAttribute(colors, 3));
        }
        writeColoredMesh(g, path.join(folder, i === 0 ? 'koi-body.glb' : 'koi-tail.glb'), i === 1);
    }
    writeFileSync(path.join(folder,'koi-paths.json'), JSON.stringify({level:nav.waterLevel, paths:createKoiPaths(nav)}));
    school.dispose();
}

for (const slug of slugs.filter(id => !process.argv[3] || id === process.argv[3])) {
    const config = worldConfig(slug);
    const folderPath = path.join(dist, config.assets);
    const nav = slug === 'secret-capybara-toilet' ? gardenNavigation(folderPath) :
        JSON.parse(readFileSync(path.join(folderPath, 'navigation-playable.json')));
    const [x,y,z] = nav.spawn, spawn = {x,y,z};
    const wet = config.swimming;
    const key = p => [Math.floor(p.x / nav.cell), Math.floor(p.z / nav.cell), Math.round(p.y*1000)].join(',');
    // A swimmer crosses the bank at the surface. Testing the riverbed step
    // incorrectly rejects steep banks, even where the water reaches dry land.
    const surfaceSwimming = ['secret-capybara-toilet','tralalero-tralala','niu-lai'].includes(slug);
    let travel = nav;
    if (surfaceSwimming) {
        const ground = {...nav.ground};
        for (const key of Object.keys(ground)) {
            const [ix, iz] = key.split(',').map(Number);
            const x = (ix+.5)*nav.cell, z = (iz+.5)*nav.cell;
            if (waterDepth(nav,x,z) > .08) ground[key] = waterLevelAt(nav,x,z) - .08;
        }
        travel = {...nav, ground};
    }
    const points = reachablePoints(travel, spawn, wet);
    const ids = new Map(points.map((p,i) => [key(p), i]));
    const directions = [[1,0],[-1,0],[0,1],[0,-1]];
    const nodes = points.map(p => {
        const links = directions.map(([dx,dz]) => {
            const q = {x:p.x+dx*nav.cell, z:p.z+dz*nav.cell};
            q.y = surfaceAt(travel,q.x,q.z,wet,p.y);
            const id = ids.get(key(q));
            return id !== undefined && canTravel(travel,p,q,wet) && canTravel(travel,q,p,wet) ? id : -1;
        });
        const water = wet && waterDepth(nav,p.x,p.z) > 0 ? waterLevelAt(nav,p.x,p.z) : null;
        const ix = Math.floor(p.x/nav.cell), iz = Math.floor(p.z/nav.cell);
        const bed = surfaceSwimming && water !== null ? nav.ground[`${ix},${iz}`] : p.y;
        return [ix,iz, Math.round(bed*1000)/1000,water,...links];
    });
    // The website authors checked these pickup anchors against both its high-detail
    // and mobile captures. Snap each to the nearest traversable cell on its deck.
    const distance = (a,b) => Math.hypot(a.x-b.x, a.y-b.y, a.z-b.z);
    const clear = points.map((_, i) => i).filter(i => i !== 0 &&
        nodes[i].slice(4).filter(n => n >= 0).length >= 3 &&
        (slug !== 'secret-capybara-toilet' || distance(points[i], spawn) <= 3.15));
    const anchors = [...new Set(nav.pickupPoints.map(anchor => clear.reduce((best, i) =>
        distance(points[i], anchor) < distance(points[best], anchor) ? i : best)))];
    assert.ok(anchors.length > 0);
    // Small website rounds have fewer than 30 points. Fill their gaps on broad
    // walkable ground while prioritising the neighbourhood of verified anchors.
    const chosen = [];
    const candidates = new Set(anchors);
    while (chosen.length < 30) {
        if (candidates.size === 0) for (const i of clear) if (!chosen.includes(i)) candidates.add(i);
        const score = i => {
            if (!chosen.length) return -Math.abs(distance(points[i],spawn)-.65);
            const spacing = Math.min(...chosen.map(j => distance(points[i],points[j])));
            const nearestAnchor = Math.min(...anchors.map(j => distance(points[i],points[j])));
            return spacing / (1 + .25*nearestAnchor*nearestAnchor);
        };
        let best, value = -Infinity;
        for (const i of candidates) { const d = score(i); if (d > value) { best = i; value = d; } }
        assert.ok(best !== undefined,slug);
        chosen.push(best); candidates.delete(best);
    }
    // Size the full-deflection running pace to the actual course. Water travels
    // at 72% land speed. A conservative nearest-next tour leaves time to turn.
    let current = 0, effort = 0;
    const pending = new Set(chosen);
    while (pending.size) {
        const previous = new Int32Array(nodes.length).fill(-1);
        const queue = [current]; previous[current] = current;
        let target;
        for (let i=0; i<queue.length && target === undefined; i++) {
            const id = queue[i];
            if (pending.has(id)) { target=id; break; }
            for (const n of nodes[id].slice(4)) if (n>=0 && previous[n]<0) {
                previous[n]=id;queue.push(n);
            }
        }
        assert.ok(target !== undefined);
        for (let n=target; n !== current; n=previous[n]) {
            const cell=nodes[n];
            effort += nav.cell*2.4 / (cell[3] !== null && cell[3]-cell[2] > .06 ? .72 : 1);
        }
        pending.delete(target); current=target;
    }
    const courseSpeed = Math.max(1.9, Math.ceil(effort / 37 * 10)/10);
    const runSpeed = slug === "secret-capybara-toilet" ? 2.0 : courseSpeed;
    // Verify the exported adjacency itself, including a return route from deep water.
    const queue = [0], seen = new Set(queue);
    for (let i=0; i<queue.length; i++) for (const n of nodes[queue[i]].slice(4)) {
        if (n>=0 && !seen.has(n)) {seen.add(n);queue.push(n);}
    }
    assert.equal(seen.size, nodes.length);
    assert.equal(new Set(chosen).size, 30);
    assert.ok(chosen.every(i => seen.has(i)));
    if (wet) assert.ok(queue.some(i=>nodes[i][3] !== null && nodes[i][3]-nodes[i][2] >= .29));
    const folder = path.join(dest,slug);
    mkdirSync(folder,{recursive:true});
    const graph = {cell:nav.cell, runSpeed, label:pickupThemes[slug].label.toLowerCase(), nodes, pickups:chosen};
    writeFileSync(path.join(folder,'navigation.json'),JSON.stringify(graph));
    writePickup(slug, slug === 'tung-tung-tung' ? path.join(dest,'..','tung-wood.glb') : path.join(folder,'pickup.glb'));
    if (slug === 'secret-capybara-toilet') await writeKoi(nav, folder);
    console.log(slug,nodes.length,'connected surfaces, 30 reachable pickups, swimming:',wet);
}
