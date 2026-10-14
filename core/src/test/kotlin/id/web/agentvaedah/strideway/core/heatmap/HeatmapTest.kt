package id.web.agentvaedah.strideway.core.heatmap

import id.web.agentvaedah.strideway.core.model.LatLon
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HeatmapTest {
    @Test
    fun pointsInSameCellMerge() {
        val pts = listOf(LatLon(-6.2, 106.8), LatLon(-6.20001, 106.80001), LatLon(-6.3, 106.9))
        val cells = Heatmap.bin(pts, cellM = 50.0)
        assertEquals(2, cells.size)
        assertEquals(1.0, cells.maxOf { it.weight }, 1e-9)
        assertEquals(0.5, cells.minOf { it.weight }, 1e-9)
    }

    @Test
    fun capKeepsHeaviestCells() {
        val pts = (0 until 100).flatMap { i -> List(if (i < 5) 10 else 1) { LatLon(i * 0.01, 0.0) } }
        val cells = Heatmap.bin(pts, cellM = 50.0, maxCells = 5)
        assertEquals(5, cells.size)
        assertTrue(cells.all { it.weight == 1.0 })
    }

    @Test
    fun emptyIsEmpty() {
        assertTrue(Heatmap.bin(emptyList()).isEmpty())
    }
}
