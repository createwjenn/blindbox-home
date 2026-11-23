package com.jenn.blindboxworld.content

import android.os.SystemClock
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.CollisionComponent
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
    else getChildren().forEach { it.allNamedGroups(groups) }
}

private fun mountTarget(content: SpatialViewContent, holder: Entity, piece: Furnishing, choose:(String)->Boolean) {
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
}

/** Keep the same independent transforms and action targets when the baked daylight shell is replaced. */
internal suspend fun decorateRoom(shell:Entity, root:Entity, content:SpatialViewContent, state:RoomFurnishing,
                                  record: RoomRecordPlayer, choose:(String)->Boolean) {
    val models=mutableMapOf<String,Entity>();shell.allNamedGroups(models)
    val loose=mutableListOf<Entity>()
    val last=mutableMapOf<String,DecorPose>()
    val visibility=mutableMapOf<String,Boolean>()
    try {
        for ((id,holder) in models) state.pieces[id]?.let { mountTarget(content,holder,it,choose) }
        val deck = models["record-player"]?.getChildren().orEmpty()
        val label = deck.firstOrNull { it.getName() == "Ivory limewash" }
        // The label and platter were authored as concentric discs. The printed stripe makes
        // their rotation visible while the arm and base stay fixed.
        label?.addChild(Entity().apply {
            components.set(TransformComponent())
            components.set(ModelComponent(MeshResource.createBox(Vector3(.038f,.001f,.009f),0f),
                glowMaterial(Color4(.39f,.12f,.055f,1f))))
            place(.004f,.0023f,.022f)
            loose.add(this)
        })
        val discs = deck.filter { it == label || it.getName() == "Black vinyl" }
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
                        addChild(payload);mountTarget(content,this,piece,choose)
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
            delay(32)
        }
    } finally {
        loose.forEach { it.releaseScene() }
    }
}
