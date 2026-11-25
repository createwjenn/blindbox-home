package com.jenn.blindboxworld.content

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

internal data class Furnishing(
    val id: String, val title: String, val width: Float, val height: Float, val depth: Float,
    val origin: RoomSpot? = null, val wall: Boolean = false, val tabletop: Boolean = false,
    val price: Int = 0, val thumbnail: Int = 0, val category: String = "", val baseYaw: Float = 0f,
) {
    val movable get() = id != "machine"
    val swappableArt get() = category in setOf("frame", "art") || id in setOf("ochre-canvas", "unboxing-print", "slop-print", "capybara-print")
    val blocksWalk get() = !wall && id != "wool-rug" && category != "rug"
    val asset get() = if (id.startsWith("art:")) "furniture/painting_${id.removePrefix("art:").replace('-', '_')}.glb"
                      else "furniture/$id.glb"
}

private fun built(id: String, title: String, x: Float, y: Float, z: Float, w: Float, h: Float, d: Float,
                  wall: Boolean = false, tabletop: Boolean = false, yaw: Float = 0f) =
    Furnishing(id,title,w,h,d,RoomSpot(x,y,z),wall,tabletop,baseYaw = yaw)

internal val loftPieces = listOf(
    built("stand","Display stand",0f,0f,-1.7f,.56f,1.21f,.56f),
    built("machine","Gumball machine",GACHA_X,0f,GACHA_Z,1.12f,1.83f,1.12f),
    built("record-player","Record player",-.55f,.905f,-4.12f,.46f,.40f,.37f,tabletop=true),
    built("console","Listening shelf",0f,0f,-4.12f,3f,.905f,.44f),
    built("vinyl-rack","Record rack",.65f,.905f,-4.12f,1.17f,.32f,.31f,tabletop=true),
    built("mushroom","Mushroom lamp",-1.18f,.905f,-4.12f,.21f,.38f,.21f,tabletop=true),
    built("speaker-left","Speaker with monstera",-1.95f,0f,-4.15f,.32f,1.18f,.38f),
    built("speaker-right","Walnut speaker",1.95f,0f,-4.15f,.32f,.64f,.38f),
    built("arc-lamp","Arc lamp",-2.75f,0f,-3.95f,.40f,1.99f,.40f),
    built("sofa","Channel sofa",2.14f,0f,-1.69f,3.06f,.83f,1.09f),
    built("wool-rug","Loop wool rug",2.06f,0f,-.67f,3.96f,.03f,2.76f),
    built("coffee-table","Glass coffee table",2.10f,0f,-.33f,1.57f,.50f,.69f),
    built("chair","Window lounge chair",-3.22f,0f,.58f,.71f,.90f,.82f),
    built("ottoman","Leather footstool",-3.09f,0f,1.54f,.49f,.33f,.42f),
    built("sideboard","Window sideboard",-3.84f,0f,-1.38f,.52f,.62f,2.35f),
    built("table-globe","Opaline table lamp",-3.68f,.62f,-1.53f,.32f,.35f,.32f,tabletop=true),
    built("sofa-lamp","Walnut floor lamp",3.64f,0f,-2.48f,.41f,1.16f,.41f),
    built("orb","Amber floor globe",3.66f,0f,1.72f,.49f,.54f,.49f),
    built("laurel-left","Window laurel",-3.72f,0f,2.63f,.38f,1.26f,.38f),
    built("laurel-right","Gallery laurel",3.72f,0f,-3.55f,.38f,1.39f,.38f),
    built("dining-table","Walnut dining table",1.16f,0f,3.27f,2.10f,1.16f,1f),
    built("chair-a","Dining chair",.61f,0f,2.61f,.45f,.83f,.44f),
    built("chair-b","Dining chair",1.71f,0f,2.61f,.45f,.83f,.44f),
    built("chair-c","Dining chair",.61f,0f,3.93f,.45f,.83f,.44f),
    built("chair-d","Dining chair",1.71f,0f,3.93f,.45f,.83f,.44f),
    built("ochre-canvas","Earth study in ochre",4.18f,1.88f,-1.48f,1.28f,1.53f,.08f,wall=true,yaw=-90f),
    built("unboxing-print","Unboxing print",-.62f,1.47f,-4.36f,.67f,.91f,.04f,wall=true),
    built("slop-print","Slop print",.92f,1.98f,-4.36f,.71f,.99f,.04f,wall=true),
    built("capybara-print","Capybara print",.10f,2.48f,-4.36f,.45f,.55f,.04f,wall=true),
    built("calendar","Wall calendar",2.25f,1.32f,-4.38f,.36f,.57f,.03f,wall=true),
    built("convex-mirror","Convex mirror",-1.72f,2.58f,-4.35f,.43f,.43f,.09f,wall=true)
)

internal data class DecorPose(val at: RoomSpot, val yaw: Float = 0f, val support: String = "") {
    fun encode() = "${at.encode()}|$yaw|$support"
    companion object {
        fun decode(value: String?): DecorPose? {
            val parts = value?.split('|') ?: return null
            val xyz = parts[0].split(',').map { it.toFloatOrNull() ?: return null }
            if (xyz.size != 3 || xyz.any { !it.isFinite() } || abs(xyz[0])>4.4f || xyz[1] !in 0f..4.24f || abs(xyz[2])>4.45f) return null
            val yaw = parts.getOrNull(1)?.toFloatOrNull()?.takeIf { it.isFinite() } ?: 0f
            return DecorPose(RoomSpot(xyz[0],xyz[1],xyz[2]),yaw,parts.getOrNull(2).orEmpty())
        }
    }
}
internal data class DecorTarget(val pose: DecorPose, val allowed: Boolean)

private fun furnitureCatalogue(context: Context): List<Furnishing> =
    context.assets.open("furniture/catalogue.json").bufferedReader().use { reader ->
        val rows = JSONArray(reader.readText())
        (0 until rows.length()).map { index ->
            val row = rows.getJSONArray(index)
            val id = row.getString(0)
            val kind = row.getString(2)
            Furnishing(id,row.getString(1),row.getDouble(5).toFloat(),row.getDouble(6).toFloat(),row.getDouble(7).toFloat(),
                wall=kind=="frame",tabletop=kind in listOf("lamp","vase","sculpt","plant"),price=row.getInt(4),
                thumbnail=context.resources.getIdentifier("room_$id","drawable",context.packageName),category=kind)
        }
    }
internal class RoomFurnishing(val catalogue: List<Furnishing>, private val prefs: SharedPreferences) {
    constructor(context: Context, prefs: SharedPreferences) : this(furnitureCatalogue(context), prefs)

    val artworks = FIGURE_IDS.map { Furnishing("art:$it", "${nameOf(it)} painting", .46f,.775f,.045f,
        wall=true, thumbnail=worldPaintingArt(it), category="art") }
    val pieces = (loftPieces + catalogue + artworks).associateBy { it.id }
    var purchased by mutableStateOf(prefs.getStringSet("roomFurniture",emptySet()).orEmpty().toSet())
        private set
    var saved by mutableStateOf(pieces.keys.mapNotNull { id -> DecorPose.decode(prefs.getString("decor.$id",null))?.let { id to it } }.toMap())
        private set
    var shopOpen by mutableStateOf(false)
        private set
    var selected by mutableStateOf<String?>(null)
    var carrying by mutableStateOf<String?>(null)
        private set
    var target by mutableStateOf<DecorTarget?>(null)
        private set
    private var storedOriginals by mutableStateOf(prefs.getStringSet("storedRoomArt", emptySet()).orEmpty().toSet())
    var swapSource by mutableStateOf<String?>(null)
        private set
    val storedArtwork get() = loftPieces.filter { it.id in storedOriginals }
    val placedIds get() = (loftPieces.map { it.id }.filterNot { it in storedOriginals } + saved.keys).distinct()
    fun openArtSwap(id: String) {
        if (carrying != null || id !in placedIds || pieces[id]?.swappableArt != true) return
        swapSource = id
        selected = null
        shopOpen = true
    }
    fun swapArt(replacement: String): Boolean {
        val source = swapSource ?: return false
        val next = pieces[replacement] ?: return false
        if (source !in placedIds || source == replacement || !next.swappableArt || !own(replacement)) return false
        val destination = pose(source)
        // An already displayed piece trades places; a loose one returns the former picture to the library.
        val former = if (replacement in placedIds) pose(replacement) else null
        val ignored = setOf(source, replacement)
        if (!wallClear(next, destination, ignored) ||
            former != null && !wallClear(pieces.getValue(source), former, ignored)) return false
        val originals = (storedOriginals - replacement).toMutableSet()
        val positions = saved.toMutableMap()
        positions[replacement] = destination
        val edit = prefs.edit().putString("decor.$replacement", destination.encode())
        if (former != null) {
            positions[source] = former
            edit.putString("decor.$source", former.encode())
        } else {
            positions.remove(source)
            if (pieces.getValue(source).origin != null) originals.add(source)
            edit.remove("decor.$source")
        }
        edit.putStringSet("storedRoomArt", originals).apply()
        storedOriginals = originals
        saved = positions
        closeShop()
        selected = replacement
        return true
    }
    fun openShop() { if (carrying == null) { selected = null; shopOpen = true } }
    fun closeShop() { shopOpen = false; swapSource = null }
    fun earned(id: String) = id.removePrefix("art:") in prefs.getStringSet("worldPaintings",emptySet()).orEmpty()
    fun own(id: String) = id in purchased || pieces[id]?.origin != null || id.startsWith("art:") && earned(id)

    /** Coin debit and inventory grant share the same preferences transaction. */
    fun buyOrCarry(id: String, balance: Int, onBalance: (Int) -> Unit) {
        val item = pieces[id] ?: return
        if (carrying != null) return
        if (!own(id)) {
            if (item.price <= 0 || balance < item.price) return
            val next = purchased + id
            prefs.edit().putInt("coins",balance-item.price).putStringSet("roomFurniture", next).apply()
            purchased = next
            onBalance(balance-item.price)
        }
        begin(id)
    }
    fun begin(id: String) {
        if (pieces[id]?.movable != true || !own(id) || carrying != null) return
        selected=null;shopOpen=false;target=null;carrying=id
    }
    fun cancel() { carrying=null;target=null;selected=null }
    private fun authored(id: String): DecorPose {
        val piece = pieces.getValue(id)
        val support = when(id) {
            "record-player","vinyl-rack","mushroom" -> "console"
            "table-globe" -> "sideboard"
            else -> ""
        }
        return DecorPose(piece.origin ?: RoomSpot(0f,0f,0f), piece.baseYaw, support)
    }
    fun pose(id: String) = saved[id] ?: authored(id)
    fun spot(id: String) = pose(id).at
    fun hasRecordControls(id: String?) = id == "record-player" || id == "vinyl-rack" ||
        (id != null && pose("record-player").support == id)
    fun visualPose(id: String): DecorPose {
        val original=pose(id)
        val held=carrying ?: return original
        if (id!=held && original.support!=held) return original
        val floating=target?.pose ?: return original
        if (id==held) return floating
        val t=floating.at
        val old=spot(held);val p=original.at
        return original.copy(at=RoomSpot(p.x+t.x-old.x,p.y+t.y-old.y,p.z+t.z-old.z))
    }
    fun visibleAtAim(id: String): Boolean {
        val moving = carrying ?: return true
        return target != null || (moving != id && pose(id).support != moving)
    }
    fun visualSpot(id: String)=visualPose(id).at
    fun putDown(alsoPersist:(SharedPreferences.Editor,RoomSpot,RoomSpot)->Unit = { _,_,_ -> }): Boolean {
        val id=carrying ?: return false
        val next=target?.takeIf { it.allowed }?.pose ?: return false
        val old=spot(id);val moved=mutableMapOf(id to next)
        // Resting accessories travel with their supporting furniture until individually lifted.
        for (other in placedIds) if (other!=id && pose(other).support==id) {
            val o=pose(other);val p=o.at
            moved[other]=o.copy(at=RoomSpot(p.x+next.at.x-old.x,p.y+next.at.y-old.y,p.z+next.at.z-old.z))
        }
        val edit=prefs.edit()
        moved.forEach { (k,v)->edit.putString("decor.$k",v.encode()) }
        if (id in storedOriginals) {
            storedOriginals = storedOriginals - id
            edit.putStringSet("storedRoomArt", storedOriginals)
        }
        alsoPersist(edit,old,next.at)
        edit.apply()
        saved=saved+moved;cancel();return true
    }
    fun obstacles() = placedIds.mapNotNull { id ->
        val i=pieces.getValue(id);val p=spot(id)
        if (id==carrying || pose(id).support==carrying && carrying!=null || !i.blocksWalk || p.y>.56f) null
        else FloorObstacle(p.x,p.z,i.width,i.depth)
    }
    fun shelfSpot(occupied: Collection<RoomSpot>): RoomSpot? {
        val at=spot("console")
        return SHELF_SLOTS.asSequence()
            .map { RoomSpot(it.x+at.x,it.y+at.y,it.z+at.z+4.12f) }
            .firstOrNull { slot ->
                occupied.none { abs(it.x-slot.x)<.15f && abs(it.y-slot.y)<.11f && abs(it.z-slot.z)<.14f } &&
                    placedIds.none { other ->
                        if (other=="console") false else {
                            val item=pieces.getValue(other)
                            val p=spot(other)
                            !item.wall && p.y+item.height>slot.y+.015f && p.y<slot.y+.12f &&
                                abs(slot.x-p.x)<item.width*.5f+.055f && abs(slot.z-p.z)<item.depth*.5f+.055f
                        }
                    }
            }
    }
    fun surfaces(): List<PlacementSurface> = placedIds.flatMap { id ->
        val p=spot(id);val i=pieces.getValue(id)
        val heights=when(id) {
            "console" -> listOf(.905f,.416f,.106f)
            "coffee-table" -> listOf(.412f)
            "dining-table" -> listOf(.785f)
            "sideboard" -> listOf(.621f)
            "chair" -> listOf(.431f)
            "ottoman" -> listOf(.331f)
            else -> if (i.category=="table" || i.category=="shelf") listOf(i.height) else emptyList()
        }
        heights.map { PlacementSurface((p.x-i.width*.48f)..(p.x+i.width*.48f),(p.z-i.depth*.48f)..(p.z+i.depth*.48f),p.y+it,id) }
    }
    fun updateAim(eye: RoomSpot, dx: Float, dy: Float, dz: Float) {
        val id=carrying ?: return
        val item=pieces.getValue(id)
        val next=if (item.wall) {
            wallPose(eye, dx, dy, dz, item.depth)?.let { DecorTarget(it, wallClear(item, it, setOf(id))) }
        } else {
            val supports = if (item.tabletop) surfaces().filter { it.id != id } else emptyList()
            supportingPose(eye, dx, dy, dz, supports)?.let { t ->
                val (x,y,z) = t.at
                val support = t.support
                val inside=abs(x)+item.width/2 < 4.24f && abs(z)+item.depth/2<4.40f
                val inDoor=abs(x+1.88f)<.65f+item.width/2 && z>3.48f-item.depth/2
                val overlaps=item.blocksWalk && placedIds.any { other ->
                    if (other==id || other==support || pose(other).support==id) false else {
                        val o=pieces.getValue(other);val a=spot(other)
                        o.blocksWalk && y<a.y+o.height-.015f && a.y<y+item.height-.015f &&
                        abs(x-a.x)<(item.width+o.width)*.5f-.01f && abs(z-a.z)<(item.depth+o.depth)*.5f-.01f
                    }
                }
                DecorTarget(t,inside && !inDoor && !overlaps)
            }
        }
        val prev=target
        if (next==null || prev==null || prev.allowed!=next.allowed || prev.pose.yaw!=next.pose.yaw || prev.pose.support!=next.pose.support ||
            abs(prev.pose.at.x-next.pose.at.x)+abs(prev.pose.at.y-next.pose.at.y)+abs(prev.pose.at.z-next.pose.at.z)>.004f) target=next
    }
    private fun wallClear(i: Furnishing, p: DecorPose, ignored: Set<String>): Boolean {
        val a=p.at;val side=p.yaw== -90f;val along=if(side)a.z else a.x
        val limit=if(side)4.24f else 4.19f
        return a.y-i.height/2 >= .02f && a.y+i.height/2 < 4.06f && abs(along)+i.width/2<limit && !(p.yaw==0f && a.x-i.width/2< -2.85f) &&
            !(p.yaw==180f && CornerShop.coversDisplay(a.x,a.y,i.width,i.height)) &&
            !(p.yaw==180f && abs(a.x+1.88f)<.61f+i.width/2 && a.y-i.height/2<2.28f) &&
            placedIds.none { other ->
                val o=pieces.getValue(other);val q=pose(other)
                other !in ignored && o.wall && q.yaw==p.yaw && abs(q.at.y-a.y)<(i.height+o.height)*.5f+.025f &&
                    abs((if(side)q.at.z else q.at.x)-along)<(i.width+o.width)*.5f+.025f
            }
    }
}

/** Proximity actions follow a nearby object that the user is actually looking at. */
internal fun RoomFurnishing.facingItem(eye:RoomSpot, dx:Float, dy:Float, dz:Float):String? {
    var best:String?=null
    var distance=Float.POSITIVE_INFINITY
    for (id in placedIds) {
        if(id=="machine" || id=="stand") continue
        val item=pieces.getValue(id)
        val p=spot(id)
        val vx=p.x-eye.x;val vy=p.y+(if(item.wall)0f else item.height*.6f)-eye.y;val vz=p.z-eye.z
        val depth=vx*dx+vy*dy+vz*dz
        if(depth !in .14f..2.4f || depth>distance) continue
        val reach=when {item.wall->.22f; id=="record-player"->.29f; else->max(.16f,item.width.coerceAtMost(item.depth)*.42f)}
        val x=vx-depth*dx;val y=vy-depth*dy;val z=vz-depth*dz
        if(x*x+y*y+z*z<reach*reach) {best=id;distance=depth}
    }
    val deck = spot("record-player")
    val behindDeck = best?.let { pieces.getValue(it).wall && distance > hypot(deck.x-eye.x, deck.z-eye.z) } == true
    return if ((best == null || behindDeck) && lookingAtListeningArea(eye, dx, dy, dz, deck)) "record-player" else best
}

/** A listener looking ahead at the console can use the deck without aiming down at its thin lid. */
internal fun lookingAtListeningArea(eye: RoomSpot, dx: Float, dy: Float, dz: Float, deck: RoomSpot): Boolean {
    val x = deck.x - eye.x
    val z = deck.z - eye.z
    val range = hypot(x, z)
    val horizontal = hypot(dx, dz)
    return range in .15f..1.85f && dy in -.85f.. .12f && abs(eye.y - deck.y) < 1.35f &&
        horizontal > .1f && x * dx + z * dz >= .76f * range * horizontal
}

/** One-time user-requested restoration; subsequent furniture moves remain persistent. */
internal fun restoreUnboxPoster(prefs: SharedPreferences) {
    if (!prefs.getBoolean("restoredUnboxPoster20261124",false)) {
        prefs.edit().remove("decor.unboxing-print")
            .putStringSet("storedRoomArt",prefs.getStringSet("storedRoomArt",emptySet()).orEmpty()-"unboxing-print")
            .putBoolean("restoredUnboxPoster20261124",true).apply()
    }
}
