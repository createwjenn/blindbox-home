package com.jenn.blindboxworld.content

import android.os.SystemClock
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import kotlinx.coroutines.delay
import kotlin.math.max

private fun Entity.allNamedGroups(groups: MutableMap<String,Entity>) {
    val name=getName()
    if (name.startsWith("furnishing_")) groups[name.removePrefix("furnishing_")]=this
    else if (name.startsWith("decor:")) groups[name.substringAfter(':')]=this
    else getChildren().forEach { it.allNamedGroups(groups) }
}

private fun mountTarget(content: SpatialViewContent, holder: Entity, piece: Furnishing, choose:(String)->Boolean): Entity {
    val target=Entity().apply {
        components.set(TransformComponent())
        setName("decor:${piece.id}")
        val side=piece.baseYaw == -90f
        makeTappable(Vector3(if(side) max(.08f,piece.depth) else piece.width, max(.055f,piece.height),
            if(side) piece.width else max(.065f,piece.depth)), if(piece.wall) 0f else piece.height*.5f)
        // Furniture with an offset shade or wide crown should respond where its silhouette is visible.
        // Leave the centre of the rug available for ordinary floor walking.
        fun box(w:Float,h:Float,d:Float,x:Float,y:Float,z:Float) =
            ShapeResource.createBox(Vector3(w,h,d)).offsetByTranslation(Vector3(x,y,z))
        val shapes=when(piece.id) {
            "wool-rug" -> listOf(
                box(piece.width,.045f,.06f,0f,.025f,-piece.depth*.5f),
                box(piece.width,.045f,.06f,0f,.025f,piece.depth*.5f),
                box(.06f,.045f,piece.depth,-piece.width*.5f,.025f,0f),
                box(.06f,.045f,piece.depth,piece.width*.5f,.025f,0f))
            "arc-lamp" -> listOf(box(.40f,1.99f,.40f,0f,.995f,0f),box(.54f,.32f,.54f,1.33f,1.82f,0f))
            "laurel-left","laurel-right" -> listOf(box(.38f,.46f,.38f,0f,.23f,0f),box(.79f,.64f,.79f,0f,.83f,0f))
            else -> emptyList()
        }
        if(shapes.isNotEmpty()) components.set(CollisionComponent(collisionShape=shapes,physicsMaterial=PhysicsMaterialResource()))
    }
    holder.addChild(target)
    registerFurnitureAction(content,target,piece) { choose(piece.id) }
    return target
}

/** Each visible mesh gets its own pick surface, including shelf contents, thin frames and rugs. */
private fun mountMeshTargets(holder: Entity, piece: Furnishing, content: SpatialViewContent,
                             choose: (String) -> Boolean, owned: MutableList<Entity>) {
    val parts = mutableListOf<Entity>()
    fun visit(node: Entity) {
        if (node.components[ModelComponent::class.java]?.mesh != null) parts.add(node)
        node.getChildren().forEach(::visit)
    }
    visit(holder)
    if (parts.isEmpty()) {
        owned.add(mountTarget(content, holder, piece, choose))
        return
    }
    // One compound pick surface lets the compositor highlight the whole object.
    // Imported meshes can have offset pivots and non-uniform scale.
    val shapes = parts.map { part ->
        val mesh = part.components[ModelComponent::class.java]!!.mesh!!
        val scale = part.convertScaleTo(Vector3.ONE, holder)
        val rigid = kotlin.math.abs(scale.x-1f) < .001f && kotlin.math.abs(scale.y-1f) < .001f &&
            kotlin.math.abs(scale.z-1f) < .001f
        val exact = if (rigid) runCatching {
            ShapeResource.createStaticMesh(mesh).offsetByTranslationAndRotation(
                part.convertRotationTo(com.pico.spatial.core.math.Quat(0f,0f,0f,1f), holder),
                part.convertPositionTo(Vector3.ZERO, holder))
        }.getOrNull() else null
        exact ?: part.getVisualBounds(holder, recursive = false, enabledOnly = false).let { bounds ->
            val size = bounds.max-bounds.min
            ShapeResource.createBox(Vector3(max(.01f,size.x),max(.01f,size.y),max(.01f,size.z)))
                .offsetByTranslation(bounds.center)
        }
    }
    holder.components.set(CollisionComponent(collisionShape=shapes, physicsMaterial=PhysicsMaterialResource()))
    holder.components.set(InteractableComponent())
    holder.components.set(HoverEffectComponent())
    registerFurnitureAction(content, holder, piece) { choose(piece.id) }
}

private class FurnitureGlow {
    private var white: com.pico.spatial.core.ecs.resource.UnlitMaterial? = null
    private var outline: com.pico.spatial.core.ecs.resource.UnlitMaterial? = null
    private val layers = mutableListOf<Entity>()
    private var current: Entity? = null

    fun show(holder: Entity?, eye: Vector3? = null) {
        if (holder === current) return
        layers.forEach { it.releaseScene() }
        layers.clear()
        white?.close()
        white = null
        outline?.close()
        outline = null
        current = holder
        if (holder == null) return
        val glow = glowMaterial(Color4(.32f,.32f,.32f,1f), additive=true).apply { setDepthWrite(false) }
        white = glow
        val rim = glowMaterial(Color4.WHITE).apply {
            setCullingMode(com.pico.spatial.core.ecs.resource.MaterialCullingMode.FRONT)
            setDepthWrite(false)
        }
        outline = rim
        fun visit(part: Entity) {
            part.getChildren().forEach(::visit)
            val source = part.components[ModelComponent::class.java] ?: return
            val mesh = source.mesh ?: return
            // A glass lid or globe has no opaque front face to occlude an inverted outline.
            // Keep its transparency and outline the opaque frame and fittings.
            if (source.materials.any { it is com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial &&
                        it.getBlendingMode() == com.pico.spatial.core.ecs.resource.BlendingMode.TRANSPARENT }) return
            val center = part.getVisualBounds(part, recursive=false, enabledOnly=false).center
            val world = part.convertPositionTo(center,null)
            val toEye = eye?.minus(world)
            val offset = if (toEye != null && toEye.length() > .01f)
                part.convertPositionFrom(world+toEye.normalize()*.002f,null)-center else Vector3.ZERO
            val layer = Entity().apply {
                components.set(TransformComponent())
                place(offset.x,offset.y,offset.z)
                components.set(ModelComponent(mesh, Array(source.materials.size.coerceAtLeast(1)) { glow }))
            }
            part.addChild(layer)
            layers.add(layer)
            val shell = Entity().apply {
                components.set(TransformComponent())
                val scale = 1.035f
                place(center.x*(1f-scale),center.y*(1f-scale),center.z*(1f-scale),scale)
                components.set(ModelComponent(mesh, Array(source.materials.size.coerceAtLeast(1)) { rim }))
            }
            part.addChild(shell)
            layers.add(shell)
        }
        visit(holder)
    }

    fun close() { show(null) }
}

/** Keep the same independent transforms and action targets when the baked daylight shell is replaced. */
internal suspend fun decorateRoom(shell:Entity, root:Entity, content:SpatialViewContent, state:RoomFurnishing,
                                  record: RoomRecordPlayer, bounds: MutableMap<String,DecorBounds>,
                                  hovered: () -> String?, focused: () -> String?, externalModel: (String) -> Entity?, eye: () -> Vector3?, choose:(String)->Boolean) {
    val models=mutableMapOf<String,Entity>();shell.allNamedGroups(models)
    val loose=mutableListOf<Entity>()
    val targets=mutableListOf<Entity>()
    val last=mutableMapOf<String,DecorPose>()
    val glow = FurnitureGlow()
    var muted: Entity? = null
    fun restoreHover() {
        muted?.takeIf { it.valid }?.components?.set(HoverEffectComponent())
        muted = null
    }
    fun prepare(holder: Entity, piece: Furnishing) {
        holder.setName("decor:${piece.id}")
        if (piece.id == "stand") {
            // Include the collectible and vitrine above the pedestal in the same station target.
            holder.makeTappable(Vector3(piece.width,piece.height,piece.depth),piece.height*.5f)
            registerFurnitureAction(content,holder,piece) { choose(piece.id) }
            return
        }
        val box = holder.getVisualBounds(holder, recursive=true, enabledOnly=false)
        val half = (box.max-box.min)*.5f
        bounds[piece.id] = DecorBounds(RoomSpot(box.center.x,box.center.y,box.center.z), RoomSpot(half.x,half.y,half.z))
        mountMeshTargets(holder,piece,content,choose,targets)
    }
    val visibility=mutableMapOf<String,Boolean>()
    try {
        // Replace only the authored turntable payload, preserving its placement and controls.
        val turntable=Entity.loadSuspend("asset://props/reference-turntable.glb")
        if (!shell.valid || !root.valid) { turntable.releaseScene(); return }
        models["record-player"]?.let { holder ->
            holder.getChildren().toList().forEach { it.releaseScene() }
            holder.addChild(turntable)

        }
        for ((id,holder) in models) state.pieces[id]?.let { prepare(holder,it) }
        val deck=mutableListOf<Entity>()
        fun descendants(entity: Entity) { deck.add(entity); entity.getChildren().forEach(::descendants) }
        descendants(turntable)
        val discs=deck.filter { it.getName() in setOf("Amber vinyl","Ivory limewash") }
        var spin = 180f
        var lastTick = SystemClock.uptimeMillis()
        while (root.valid && shell.valid) {
            val now = SystemClock.uptimeMillis()
            if (record.isPlaying) {
                spin = (spin - (now - lastTick) * .2f) % 360f // 33⅓ RPM
                discs.forEach { it.components[TransformComponent::class.java]?.setEulerAngles(EulerAngles(0f,spin,0f)) }
            }
            lastTick = now
            val carried=state.carrying
            val active=state.placedIds.toMutableSet().apply { carried?.let(::add) }
            for(id in active) {
                if(id=="machine") continue
                val piece=state.pieces.getValue(id)
                val model=models[id] ?: if(piece.origin==null) {
                    val payload=Entity.loadSuspend("asset://${piece.asset}")
                    if (!shell.valid || !root.valid) { payload.releaseScene();return }
                    Entity().apply {
                        components.set(TransformComponent())
                        addChild(payload);prepare(this,piece)
                        root.addChild(this);loose.add(this);models[id]=this
                    }
                } else continue
                val p=state.visualPose(id)
                if(last[id]!=p) {
                    model.place(p.at.x,p.at.y,p.at.z,yaw=p.yaw-piece.baseYaw)
                    last[id]=p
                }
                val shouldShow=state.visibleAtAim(id)
                if(visibility[id]!=shouldShow) { model.enabled=shouldShow;visibility[id]=shouldShow }
            }
            for ((id,model) in models) if(id !in active && visibility[id]!=false) {model.enabled=false;visibility[id]=false}
            val focus = focused()
            val selected = focus?.let { models[it] ?: externalModel(it) }
            if (selected !== muted) {
                restoreHover()
                if (selected?.valid == true && selected.components[HoverEffectComponent::class.java] != null) {
                    selected.components.remove(HoverEffectComponent::class.java)
                    muted = selected
                }
            }
            val over = hovered()?.takeUnless { it == focus }?.let { models[it] ?: externalModel(it) }
            glow.show(over?.takeIf { it.valid && it.enabled }, eye())
            delay(32)
        }
    } finally {
        // Action proxies belong to this decoration pass, including proxies on imported groups.
        // Destroying the shell alone does not unregister these independently published targets.
        restoreHover()
        glow.close()
        bounds.clear()
        targets.forEach { it.releaseScene() }
        loose.forEach { it.releaseScene() }
    }
}
