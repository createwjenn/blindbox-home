"""Make the wearable GLBs and their transparent inventory portraits with Blender's bpy module.

Run with Blender Python or a Python environment containing bpy:
    python accessory_atelier.py [item_id ...]
Coordinates in the pattern definitions are glTF X/right, Y/up, Z/front.
"""
import math
import re
import sys
from pathlib import Path

import bpy
from mathutils import Matrix, Vector

MAIN = Path(__file__).resolve().parents[1] / 'app/src/main'
MODELS = MAIN / 'assets/accessories'
ICONS = MAIN / 'res/drawable-nodpi'
CATALOG = MAIN / 'java/com/jenn/blindboxworld/content/Accessories.kt'
TAU = math.tau
cos, sin = math.cos, math.sin

cream, gold, ink = 'fff2cf', 'eab047', '332938'
coral, pink, rose = 'f05d67', 'ffb1c1', 'c3325d'
mint, leaf, pine = '9be4bd', '69ae65', '316a54'
blue, lilac, cocoa = '529aff', 'bb91e5', '653b36'


def xyz(p):
    return Vector((p[0], -p[2], p[1]))


def material(color):
    mat = bpy.data.materials.get(color)
    if mat:
        return mat
    mat = bpy.data.materials.new(color)
    mat.use_nodes = True
    bsdf = mat.node_tree.nodes.get('Principled BSDF')
    srgb = [int(color[k:k + 2], 16) / 255 for k in (0, 2, 4)]
    linear = tuple(((v + .055) / 1.055) ** 2.4 if v > .04045 else v / 12.92 for v in srgb) + (1,)
    bsdf.inputs['Base Color'].default_value = linear
    bsdf.inputs['Roughness'].default_value = .31 if color in (gold, ink, '35495f') else .43
    bsdf.inputs['Metallic'].default_value = .33 if color == gold else .025
    # The collection room uses dim ambient illumination.
    bsdf.inputs['Emission Color'].default_value = linear
    bsdf.inputs['Emission Strength'].default_value = .18
    return mat


def finish(obj, color):
    obj.data.materials.append(material(color))
    return obj


def ball(p, size, color, angle=0):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=16, ring_count=8, location=xyz(p))
    obj = bpy.context.object
    obj.scale = (size[0], size[2], size[1])
    obj.rotation_euler.y = -angle
    for poly in obj.data.polygons:
        poly.use_smooth = True
    return finish(obj, color)


def stroke(points, color, width, radii=None, loop=False, smooth=True):
    data = bpy.data.curves.new('Piping', 'CURVE')
    data.dimensions = '3D'
    data.resolution_u = 4
    data.bevel_depth = width
    data.bevel_resolution = 1
    data.use_fill_caps = True
    if smooth:
        spline = data.splines.new('BEZIER')
        spline.bezier_points.add(len(points) - 1)
        for i, bp in enumerate(spline.bezier_points):
            bp.co = xyz(points[i])
            bp.handle_left_type = bp.handle_right_type = 'AUTO'
            if radii:
                bp.radius = radii[i]
    else:
        spline = data.splines.new('POLY')
        spline.points.add(len(points) - 1)
        for i, pt in enumerate(spline.points):
            pt.co = (*xyz(points[i]), 1)
            if radii:
                pt.radius = radii[i]
    spline.use_cyclic_u = loop
    obj = bpy.data.objects.new('Piping', data)
    bpy.context.collection.objects.link(obj)
    return finish(obj, color)


def shape(outline, depth, color, center=(0, 0, 0), bevel=.018):
    """Extruded front-plane pattern with a rounded edge, supports concave cuts."""
    data = bpy.data.curves.new('Enamel cutout', 'CURVE')
    data.dimensions = '2D'
    data.fill_mode = 'BOTH'
    data.extrude = depth / 2
    data.bevel_depth = bevel
    data.bevel_resolution = 2
    spline = data.splines.new('POLY')
    spline.points.add(len(outline) - 1)
    for pt, (x, y) in zip(spline.points, outline):
        pt.co = (x, y, 0, 1)
    spline.use_cyclic_u = True
    obj = bpy.data.objects.new('Enamel cutout', data)
    bpy.context.collection.objects.link(obj)
    obj.rotation_euler.x = math.pi / 2
    obj.location = xyz(center)
    return finish(obj, color)


def turn(rings, color, depth=.86, ripple=0, offset=0, rodeo=False):
    """Revolve a closed hat or collar cross-section, with optional brim shaping."""
    pts, faces = [], []
    count = 48
    for i, (r, y) in enumerate(rings):
        for j in range(count):
            a = TAU * j / count
            wave = r * ripple * cos(6 * a)
            rise = .48 * r ** 2 * cos(2 * a) if rodeo else 0
            pts.append(xyz((r * cos(a) + offset * y, y + wave + rise, depth * r * sin(a))))
        if i:
            for j in range(count):
                k = (j + 1) % count
                faces.append(((i - 1) * count + j, i * count + j, i * count + k, (i - 1) * count + k))
    mesh = bpy.data.meshes.new('Turned body')
    mesh.from_pydata(pts, [], faces)
    mesh.update()
    obj = bpy.data.objects.new('Turned body', mesh)
    bpy.context.collection.objects.link(obj)
    for poly in mesh.polygons:
        poly.use_smooth = True
    return finish(obj, color)


def ring(y, rx, rz, color, width=.025, z=0, tilt=0):
    return stroke([(rx*cos(a), y + tilt*sin(a), z + rz*sin(a)) for a in (TAU*j/20 for j in range(20))],
                  color, width, loop=True)


def oval(rx, ry, steps=36):
    return [(rx*cos(i*TAU/steps), ry*sin(i*TAU/steps)) for i in range(steps)]


def star(r, inner, n=5):
    return [((r if j%2 == 0 else inner)*cos(j*math.pi/n+math.pi/2),
             (r if j%2 == 0 else inner)*sin(j*math.pi/n+math.pi/2)) for j in range(2*n)]


def flower(p, r, petals=6, color=cream, center=gold):
    x, y, z = p
    for j in range(petals):
        a = TAU*j/petals
        ball((x + .58*r*cos(a), y + .58*r*sin(a), z), (.50*r, .28*r, .16*r), color, a)
    ball((x, y, z+.018), (.30*r, .30*r, .20*r), center)


def lenses(outline, frame, glass, radius=.026, spread=.25, gap=.045, funky=False):
    """One glass pattern per eye, with a backwards temple on each outside edge."""
    for side in (-1, 1):
        pts = [(side*(spread+x), y) for x, y in outline]
        shape(pts, .018, glass, (0,0,gap), bevel=.005)
        stroke([(x, y, gap+.014) for x, y in pts], frame, radius, loop=True, smooth=False)
        x = max(a for a,b in outline) + spread
        stroke([(side*x,.035,gap),(side*(x+.012),.042,-.09),(side*(x-.045),.012,-.31),
                (side*(x-.06),-.06,-.39)],frame,radius*.6)
        ball((side*x,.035,gap+.020),(.018,.018,.012),gold)
        if not funky:
            stroke([(side*(spread-.07),.063,gap+.026),(side*(spread-.025),.092,gap+.026)],'b6e4e5',.009)
    stroke([(-.085,.025,gap),(0,.060,gap+.018),(.085,.025,gap)],frame,radius*.87)


def headband(color, size=.04):
    return stroke([(.43*cos(a), -.22+.31*sin(a), -.04) for a in (math.pi*j/14 for j in range(15))],
                  color,size)


def neckcord(color, width=.024, drop=.20):
    points = [(.42*cos(a), -.04 - drop*max(sin(a),0), .32*sin(a)) for a in (TAU*j/28 for j in range(28))]
    stroke(points,color,width,loop=True)


def daisy_bucket():
    turn([(.30,.005),(.33,.07),(.33,.30),(.30,.34),(.24,.35),(.0001,.35)],'e7bc64')
    turn([(.305,.09),(.36,.088),(.494,-.025),(.50,-.046),(.48,-.056),(.318,.033)],'f0c773',ripple=.017)
    turn([(.335,.095),(.335,.156)],'f18f7a')
    for r in (.393,.445,.486):
        ring(.06-(r-.37)*.91,r,r*.86,'c8924b',.007)
    flower((.11,.133,.285),.104,7)


def cocoa_curl():
    for s in (-1,1):
        pts=[(0,.014,.028),(s*.113,.048,.053),(s*.22,-.038,.07),
             (s*.354,-.046,.047),(s*.446,.045,.012),(s*.450,.125,.004),(s*.396,.151,.005)]
        stroke(pts,'563133',.069,[.60,1.31,1.04,.70,.40,.23,.03])
        stroke([(s*.042,.041,.091),(s*.115,.065,.139),(s*.229,-.006,.139),(s*.316,-.032,.111)],
               'a56547',.008,[.2,.95,.70,.04])
        stroke([(s*.08,.006,.12),(s*.145,.002,.141),(s*.235,-.05,.141)],'774535',.006,[.25,1,.05])


def melon_shades():
    lenses([(-.181,.090),(-.159,.126),(.163,.126),(.196,.103),(.152,-.087),(.118,-.118),
            (-.091,-.124),(-.147,-.096)],'6aca99','35495f',.033,spread=.235)
    for s in (-1,1):
        stroke([(s*.315,.139,.078),(s*.390,.137,.078)],'f89d93',.015)


def berry_beret():
    turn([(.29,0),(.36,.035),(.47,.12),(.478,.20),(.414,.27),(.28,.321),(.0001,.36)],'da435d',offset=-.15)
    turn([(.301,-.01),(.32,.022),(.331,.062)],'a9314e')
    for j in range(5):
        a=TAU*j/5
        ball((-.04+.062*cos(a),.356,.05*sin(a)),(.091,.032,.041),leaf, .08*cos(a))
    stroke([(-.049,.34,0),(-.040,.425,0),(-.006,.454,-.012)],pine,.019,[1.1,.82,.58])
    for j in range(14):
        a=TAU*j/14
        ball((.475*cos(a)-.028,.186+.022*cos(a*3),.410*sin(a)),(.014,.027,.015),'ffdd9d',.22*cos(a))


def moon_monocle():
    x=.12
    shape(oval(.19,.19),.026,'a191c6',(x,0,.04))
    stroke([(x+a,b,.077) for a,b in oval(.196,.196)],gold,.022,loop=True,smooth=False)
    stroke([(x-.10,.03,.070),(x-.048,.098,.070),(x+.005,.126,.070)],cream,.010)
    for j in range(9):
        a=j/8
        px=x+.15-.17*a+.068*sin(a*math.pi)
        py=-.14-.30*a
        stroke([(px+u,py+v,.052) for u,v in oval(.017 if j%2 else .010,.027,16)],gold,.006,loop=True,smooth=False)
    shape(star(.062,.029),.018,cream,(x-.025,-.490,.054),.006)


def sardine_scarf():
    ring(-.009,.395,.30,'68b9bf',.084,tilt=-.055)
    for s in (-1,1):
        outline=[(-.05,.04),(.045,.02),(.08,-.08),(.075,-.17),(.044,-.22),(.08,-.29),
                 (0,-.264),(-.062,-.28),(-.039,-.218),(-.069,-.17),(-.087,-.08)]
        shape([(s*(x+(.25 if s==1 else .01)*-y),y) for x,y in outline],.05,
              '5aa7b3' if s==1 else '92d8cc',(s*.08,-.07,.33 if s==1 else .31),.018)
        ball((s*.079,-.129,.376),(.014,.014,.010),ink)
        for j in range(2):
            stroke([(s*(.037+i*.04),-.171-j*.045-.012*sin(i*1.5),.38) for i in range(3)],cream,.008)
    ball((0,-.075,.32),(.085,.07,.06),'3a8290')


def frog_phones():
    headband(leaf)
    for s in (-1,1):
        ball((s*.40,-.17,-.015),(.113,.151,.132),'41795e')
        ball((s*.424,-.134,.05),(.102,.127,.109),'92ce7b')
        for offset in [-.043,.043]:
            ball((s*.421+offset,-.024,.107),(.041,.046,.044),'a2d789')
            ball((s*.421+offset,-.019,.144),(.017,.025,.012),ink)
        stroke([(s*.421-.038,-.111,.159),(s*.421,-.126,.162),(s*.421+.038,-.111,.159)],'47764d',.008)


def cherry_bow():
    for s in (-1,1):
        ball((s*.234,.15,.035),(.245,.157,.069),'e35065',s*.29)
        outline=[(0,.17),(.06,.14),(.26,-.037),(.16,-.030),(.17,-.13),(.025,-.015),(-.05,.065)]
        shape([(s*x,y) for x,y in outline],.028,'b93758',(0,0,-.01))
        stroke([(s*.065,.124,.097),(s*.166,.15,.105),(s*.27,.204,.102)],'ffad9c',.012,[.1,1,.16])
        stroke([(s*.073,.122,.108),(s*.18,.087,.103),(s*.264,.081,.10)],'ab2f50',.009,[.15,1,.10])
    ball((0,.126,.076),(.078,.096,.077),rose)


def pixel_specs():
    poly=[(-.18,.098),(.17,.098),(.17,-.011),(.13,-.011),(.13,-.07),(.08,-.07),(.08,-.11),(-.10,-.11),
          (-.10,-.076),(-.14,-.076),(-.14,-.03),(-.18,-.03)]
    lenses(poly,ink,'3c395e',.018,spread=.199,funky=True)
    for s in (-1,1):
        for j in range(3):
            x=s*.199+.04*j-.067; y=.04-.031*j
            shape([(-.020,-.015),(.020,-.015),(.020,.015),(-.020,.015)],.008,'adbaff',(x,y,.077),.001)


def peach_bubble():
    ball((-.115,-.023,.035),(.15,.077,.073),'d87980')
    ball((.070,.045,.182),(.22,.210,.20),'f390b4')
    ball((.002,.119,.357),(.048,.015,.014),'ffe1df',.63)
    ball((-.030,.086,.360),(.012,.010,.009),cream)


def ramen_crown():
    turn([(.236,.004),(.31,.052),(.411,.219),(.426,.265),(.411,.285),(.391,.271)],'c74c4e')
    ring(.242,.418,.360,cream,.014)
    ring(.07,.324,.281,'efa46a',.008)
    ball((0,.252,0),(.399,.055,.333),'ae612e')
    for j in range(5):
        stroke([(-.27+i*.050,.30+.006*sin(i+j),-.09+j*.044+.023*sin(i*1.2+j)) for i in range(12)],
               'efc569',.011)
    ball((-.117,.323,-.013),(.138,.033,.105),cream)
    ball((-.108,.351,-.005),(.061,.02,.049),'eeab40')
    for j in range(2):
        stroke([(.091+j*.069,.302,-.100),(.308+j*.099,.590,-.21)],cocoa,.013,[.66,1])
    for j in range(4):
        a=j*1.7
        ball((.12+.062*cos(a),.307,.08+.038*sin(a)),(.033,.013,.019),leaf)


def shell_ruff():
    ring(0,.333,.263,'d8abc5',.034)
    for j in range(16):
        a=TAU*j/16
        ball((.326*cos(a),-.025,.274*sin(a)),(.096,.090,.075),'f4cbd7' if j%2 else 'fff1dc',.15*cos(a))
    for j in range(13):
        a=math.pi*j/12
        ball((.365*cos(a),-.115,.303*sin(a)),(.029,.029,.027),'fff5e8')
    shape(star(.067,.036,5),.032,gold,(0,-.153,.311),.009)


def butter_toast():
    edge=[(-.23,.14),(-.254,.170),(-.247,.212),(-.20,.247),(-.11,.260),(.075,.258),(.082,.216),
          (.13,.211),(.135,.174),(.188,.174),(.191,.14),(.23,.141),(.227,-.20),(.20,-.235),(-.20,-.238),(-.229,-.212)]
    shape(edge,.075,'bd7443',(.04,-.036,.065),.020)
    shape([(x*.89,y*.88) for x,y in edge],.010,'f4cf80',(.04,-.036,.123),.014)
    shape([(-.060,-.042),(.033,-.067),(.074,.032),(-.029,.060)],.016,'f5b72e',(.054,-.017,.146),.015)
    for x,y in [(-.12,.14),(-.16,-.09),(.15,-.14),(.13,.032),(-.075,-.18)]:
        ball((x+.04,y-.036,.143),(.008,.012,.005),'d7a357')


def cloud_visor():
    outline=[(-.435,-.025),(-.431,.074),(-.398,.104),(-.358,.117),(-.325,.162),(-.262,.184),
             (-.201,.159),(-.14,.108),(-.079,.128),(-.032,.125),(.035,.157),(.113,.163),
             (.183,.119),(.233,.149),(.30,.153),(.36,.106),(.41,.092),(.441,.049),(.424,-.053),
             (.373,-.084),(.19,-.119),(.095,-.112),(.061,-.067),(-.047,-.066),(-.095,-.116),(-.21,-.128),(-.372,-.085)]
    shape(outline,.028,'77bbdf',(0,0,.051),.018)
    stroke([(x,y,.076) for x,y in outline], 'e8f2ee',.012,loop=True,smooth=False)
    for s in (-1,1):
        stroke([(s*.424,.045,.042),(s*.435,.027,-.16),(s*.38,-.04,-.336)],'83a8dc',.019)
    stroke([(-.30,-.057,.089),(-.20,.074,.089)],'cff6ed',.019,[.65,1])
    stroke([(-.238,-.069,.089),(-.172,.021,.089)],'b5e7ec',.009)


def matcha_sprout():
    ring(.009,.36,.265,'679b6f',.027)
    stroke([(0,.02,0),(-.006,.16,0),(.054,.292,0)],pine,.021,[1.4,1,.6])
    for s in (-1,1):
        y=.22+.049*s
        ball((s*.127,y,.006),(.160,.067,.025),leaf if s==1 else mint,s*.46)
        stroke([(s*.031,y-.044,.025),(s*.119,y+.001,.035),(s*.217,y+.042,.021)],pine,.007,[.85,1,.05])


def honey_tie():
    neckcord('825536',.016,.032)
    for s in (-1,1):
        ball((s*.218,-.069,.34),(.182,.100,.041),'f2bc45',s*.29)
        stroke([(s*.218,-.14,.370),(s*.235,-.072,.39),(s*.264,.032,.360)],cocoa,.021)
        ball((s*.080,.027,.36),(.092,.040,.022),'c3e8e0',s*1.00)
    ball((0,-.074,.374),(.07,.073,.065),'62423a')
    for s in (-1,1):
        stroke([(s*.025,-.032,.358),(s*.050,.043,.345),(s*.068,.060,.35)],cocoa,.008)


def comet_band():
    headband('7a65a7',.025)
    for s in (-1,1):
        x=.28*s
        stroke([(x,-.002,0),(x+.017*s,.095,0),(x+.075*s,.171,0),(x+.082*s,.257,.012)],gold,.013)
        shape(star(.11 if s==1 else .080,.036,4),.036,'b6a6ec' if s==1 else 'f9c77c',
              (x+.082*s,.28,.016),.012)
        ball((x+.044*s,.177,.024),(.025,.025,.025),cream)


def love_letters():
    pts=[]
    for j in range(40):
        a=j*TAU/40
        pts.append((.194*sin(a)**3, .011*(13*cos(a)-5*cos(2*a)-2*cos(3*a)-cos(4*a))))
    lenses(pts,'e35e82','a84170',.022,spread=.24,funky=True)
    for s in (-1,1):
        ball((s*.29,.058,.072),(.030,.012,.008),'ffb9c3',.58)


def leaf_whistle():
    stroke([(-.36,-.08,.02),(-.23,-.05,.042),(-.035,-.004,.06)],'827742',.018,[.54,.75,1])
    outline=[(-.22,-.034),(-.107,.10),(.057,.153),(.253,.128),(.371,.15),(.292,.014),(.175,-.08),(.012,-.117),(-.131,-.09)]
    shape(outline,.028,'64af70',(.002,0,.055),.012)
    stroke([(-.219,-.031,.088),(-.047,-.007,.09),(.156,.049,.09),(.345,.138,.083)],'b4db83',.010,[.89,1,.72,.04])
    for x in (-.071,.058,.16):
        y=.24*x+.008
        stroke([(x,y,.088),(x+.018,y+.043,.088),(x+.052,y+.085,.085)],'b4db83',.006,[1,.7,.05])
        stroke([(x,y,.088),(x+.047,y-.045,.088),(x+.082,y-.059,.085)],'b4db83',.006,[1,.7,.05])


def koi_kerchief():
    ring(0,.396,.282,'ed8359',.048)
    edge=[(-.328,.05),(-.21,.026),(0,0),(.20,.019),(.32,.064),(.248,-.092),(.051,-.309),
          (-.004,-.335),(-.072,-.304),(-.244,-.124)]
    shape(edge,.022,'f28955',(0,-.021,.27),.014)
    shape([(-.25,.017),(-.17,-.011),(-.07,-.027),(.03,-.037),(-.065,-.09),(-.133,-.094),(-.199,-.064)],
          .006,'fff4db',(0,-.021,.300),.009)
    for i in range(3):
        x=.06*i-.052
        stroke([(x-.033,-.161,.302),(x,-.185,.304),(x+.03,-.165,.302)],'c7563e',.007)
    ball((.084,-.09,.301),(.036,.022,.008),ink)
    stroke([(-.05,-.304,.300),(0,-.342,.30),(.053,-.298,.300)],cream,.009)


def blue_bonnet():
    turn([(.28,.008),(.386,.038),(.425,.125),(.397,.261),(.321,.340),(.195,.396),(.0001,.415)],'7575b9')
    turn([(.309,.04),(.433,.019),(.465,.0),(.458,-.03),(.420,-.038)],'acabe1',ripple=.042)
    for j in range(6):
        a=TAU*j/6
        ball((.046*cos(a),.418,.041*sin(a)),(.071,.018,.040),'455b90',.16*cos(a))
    for s in (-1,1):
        stroke([(s*.329,.008,.088),(s*.344,-.124,.094),(s*.278,-.212,.129),(s*.246,-.257,.147)],
               'c7b7ed',.015,[1,.85,.7,.32])
    ring(.052,.393,.341,'8e89cb',.012)


def pocket_camera():
    neckcord('588482',.020,.204)
    def rect(w,h):
        return [(-w,-h),(w,-h),(w,h),(-w,h)]
    shape(rect(.178,.109),.080,'e7b288',(0,-.262,.309),.019)
    shape(rect(.177,.055),.009,'7bb3a8',(0,-.269,.366),.007)
    shape(oval(.069,.069),.037,ink,(.010,-.27,.381),.006)
    shape(oval(.051,.051),.019,'55869e',(.010,-.27,.411),.003)
    ball((-.010,-.24,.429),(.016,.010,.005),'bce4e3',.5)
    shape(rect(.031,.015),.010,cream,(.119,-.197,.365),.003)
    shape(rect(.028,.011),.018,coral,(-.102,-.14,.326),.004)


def croissant_band():
    headband('b08155',.026)
    for s in (-1,1):
        stroke([(s*.272,.007,.0),(s*.367,.115,.017),(s*.360,.227,.022),
                (s*.272,.292,0),(s*.191,.277,-.018),(s*.151,.227,-.015)],
               'c2874e',.089,[1.0,1.15,1.05,.76,.40,.036])
        for x,y,rx,ry,ang in [( .30,.048,.079,.107,-.36),(.37,.139,.092,.094,.14),
                               (.35,.220,.076,.084,.51),(.288,.269,.050,.069,.96)]:
            ball((s*x,y,.017),(rx,ry,.082 if y<.23 else .060),'e6ae63',s*ang)


def soda_swirl():
    stroke([(-.331,-.201,.033),(-.045,-.071,.049),(.154,.059,.057)],cream,.019)
    ball((.191,.101,.076),(.178,.174,.046),'ef7280')
    pts=[]
    for j in range(87):
        a=j/86*TAU*1.72
        r=.005+.146*j/86
        pts.append((.19+r*cos(a),.10+r*sin(a),.125-.025*(r/.17)**2))
    stroke(pts,'fff2ca',.015,smooth=False)
    for s in (-1,1):
        ball((.019+s*.027,-.018+.016*s,.067),(.034,.014,.010),mint,s*1.1)


def disco_stars():
    lenses(star(.21,.101),gold,'874f96',.017,spread=.226,funky=True)
    for s in (-1,1):
        shape(star(.033,.010,4),.005,'ffccdc',(s*.22-.038,.031,.074),.002)


def milk_charm():
    neckcord('cd9e99',.011,.204)
    for j in range(7):
        a=.28+2.55*j/6
        ball((.42*cos(a),-.04-.204*sin(a),.32*sin(a)),(.018,.018,.018),pink if j%2 else cream)
    outline=[(-.09,-.11),(.09,-.11),(.09,.065),(.047,.128),(-.063,.128),(-.09,.066)]
    shape(outline,.107,'ffe4dc',(0,-.28,.313),.008)
    shape([(-.089,-.107),(.089,-.107),(.089,-.054),(-.089,-.054)],.006,'de7692',(0,-.28,.378),.002)
    shape([(-.086,.071),(.087,.071),(.046,.127),(-.062,.127)],.009,'f8a6b5',(0,-.28,.377),.002)
    shape([(-.037,.013),(-.029,.030),(-.001,.037),(.031,.024),(.038,.008),(.027,-.013),(0,-.037),(-.023,-.023)],
          .008,'d85270',(0,-.283,.38),.004)
    for s in (-1,1):
        ball((s*.012,-.25,.393),(.017,.008,.006),leaf,s*.48)


def lilac_rodeo():
    turn([(.297,.034),(.305,.143),(.285,.239),(.257,.281),(.197,.294),(.120,.230),(.0001,.225)],'aaa0df',depth=.93)
    turn([(.298,.085),(.353,.071),(.425,.081),(.486,.112),(.497,.113),(.495,.088),(.455,.065),(.329,.038)],
         'c7a9e0',depth=.78,rodeo=True)
    turn([(.300,.100),(.300,.14)],'6b536d',depth=.94)
    shape([(-.054,0),(0,.051),(.054,0),(0,-.051)],.020,'65beb0',(0,.123,.282),.012)


def daisy_stem():
    stroke([(-.359,-.121,.023),(-.213,-.09,.045),(.026,.001,.058),(.216,.099,.055)],pine,.013,[.55,.88,1,.8])
    ball((-.050,.009,.058),(.074,.022,.009),leaf,.72)
    flower((.229,.118,.077),.130,9,'fff1c3','f1b548')
    for dx,dy in [(-.014,0),(.011,.009),(.008,-.011)]:
        ball((.229+dx,.118+dy,.125),(.005,.005,.004),'cd842c')


def rainbow_chain():
    neckcord('caa3a3',.010,.233)
    for j in range(23):
        a=TAU*j/23
        ball((.42*cos(a),-.04-.233*max(sin(a),0),.32*sin(a)),(.025,.025,.024),
             [cream,'e6a5b1','a4cdbc','ebcb7a','bcaadf'][j%5])
    for j,c in enumerate(['eb808b','f2be68','87cbb6']):
        r=.112-j*.019
        stroke([(r*cos(a),-.319+r*sin(a),.323) for a in (math.pi*i/19 for i in range(20))],c,.012)
    for s in (-1,1):
        for j in range(3):
            ball((s*.096+(j-1)*.022,-.320+.010*(j%2),.34),(.026,.022,.014),cream)


def plum_fangs():
    edge=[(-.250,.044),(-.200,.067),(-.126,.097),(-.054,.071),(0,.048),(.06,.071),(.136,.102),
          (.201,.067),(.251,.037),(.173,-.018),(.101,-.057),(0,-.079),(-.090,-.065),(-.178,-.022)]
    shape(edge,.046,'933852',(0,0,.049),.016)
    stroke([(-.23,.033,.086),(-.13,.014,.089),(-.007,.003,.088),(.137,.014,.09),(.233,.036,.084)],'652b47',.010)
    for s in (-1,1):
        shape([(-.038,.025),(.035,.028),(.030,-.039),(0,-.105),(-.022,-.045)],.026,'fff3ce',
              (s*.145,.005,.092),.007)


def studio(model, slot):
    scene = bpy.context.scene
    scene.render.engine = 'CYCLES'
    scene.cycles.device = 'CPU'
    scene.cycles.samples = 16
    scene.cycles.use_denoising = True
    scene.render.resolution_x = scene.render.resolution_y = 256
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = 'PNG'
    scene.render.image_settings.color_mode = 'RGBA'
    scene.render.film_transparent = True
    scene.render.threads_mode = 'FIXED'
    scene.render.threads = 5
    scene.world = bpy.data.worlds.new('Soft cream studio')
    scene.world.use_nodes = True
    scene.world.node_tree.nodes['Background'].inputs[0].default_value = (.90,.86,.81,1)
    scene.world.node_tree.nodes['Background'].inputs[1].default_value = .37
    scene.view_settings.view_transform = 'AgX'
    scene.view_settings.look = 'AgX - Medium High Contrast'

    def lamp(loc, energy, size, color):
        data = bpy.data.lights.new('Softbox','AREA')
        data.energy, data.shape, data.size, data.color = energy, 'DISK', size, color
        ob = bpy.data.objects.new('Softbox',data)
        scene.collection.objects.link(ob)
        ob.location = xyz(loc)
        ob.rotation_euler = (-ob.location).to_track_quat('-Z','Y').to_euler()

    lamp((-.9,1.5,1.3),62,1.35,(1,.92,.83))
    lamp((.95,.6,.9),28,.90,(.83,.89,1))
    lamp((.5,1.3,-.8),54,1.1,(1,.87,.79))
    cam_data = bpy.data.cameras.new('Portrait lens')
    cam = bpy.data.objects.new('Portrait lens',cam_data)
    scene.collection.objects.link(cam)
    scene.camera = cam
    cam_data.type = 'ORTHO'
    direction = xyz((.23 if slot=='HEAD' else .14, .31 if slot in ('HEAD','NECK') else .11, 1)).normalized()
    cam.rotation_euler = (-direction).to_track_quat('-Z','Y').to_euler()
    rotate = cam.rotation_euler.to_matrix().inverted()
    pts = [rotate @ v.co for v in model.data.vertices]
    lo = Vector(tuple(min(p[i] for p in pts) for i in range(3)))
    hi = Vector(tuple(max(p[i] for p in pts) for i in range(3)))
    target = rotate.inverted() @ ((lo + hi) / 2)
    cam.location = target + 3*direction
    cam_data.ortho_scale = max(hi.x-lo.x,hi.y-lo.y)*1.105


def build(ident, name, slot):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    globals()[ident]()
    bpy.ops.object.select_all(action='SELECT')
    bpy.context.view_layer.objects.active = bpy.context.selected_objects[0]
    bpy.ops.object.convert(target='MESH')
    if len(bpy.context.selected_objects)>1:
        bpy.ops.object.join()
    obj=bpy.context.object
    original=obj.matrix_world.copy()
    width=max((original @ v.co).x for v in obj.data.vertices)-min((original @ v.co).x for v in obj.data.vertices)
    obj.data.transform(Matrix.Scale(1/width,4) @ original)
    obj.matrix_world=Matrix.Identity(4)
    obj.name=name
    # Match the runtime's front-facing convention without node transforms.
    bpy.ops.export_scene.gltf(filepath=str(MODELS/(ident+'.glb')),export_format='GLB',
                              export_yup=True, use_selection=True, export_texcoords=False,
                              export_tangents=False,export_cameras=False,export_lights=False)
    studio(obj,slot)
    bpy.context.scene.render.filepath=str(ICONS/('accessory_'+ident+'.png'))
    bpy.ops.render.render(write_still=True)


if __name__=='__main__':
    MODELS.mkdir(parents=True,exist_ok=True)
    ICONS.mkdir(parents=True,exist_ok=True)
    catalog=re.findall(r'Accessory\("([^"]+)", "([^"]+)", AccessorySlot\.(\w+)',CATALOG.read_text())
    requested = sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else sys.argv[1:]
    for ident,name,slot in catalog:
        if not requested or ident in requested:
            build(ident,name,slot)
