# Reference furniture production

104 independent GLBs and matching rendered thumbnails are registered in the room library. Existing purchases and asset IDs are preserved. Repeated photographs map to one item; distinguishable material variants have separate entries.

These locally authored models are reference-inspired approximations, not scanned or exact reproductions. Some chair and table silhouettes share construction families and need further refinement for photographic fidelity. Cropped unresolved references remain documented in inventory.json.

## Production
- Rebuild: tools/build_reference_library.py using Blender Python.
- Source images: sources/reference-01.png through reference-08.png.
- Inventory: inventory.json; generated dimensions: generated-0.json.
- Review: model-contact-sheet.jpg; geometry audit: model-audit.json.
- Each asset has its own mesh, thumbnail, dimensions and placement footprint. Geometry is capped at 24,000 triangles per asset.

## Verification
- All 104 assets and thumbnails exist and load as GLB/PNG.
- Android build and 53 unit tests passed after catalogue and footprint integration.
- Installed and viewed the reference collection in the PICO emulator. Library Next and Close were exercised with native mouse clicks.
- Menu callback navigation was checked through published accessibility controls. Native object-menu pointer behavior is still awaiting verification after the pointer tool began returning noWindowsAvailable.
- Independent purchase/place/move/store behavior has not been exercised for every new item.

## Remaining work
Further silhouette/material refinement and all-item placement review; San Francisco skyline is implemented; see skyline-brief.md and the four window-view light studies.
