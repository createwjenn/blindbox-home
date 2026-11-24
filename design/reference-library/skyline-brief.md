# San Francisco exterior

Replace the generic city geometry with a Rincon Hill-inspired panoramic composition, retaining window mullions and room geometry. Include Salesforce Tower with rounded taper and crown, Transamerica Pyramid, the Bay Bridge western suspension spans and Yerba Buena Island, and visible bay water. Use separate directions around the panorama rather than forcing geographically separated landmarks into a narrow view. This is an artistic Rincon Hill-inspired view, not a surveyed view from a specific address.

Preserve identical geometry and horizon for sunrise/day/sunset/night and their cloudy variants. Sunrise: eastern warm light, cool western towers. Day: marine blue water, pale atmospheric hills. Sunset: western warm reflections and cooler bay. Night: varied office windows, bridge roadway and cable lights with subdued water reflections.

Existing integration: design/collection_room.py owns sky planes and generic city elevations; design/room_looks.py builds eight .look variants referencing the collection-room GLB binary. Both must be regenerated together if geometry changes. Avoid painting building imagery into the sky while retaining the generic foreground facades. Verify west and corner window banks at all four times.

Geographic reference: https://sfocii.org/sites/default/files/inline-files/Transbay%20Redevelopment%20Project%20Area%20Design%20for%20Development%20-%20Full.pdf

Status: implemented in design/sf_skyline.py and integrated into collection-room.glb. All eight .look variants were regenerated. The base loft lighting and furniture geometry are preserved. Retired generic skyline mesh references are removed, including orphan nodes.

Verification: all variants have identical SF geometry and valid buffer bounds. Four window-view renders are in sf-window-{sunrise,day,sunset,night}.png. This is a modeled, Rincon Hill-inspired panorama rather than a photographic or surveyed view.
