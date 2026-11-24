package com.jenn.blindboxworld.content

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class FurnitureAssetTest {
    private val source = sequenceOf(File("src/main"), File("app/src/main")).first(File::isDirectory)
    private fun header(file: File, variant: Boolean = false): JsonObject {
        val data=file.readBytes()
        val start=if(variant) 4 else 20
        val length=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getInt(if(variant) 0 else 12)
        return JsonParser.parseString(String(data,start,length,Charsets.UTF_8)).asJsonObject
    }
    @Test fun shopModelsMatchPricesAndPhotographs() {
        val folder=File(source,"assets/furniture")
        val rows=JsonParser.parseString(File(folder,"catalogue.json").readText()).asJsonArray
        assertTrue(rows.size()>=20)
        val ids=mutableSetOf<String>()
        assertTrue(rows.map { it.asJsonArray }.filter { it[2].asString=="frame" }.map { it[5].asFloat }.distinct().size>=3)
        rows.forEach { entry ->
            val r=entry.asJsonArray;val id=r[0].asString
            assertTrue(ids.add(id))
            val price=r[4].asInt
            assertTrue("$id price must be 10–30 in steps of five",price in 10..30 && price%5==0)
            if (r[2].asString=="frame") assertEquals("$id frame price",10,price)
            else assertTrue("$id must cost more than a frame",price>10)
            assertTrue((5..7).all { r[it].asFloat>.02f })
            assertTrue(File(source,"res/drawable-nodpi/room_$id.png").isFile)
            assertTrue(header(File(folder,"$id.glb")).getAsJsonArray("meshes").size()>0)
        }
        for(id in FIGURE_IDS) {
            val path="painting_${id.replace('-','_')}"
            assertTrue(File(source,"res/drawable-nodpi/$path.jpg").isFile)
            assertTrue(header(File(folder,"$path.glb")).getAsJsonArray("images").size()>0)
        }
    }
    @Test fun authoredAssembliesSurviveEveryLightingReplacement() {
        val assets=File(source,"assets")
        val required=loftPieces.map { it.id }.toSet()-"machine"
        for(file in listOf(File(assets,"collection-room.glb"))+File(assets,"atmosphere").listFiles { f->f.extension=="look" }!!.toList()) {
            val doc=header(file,file.extension=="look")
            val nodes=doc.getAsJsonArray("nodes")
            val named=nodes.map { it.asJsonObject }.filter { it["name"].asString.startsWith("furnishing_") }
            assertEquals(required,named.map { it["name"].asString.removePrefix("furnishing_") }.toSet())
            val roots=doc.getAsJsonArray("scenes")[0].asJsonObject.getAsJsonArray("nodes").map { it.asInt }.toSet()
            val children=named.flatMap { n->n.getAsJsonArray("children").map { it.asInt } }
            assertEquals(children.size,children.distinct().size)
            assertTrue(children.none { it in roots })
        }
    }
    @Test fun projectedOutlinesCoverEveryMovableMesh() {
        val assets=File(source,"assets/furniture")
        val profiles=JsonParser.parseString(File(assets,"footprints.json").readText()).asJsonObject
        val products=JsonParser.parseString(File(assets,"catalogue.json").readText()).asJsonArray.map { it.asJsonArray[0].asString }
        val expected=loftPieces.map { it.id }+products+FIGURE_IDS.flatMap { listOf("art:$it","figure:$it") }
        assertEquals(expected.toSet(),profiles.keySet())
        for((id,value) in profiles.entrySet()) {
            val points=value.asJsonArray.map { it.asJsonArray[0].asFloat to it.asJsonArray[1].asFloat }
            assertTrue(id,points.size in 3..64)
            for(i in points.indices) {
                val a=points[i];val b=points[(i+1)%points.size];val c=points[(i+2)%points.size]
                assertTrue(id,(b.first-a.first)*(c.second-b.second)-(b.second-a.second)*(c.first-b.first)>=-.00001f)
            }
        }
        assertTrue(profiles["convex-mirror"].asJsonArray.size()>15)
        assertTrue(profiles["kiln_vase"].asJsonArray.size()>15)
        assertTrue(profiles["figure:ballerina-capuccina"].asJsonArray.size()>8)
        assertTrue(profiles["arc-lamp"].asJsonArray.maxOf { it.asJsonArray[0].asFloat }>1.5f)
    }
    @Test fun relocatedFurnitureChangesBothCollisionAndWalkRoutes() {
        val origin=FloorPoint(0f,-1.70f)
        val moved=FloorPoint(-1f,.6f)
        try {
            RoomFloor.setFurnishings(listOf(FloorObstacle(moved.x,moved.z,.56f,.56f)))
            assertTrue(RoomFloor.isWalkable(origin));assertFalse(RoomFloor.isWalkable(moved))
            val path=RoomFloor.route(FloorPoint(-1f,1.8f),FloorPoint(-1f,-.8f))
            assertTrue(path.size>1);assertEquals(FloorPoint(-1f,-.8f),path.last())
        } finally {RoomFloor.setFurnishings(null)}
        assertFalse(RoomFloor.isWalkable(origin));assertTrue(RoomFloor.isWalkable(moved))
    }
    @Test fun artworkAndShelfItemsKeepTheirPoseAcrossReloads() {
        for(p in listOf(DecorPose(RoomSpot(-.87f,1.68f,-4.33f)),DecorPose(RoomSpot(4.16f,1.4f,.33f),-90f),
            DecorPose(RoomSpot(-.55f,.905f,-4.12f),0f,"console")))
            assertEquals(p,DecorPose.decode(p.encode()))
        assertNull(DecorPose.decode("NaN,2,3|0|"))
        assertNull(DecorPose.decode("300,2,3|0|"))
    }
}
