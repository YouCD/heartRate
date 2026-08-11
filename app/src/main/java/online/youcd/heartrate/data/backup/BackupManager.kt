package online.youcd.heartrate.data.backup

import online.youcd.heartrate.data.db.HeartRateEntity
import online.youcd.heartrate.data.db.SessionEntity
import online.youcd.heartrate.data.model.Gender
import online.youcd.heartrate.data.model.MaxHrMode
import online.youcd.heartrate.data.model.UserProfile
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor() {

    data class BackupData(
        val profile: UserProfile?,
        val sessions: List<SessionEntity>,
        val heartRates: List<HeartRateEntity>
    )

    companion object {
        const val PROFILE_JSON = "profile.json"
        const val SESSIONS_JSON = "sessions.json"
    }

    fun buildZip(
        profile: UserProfile,
        sessions: List<SessionEntity>,
        heartRates: List<HeartRateEntity>
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry(PROFILE_JSON))
            zos.write(profileToJson(profile).toString().toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            val sessionsJson = JSONObject().apply {
                put("version", 1)
                put("sessions", JSONArray().apply {
                    sessions.forEach { put(sessionToJson(it)) }
                })
                put("heartRates", JSONArray().apply {
                    heartRates.forEach { put(heartRateToJson(it)) }
                })
            }
            zos.putNextEntry(ZipEntry(SESSIONS_JSON))
            zos.write(sessionsJson.toString().toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        return baos.toByteArray()
    }

    fun parse(input: InputStream): BackupData {
        val contents = mutableMapOf<String, String>()
        ZipInputStream(input).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == PROFILE_JSON || entry.name == SESSIONS_JSON) {
                    contents[entry.name] = zis.readBytes().toString(Charsets.UTF_8)
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val profile = contents[PROFILE_JSON]
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?.let { jsonToProfile(it) }

        val sessions = mutableListOf<SessionEntity>()
        val heartRates = mutableListOf<HeartRateEntity>()
        contents[SESSIONS_JSON]
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?.let { obj ->
                obj.optJSONArray("sessions")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        runCatching { sessions.add(jsonToSession(arr.getJSONObject(i))) }
                    }
                }
                obj.optJSONArray("heartRates")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        runCatching { heartRates.add(jsonToHeartRate(arr.getJSONObject(i))) }
                    }
                }
            }

        return BackupData(profile, sessions, heartRates)
    }

    private fun profileToJson(profile: UserProfile): JSONObject = JSONObject().apply {
        put("nickname", profile.nickname)
        put("gender", profile.gender.name)
        put("birthYear", profile.birthYear)
        put("birthMonth", profile.birthMonth)
        put("heightCm", profile.heightCm)
        put("weightKg", profile.weightKg)
        put("maxHrMode", profile.maxHrMode.name)
        put("manualMaxHr", profile.manualMaxHr)
    }

    private fun jsonToProfile(json: JSONObject): UserProfile = UserProfile(
        nickname = json.optString("nickname"),
        gender = runCatching { Gender.valueOf(json.optString("gender")) }
            .getOrDefault(Gender.MALE),
        birthYear = json.optInt("birthYear", 1996),
        birthMonth = json.optInt("birthMonth", 1),
        heightCm = json.optInt("heightCm", 170),
        weightKg = json.optInt("weightKg", 70),
        maxHrMode = runCatching { MaxHrMode.valueOf(json.optString("maxHrMode")) }
            .getOrDefault(MaxHrMode.AUTO),
        manualMaxHr = json.optInt("manualMaxHr", 190)
    )

    private fun sessionToJson(session: SessionEntity): JSONObject = JSONObject().apply {
        put("id", session.id)
        put("startTime", session.startTime)
        put("endTime", session.endTime)
        put("durationMillis", session.durationMillis)
        put("avgBpm", session.avgBpm)
        put("maxBpm", session.maxBpm)
        put("minBpm", session.minBpm)
        put("calories", session.calories)
        put("maxHr", session.maxHr)
        put("zoneSeconds", session.zoneSeconds)
        put("deviceName", session.deviceName)
        put("note", session.note)
    }

    private fun jsonToSession(json: JSONObject): SessionEntity = SessionEntity(
        id = json.optLong("id"),
        startTime = json.optLong("startTime"),
        endTime = json.optLong("endTime"),
        durationMillis = json.optLong("durationMillis"),
        avgBpm = json.optInt("avgBpm"),
        maxBpm = json.optInt("maxBpm"),
        minBpm = json.optInt("minBpm"),
        calories = json.optInt("calories"),
        maxHr = json.optInt("maxHr"),
        zoneSeconds = json.optString("zoneSeconds"),
        deviceName = json.optString("deviceName"),
        note = json.optString("note")
    )

    private fun heartRateToJson(hr: HeartRateEntity): JSONObject = JSONObject().apply {
        put("id", hr.id)
        put("sessionId", hr.sessionId)
        put("timestamp", hr.timestamp)
        put("bpm", hr.bpm)
    }

    private fun jsonToHeartRate(json: JSONObject): HeartRateEntity = HeartRateEntity(
        id = json.optLong("id"),
        sessionId = json.optLong("sessionId"),
        timestamp = json.optLong("timestamp"),
        bpm = json.optInt("bpm")
    )
}