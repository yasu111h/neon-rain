package com.neonrain.game.renderer

object MeshBuilder {

    /** ポスト処理用フルスクリーンクアッド（NDC） pos(3)+uv(2) */
    fun buildFullscreenQuad(): Mesh {
        val v = floatArrayOf(
            -1f, -1f, 0f, 0f, 0f,
            1f, -1f, 0f, 1f, 0f,
            1f, 1f, 0f, 1f, 1f,
            -1f, -1f, 0f, 0f, 0f,
            1f, 1f, 0f, 1f, 1f,
            -1f, 1f, 0f, 0f, 1f,
        )
        return Mesh(v, 6, 5, hasNormals = false, hasUVs = true)
    }

    /** 単位クアッド（XY平面、中心原点） pos(3)+uv(2) */
    fun buildQuad(halfW: Float = 0.5f, halfH: Float = 0.5f): Mesh {
        val v = floatArrayOf(
            -halfW, -halfH, 0f, 0f, 1f,
            halfW, -halfH, 0f, 1f, 1f,
            halfW, halfH, 0f, 1f, 0f,
            -halfW, -halfH, 0f, 0f, 1f,
            halfW, halfH, 0f, 1f, 0f,
            -halfW, halfH, 0f, 0f, 0f,
        )
        return Mesh(v, 6, 5, hasNormals = false, hasUVs = true)
    }

    /** 単位ボックス（底面が原点、Y上方向に伸びる） pos(3)+normal(3) */
    fun buildUnitBox(): Mesh {
        val verts = ArrayList<Float>(36 * 6)

        fun addFace(
            p0x: Float, p0y: Float, p0z: Float,
            p1x: Float, p1y: Float, p1z: Float,
            p2x: Float, p2y: Float, p2z: Float,
            p3x: Float, p3y: Float, p3z: Float,
            nx: Float, ny: Float, nz: Float
        ) {
            verts.addAll(listOf(p0x, p0y, p0z, nx, ny, nz))
            verts.addAll(listOf(p1x, p1y, p1z, nx, ny, nz))
            verts.addAll(listOf(p2x, p2y, p2z, nx, ny, nz))
            verts.addAll(listOf(p0x, p0y, p0z, nx, ny, nz))
            verts.addAll(listOf(p2x, p2y, p2z, nx, ny, nz))
            verts.addAll(listOf(p3x, p3y, p3z, nx, ny, nz))
        }

        val h = 0.5f
        // 前面 (+z)
        addFace(-h, 0f, h, h, 0f, h, h, 1f, h, -h, 1f, h, 0f, 0f, 1f)
        // 背面 (-z)
        addFace(h, 0f, -h, -h, 0f, -h, -h, 1f, -h, h, 1f, -h, 0f, 0f, -1f)
        // 右面 (+x)
        addFace(h, 0f, h, h, 0f, -h, h, 1f, -h, h, 1f, h, 1f, 0f, 0f)
        // 左面 (-x)
        addFace(-h, 0f, -h, -h, 0f, h, -h, 1f, h, -h, 1f, -h, -1f, 0f, 0f)
        // 上面 (+y)
        addFace(-h, 1f, h, h, 1f, h, h, 1f, -h, -h, 1f, -h, 0f, 1f, 0f)

        return Mesh(verts.toFloatArray(), verts.size / 6, 6, hasNormals = true, hasUVs = false)
    }

    /** 路面平面（XZ平面、原点中心、+Z前方） pos(3)+normal(3)+uv(2) */
    fun buildRoadPlane(width: Float, zNear: Float, zFar: Float): Mesh {
        val hw = width / 2f
        val v = floatArrayOf(
            -hw, 0f, zNear, 0f, 1f, 0f, 0f, 0f,
            hw, 0f, zNear, 0f, 1f, 0f, 1f, 0f,
            hw, 0f, zFar, 0f, 1f, 0f, 1f, 1f,
            -hw, 0f, zNear, 0f, 1f, 0f, 0f, 0f,
            hw, 0f, zFar, 0f, 1f, 0f, 1f, 1f,
            -hw, 0f, zFar, 0f, 1f, 0f, 0f, 1f,
        )
        return Mesh(v, 6, 8, hasNormals = true, hasUVs = true)
    }
}
