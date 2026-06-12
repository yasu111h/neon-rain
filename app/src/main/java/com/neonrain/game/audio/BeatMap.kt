package com.neonrain.game.audio

import android.content.Context
import org.json.JSONObject

/**
 * ビートマップデータ（技術設計書 §4.2）。
 * notes が空の場合はプロシージャルスポーン（ObstacleSystem側）で運用する。
 */
data class BeatNote(val beat: Double, val lane: Int, val type: String)

data class BeatMap(
    val title: String,
    val audio: String,
    val bpm: Float,
    val offsetMs: Double,
    val notes: List<BeatNote>
) {
    companion object {
        /** assets/songs/<id>/beatmap.json から読み込み。失敗時はデフォルト */
        fun load(context: Context, songId: String): BeatMap {
            return try {
                val json = context.assets.open("songs/$songId/beatmap.json")
                    .bufferedReader().use { it.readText() }
                parse(JSONObject(json))
            } catch (e: Exception) {
                default()
            }
        }

        fun parse(obj: JSONObject): BeatMap {
            val notes = ArrayList<BeatNote>()
            val arr = obj.optJSONArray("notes")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val n = arr.getJSONObject(i)
                    notes.add(
                        BeatNote(
                            n.getDouble("beat"),
                            n.optInt("lane", -1),
                            n.optString("type", "OBSTACLE")
                        )
                    )
                }
            }
            return BeatMap(
                title = obj.optString("title", "Untitled"),
                audio = obj.optString("audio", ""),
                bpm = obj.optDouble("bpm", 80.0).toFloat(),
                offsetMs = obj.optDouble("offsetMs", 0.0),
                notes = notes
            )
        }

        fun default(): BeatMap = BeatMap(
            title = "Metronome 80",
            audio = "",
            bpm = 80f,
            offsetMs = 0.0,
            notes = emptyList()
        )
    }
}
