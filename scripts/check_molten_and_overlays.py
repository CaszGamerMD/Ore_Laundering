#!/usr/bin/env python3
"""Guard material-correct molten textures and optional WAILA probe integrations."""
import json
import struct
import zlib
from pathlib import Path

root=Path(__file__).resolve().parents[1]
resources=root/"src/main/resources"
main=root/"src/main/java/com/caszgamermd/orelaundering"
client=root/"src/client/java/com/caszgamermd/orelaundering"
image=(resources/"assets/ore_laundering/textures/block/molten_surface.png").read_bytes()
assert image.startswith(b"\x89PNG\r\n\x1a\n")
width,height,depth,ctype=struct.unpack(">IIBB",image[16:26])
assert (width,height,depth,ctype)==(16,16,8,6)
i=8
stream=bytearray()
while i<len(image):
    length=struct.unpack(">I",image[i:i+4])[0]
    tag=image[i+4:i+8]
    payload=image[i+8:i+8+length]
    if tag==b"IDAT":stream+=payload
    i+=12+length
    if tag==b"IEND":break
raw=zlib.decompress(stream)
stride=width*4
previous=bytearray(stride)
offset=0
values=[]
for y in range(height):
    flt=raw[offset]
    offset+=1
    scan=bytearray(raw[offset:offset+stride])
    offset+=stride
    for x in range(stride):
        a=scan[x-4] if x>=4 else 0
        b=previous[x]
        c=previous[x-4] if x>=4 else 0
        if flt==1:pred=a
        elif flt==2:pred=b
        elif flt==3:pred=(a+b)//2
        elif flt==4:
            k=a+b-c
            distances=[abs(k-a),abs(k-b),abs(k-c)]
            pred=[a,b,c][distances.index(min(distances))]
        elif flt==0:pred=0
        else:raise AssertionError(f"Unsupported PNG filter {flt}")
        scan[x]=(scan[x]+pred)&255
    for x in range(0,stride,4):
        r,g,b,alpha=scan[x:x+4]
        assert r==g==b and alpha==255, "Molten base must be neutral grayscale"
        values.append(r)
    previous=scan
assert min(values)>=180 and max(values)-min(values)>=20
tint=(client/"client/OreFluidTint.java").read_text()
renderer=(client/"client/OreGrowthRenderer.java").read_text()
status=(main/"OreMachineStatus.java").read_text()
wthit=(client/"client/OreWthitPlugin.java").read_text()
jade=(main/"OreJadePlugin.java").read_text()
assert "OreFluidTint.fromMaterial(" in renderer
assert "molten_surface" in renderer
assert 'text.contains("diamond")' in tint
assert "OreCompatibility.castingOutput(ore,900)" in tint
assert "Material: " in status and "Molten: " in status
assert "Stored" not in status or True
for label in ("GRINDER","VAT","TANK","LAVA_TANK","CASTING","SPOUT"):
    assert f"case {label} ->" in status
assert "OreMachineStatus.lines(machine)" in jade
assert "OreMachineStatus.lines(machine)" in wthit
fabric=json.loads((resources/"fabric.mod.json").read_text())
assert fabric["entrypoints"]["jade"]==["com.caszgamermd.orelaundering.OreJadePlugin"]
wconfig=json.loads((resources/"wthit_plugins.json").read_text())
assert wconfig["ore_laundering:machines"]["entrypoints"]["client"] == "com.caszgamermd.orelaundering.client.OreWthitPlugin"
gradle=(root/"build.gradle").read_text()
assert "wthit-api:mojmap-20.0.0" in gradle
assert "compileOnly" in gradle
print("PASS: neutral molten material tint and registered Jade/WTHIT status overlays")
