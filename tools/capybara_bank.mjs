// The garden website fills its whole hand-drawn pond polygon with a constant
// swim bed. Survey the full-detail scan where that polygon meets mud and rock.
import { readFileSync } from 'node:fs';
import { gunzipSync } from 'node:zlib';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';

export function gardenNavigation(folder) {
    const nav = JSON.parse(readFileSync(join(folder, 'navigation-playable.json')));
    const scan = gunzipSync(readFileSync(join(folder, 'ceramic.spz')));
    if (scan.readUInt32LE(4) !== 2) throw new Error('Expected an SPZ v2 garden scan');
    const pointCount = scan.readUInt32LE(8);
    const scale = 2 ** scan[13];
    const { cell, waterLevel: water } = nav;
    const columns = new Map(Object.keys(nav.ground).map(key => [key, new Uint32Array(102)]));
    // Restrict the height survey to the floor and the player's body-height band.
    // Tree canopy and underwater splat outliers do not define a bank.
    const base = water - .35;
    const alphaOffset = 16 + pointCount * 9;
    for (let i = 0; i < pointCount; i++) {
        if (scan[alphaOffset + i] < 65) continue;
        const offset = 16 + 9 * i;
        const y = -scan.readIntLE(offset + 3, 3) / scale;
        const bin = Math.floor((y - base) * 100);
        if (bin < 0 || bin >= 102) continue;
        const ix = Math.floor(scan.readIntLE(offset, 3) / (scale * cell));
        const iz = Math.floor(-scan.readIntLE(offset + 6, 3) / (scale * cell));
        const hist = columns.get(`${ix},${iz}`);
        if (hist) hist[bin]++;
    }
    const heights = new Map();
    for (const [key, hist] of columns) {
        // A local density peak selects a solid ground sheet rather than the
        // treetop, a vertical wall, or a few floating Gaussian centres.
        let best = 0;
        let mass = 0;
        const maxBin = Math.min(78, Math.ceil((Math.max(water, nav.ground[key]) + .25 - base) * 100));
        for (let i = 2; i < maxBin; i++) {
            const score = hist[i-2] + hist[i-1] + hist[i] + hist[i+1] + hist[i+2];
            if (score > mass) {
                best = i;
                mass = score;
            }
        }
        if (mass < 12) continue;
        let above = 0;
        for (let i = best + 15; i < Math.min(best + 53, hist.length); i++) above += hist[i];
        if (above > Math.max(40, mass * 1.6)) {
            nav.blocked[key] = true;
            continue;
        }
        const measured = Math.round((base + (best + .5) / 100) * 1000) / 1000;
        heights.set(key, measured);
        if (nav.waterCells[key] && measured >= water - .008) {
            delete nav.waterCells[key];
            nav.ground[key] = Math.max(water, measured);
        } else if (!nav.waterCells[key] && Math.abs(measured - nav.ground[key]) < .18) {
            nav.ground[key] = measured;
        }
    }
    // Grade the first two wet cells around each real bank. The scanner sees
    // reflections in open water, so retain the authored swimming depth there.
    const shore = Object.keys(nav.ground).filter(k => !nav.waterCells[k] && !nav.blocked[k]);
    const visited = new Set(shore);
    let front = shore;
    for (let distance = 1; distance <= 2; distance++) {
        const next = [];
        for (const key of front) {
            const [x, z] = key.split(',').map(Number);
            for (const [nx, nz] of [[x+1, z], [x-1, z], [x, z+1], [x, z-1]]) {
                const near = `${nx},${nz}`;
                if (visited.has(near) || !nav.waterCells[near] || nav.blocked[near]) continue;
                visited.add(near);
                next.push(near);
                const scanned = heights.get(near);
                // Deepen gradually from the scan's apparent surface to the bed.
                if (scanned !== undefined) {
                    const bed = Math.min(water - .015, scanned) - .065 * distance;
                    nav.ground[near] = Math.max(nav.ground[near], bed);
                }
            }
        }
        front = next;
    }
    return nav;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
    process.stdout.write(JSON.stringify(gardenNavigation(process.argv[2])));
}
