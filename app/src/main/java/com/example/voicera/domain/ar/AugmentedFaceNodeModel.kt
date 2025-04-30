package com.example.voicera.domain.ar
import com.google.android.filament.Engine
import com.google.android.filament.IndexBuffer
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.RenderableManager.PrimitiveType
import com.google.android.filament.VertexBuffer
import com.google.android.filament.VertexBuffer.AttributeType
import com.google.android.filament.VertexBuffer.VertexAttribute.POSITION
import com.google.android.filament.VertexBuffer.VertexAttribute.UV0
import com.google.ar.core.AugmentedFace
import com.google.ar.core.AugmentedFace.RegionType
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.arcore.transform
import io.github.sceneview.node.MeshNode
import io.github.sceneview.node.Node

class AugmentedFaceNodeModel(
    engine: Engine,
    augmentedFace: AugmentedFace,
    faceMeshMaterialInstance: MaterialInstance? = null,
    builder: RenderableManager.Builder.() -> Unit = {}
) : Node(engine = engine) {

    /**
     * The center of the face, defined to have the origin located behind the nose and between the
     * two cheek bones.
     *
     * Z+ is forward out of the nose, Y+ is upwards, and X+ is towards the left.
     * The units are in meters. When the face trackable state is TRACKING, this pose is synced with
     * the latest frame. When face trackable state is PAUSED, an identity pose will be returned.
     *
     * Use [regionNodes] to retrieve poses of specific regions of the face.
     */
    val centerNode = Node(engine).apply { parent = this@AugmentedFaceNodeModel }

    /**
     * The region nodes at the tip of the nose, the detected face's left side of the forehead,
     * the detected face's right side of the forehead.
     *
     * Defines face regions to query the pose for. Left and right are defined relative to the person
     * that the mesh belongs to. To retrieve the center pose use [AugmentedFace.getCenterPose].
     */
    val regionNodes = RegionType.entries.associateWith {
        Node(engine).apply { parent = this@AugmentedFaceNodeModel }
    }

    private val faceMeshNode: MeshNode? = if (faceMeshMaterialInstance != null) {
        createFaceMeshNode(augmentedFace, faceMeshMaterialInstance, builder)
            .apply { parent = centerNode }
    } else {
        null
    }

    fun update(augmentedFace: AugmentedFace) {
        if (augmentedFace.trackingState == TrackingState.TRACKING) {
            centerNode.worldTransform = augmentedFace.centerPose.transform
            faceMeshNode?.vertexBuffer?.setBufferAt(engine, 0, augmentedFace.meshVertices)
            regionNodes.forEach { (regionType, regionNode) ->
                regionNode.worldTransform = augmentedFace.getRegionPose(regionType).transform
            }
        }
    }

    private fun createFaceMeshNode(
        augmentedFace: AugmentedFace,
        materialInstance: MaterialInstance,
        builder: RenderableManager.Builder.() -> Unit
    ): MeshNode {
        val vertexBuffer = VertexBuffer.Builder()
            .bufferCount(2)
            .vertexCount(augmentedFace.meshVertices.limit() / 3)
            .attribute(POSITION, 0, AttributeType.FLOAT3) // positions (x, y, z)
            .attribute(UV0, 1, AttributeType.FLOAT2)      // texture coordinates (x, y)
            .build(engine).apply {
                setBufferAt(engine, 1, augmentedFace.meshTextureCoordinates)
            }

        val indexBuffer = IndexBuffer.Builder()
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .indexCount(augmentedFace.meshTriangleIndices.limit())
            .build(engine).apply {
                setBuffer(engine, augmentedFace.meshTriangleIndices)
            }

        return MeshNode(
            engine = engine,
            primitiveType = PrimitiveType.TRIANGLES,
            vertexBuffer = vertexBuffer,
            indexBuffer = indexBuffer,
            materialInstance = materialInstance,
            builder = builder
        ).apply {
            onRemovedFromScene = {
                engine.destroyVertexBuffer(vertexBuffer)
                engine.destroyIndexBuffer(indexBuffer)
            }
        }
    }
}