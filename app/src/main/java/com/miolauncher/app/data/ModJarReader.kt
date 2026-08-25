package com.miolauncher.app.data

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File
import java.util.zip.ZipFile

/**
 * 读取本地 mod jar 的元数据（名称 / 版本 / 描述 / 加载器 / 适配 MC 版本 / 图标）。
 * 支持 Fabric / Quilt / Forge / NeoForge 四种格式，解析失败返回 null（由调用方兜底展示文件名）。
 */
object ModJarReader {

    data class ModJarMeta(
        val id: String,
        val name: String,
        val version: String,
        val description: String,
        val loader: ModLoader,
        val minecraftVersionRange: String,
        val iconPath: String,
    )

    private val gson = Gson()

    /** 读取 jar 元数据；非 mod 或解析失败返回 null。 */
    fun readMeta(jar: File): ModJarMeta? = try {
        ZipFile(jar).use { zf ->
            val quilt = zf.getEntry("quilt.mod.json")?.let { readText(zf, it.name) }
            val fabric = zf.getEntry("fabric.mod.json")?.let { readText(zf, it.name) }
            val neoforge = zf.getEntry("META-INF/neoforge.mods.toml")?.let { readText(zf, it.name) }
            val forge = if (neoforge == null) zf.getEntry("META-INF/mods.toml")?.let { readText(zf, it.name) } else null

            when {
                quilt != null -> parseQuilt(quilt)
                fabric != null -> parseFabric(fabric)
                neoforge != null -> parseForgeToml(neoforge, ModLoader.NEO_FORGE)
                forge != null -> parseForgeToml(forge, ModLoader.FORGE)
                else -> null
            }
        }
    } catch (_: Exception) {
        null
    }

    /** 从 jar 内读取图标字节（png/jpg），无则 null。 */
    fun readIconBytes(jar: File, iconPath: String): ByteArray? {
        if (iconPath.isBlank()) return null
        return try {
            ZipFile(jar).use { zf ->
                zf.getEntry(iconPath)?.let { zf.getInputStream(it).use { i -> i.readBytes() } }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 当前版本是否兼容：加载器一致 且 声明支持的 MC 版本范围覆盖当前版本。
     * 任一项无法确认即视为不兼容。
     */
    fun isCompatible(meta: ModJarMeta?, gameVersion: String?, gameLoader: ModLoader?): Boolean {
        if (meta == null) return false
        if (gameLoader == null || meta.loader == ModLoader.NONE) return false
        if (meta.loader != gameLoader) return false
        if (gameVersion.isNullOrBlank()) return false
        return mcVersionMatches(meta.minecraftVersionRange, gameVersion)
    }

    /** 从版本 json 推断加载器（读 libraries，未命中回退 NONE=原版）。 */
    fun detectVersionLoader(json: File): ModLoader {
        if (!json.isFile) return ModLoader.NONE
        return try {
            val libs = gson.fromJson(json.readText(), JsonObject::class.java)
                ?.getAsJsonArray("libraries") ?: JsonArray()
            for (lib in libs) {
                val name = lib.asJsonObject?.get("name")?.asString ?: continue
                when {
                    name.contains("org.quiltmc:quilt-loader") -> return ModLoader.QUILT
                    name.contains("net.fabricmc:fabric-loader") -> return ModLoader.FABRIC
                    name.contains("net.neoforged:neoforge") -> return ModLoader.NEO_FORGE
                    name.contains("net.minecraftforge:forge") || name.contains("net.minecraftforge:fmlloader") ->
                        return ModLoader.FORGE
                }
            }
            ModLoader.NONE
        } catch (_: Exception) {
            ModLoader.NONE
        }
    }

    // ---------- 各格式解析 ----------

    private fun readText(zf: ZipFile, name: String): String =
        zf.getInputStream(zf.getEntry(name)).use { String(it.readBytes(), Charsets.UTF_8) }

    private fun parseFabric(text: String): ModJarMeta? {
        val root = gson.fromJson(text, JsonObject::class.java) ?: return null
        val id = root.get("id")?.takeUnless { it.isJsonNull }?.asString ?: return null
        val depends = root.get("depends")?.takeIf { it.isJsonObject }?.asJsonObject
        val mc = depends?.get("minecraft")
        val mcRange = when {
            mc == null || mc.isJsonNull -> ""
            mc.isJsonPrimitive -> mc.asString
            mc.isJsonObject -> mc.asJsonObject.get("version")?.takeUnless { it.isJsonNull }?.asString ?: ""
            else -> ""
        }
        return ModJarMeta(
            id = id,
            name = root.get("name")?.takeUnless { it.isJsonNull }?.asString ?: id,
            version = root.get("version")?.takeUnless { it.isJsonNull }?.asString ?: "",
            description = root.get("description")?.takeUnless { it.isJsonNull }?.asString ?: "",
            loader = ModLoader.FABRIC,
            minecraftVersionRange = mcRange,
            iconPath = root.get("icon")?.takeUnless { it.isJsonNull }?.asString ?: "",
        )
    }

    private fun parseQuilt(text: String): ModJarMeta? {
        val root = gson.fromJson(text, JsonObject::class.java) ?: return null
        val loader = root.get("quilt_loader")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val id = loader.get("id")?.takeUnless { it.isJsonNull }?.asString ?: return null
        val meta = loader.get("metadata")?.takeIf { it.isJsonObject }?.asJsonObject
        var mcRange = ""
        loader.getAsJsonArray("depends").forEach { d ->
            val doo = d.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            if (doo.get("id")?.asString == "minecraft") {
                val v = doo.get("versions")
                if (v != null && !v.isJsonNull && v.isJsonPrimitive) mcRange = v.asString
            }
        }
        return ModJarMeta(
            id = id,
            name = meta?.get("name")?.takeUnless { it.isJsonNull }?.asString ?: id,
            version = loader.get("version")?.takeUnless { it.isJsonNull }?.asString ?: "",
            description = meta?.get("description")?.takeUnless { it.isJsonNull }?.asString ?: "",
            loader = ModLoader.QUILT,
            minecraftVersionRange = mcRange,
            iconPath = meta?.get("icon")?.takeUnless { it.isJsonNull }?.asString ?: "",
        )
    }

    /** 最小 TOML 解析：取第一个 [[mods]] 块的标量键，以及声明 minecraft 依赖的 versionRange。 */
    private fun parseForgeToml(text: String, loader: ModLoader): ModJarMeta? {
        val lines = text.lineSequence().toList()
        var section = ""
        var inFirstMods = false
        var id = ""
        var name = ""
        var version = ""
        var description = ""
        var logoFile = ""
        var mcRange = ""

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            when {
                line.startsWith("[[") -> {
                    section = line.substring(2).trimEnd(']').trim()
                    if (section == "mods") inFirstMods = true else inFirstMods = false
                }
                line.startsWith("[") -> {
                    section = line.substring(1).trimEnd(']').trim()
                    inFirstMods = false
                }
                else -> {
                    val eq = line.indexOf('=')
                    if (eq <= 0) { i++; continue }
                    val key = line.substring(0, eq).trim()
                    val raw = line.substring(eq + 1).trim()

                    fun stripQuotes(s: String): String {
                        var t = s
                        val hash = t.indexOf(" #")
                        if (hash >= 0) t = t.substring(0, hash)
                        t = t.trim()
                        if ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("'") && t.endsWith("'"))) {
                            t = t.substring(1, t.length - 1)
                        }
                        return t
                    }

                    when {
                        section.startsWith("dependencies.") && key == "modId" ->
                            if (stripQuotes(raw) == "minecraft") {
                                while (i + 1 < lines.size && lines[i + 1].trim().startsWith("versionRange")) {
                                    i++
                                    mcRange = stripQuotes(lines[i].substringAfter("=").trim())
                                }
                            }
                        inFirstMods && id.isEmpty() && key == "modId" -> id = stripQuotes(raw)
                        inFirstMods && name.isEmpty() && key == "displayName" -> name = stripQuotes(raw)
                        inFirstMods && version.isEmpty() && key == "version" -> version = stripQuotes(raw)
                        inFirstMods && logoFile.isEmpty() && key == "logoFile" -> logoFile = stripQuotes(raw)
                        inFirstMods && description.isEmpty() && key == "description" -> {
                            if (raw.startsWith("'''") || raw.startsWith("\"\"\"")) {
                                val delim = if (raw.startsWith("'''")) "'''" else "\"\"\""
                                val content = raw.removePrefix(delim)
                                if (raw.endsWith(delim)) {
                                    description = content.dropLast(delim.length).trim()
                                } else {
                                    val sb = StringBuilder(content.trimStart())
                                    var closed = false
                                    while (i + 1 < lines.size) {
                                        i++
                                        val l = lines[i].trim()
                                        if (l.contains(delim)) {
                                            sb.append("\n").append(l.substringBefore(delim).trimEnd())
                                            closed = true
                                            break
                                        }
                                        sb.append("\n").append(l)
                                    }
                                    if (closed) description = sb.toString().trim() else description = content.trim()
                                }
                            } else {
                                description = stripQuotes(raw)
                            }
                        }
                    }
                }
            }
            i++
        }
        return finish(id, name, version, description, logoFile, loader, mcRange)
    }

    private fun finish(
        id: String, name: String, version: String, description: String,
        logoFile: String, loader: ModLoader, mcRange: String,
    ): ModJarMeta? {
        if (id.isEmpty()) return null
        return ModJarMeta(
            id = id,
            name = name.ifEmpty { id },
            version = version,
            description = description,
            loader = loader,
            minecraftVersionRange = mcRange,
            iconPath = logoFile,
        )
    }

    // ---------- MC 版本范围匹配 ----------

    private fun parseVersionParts(v: String): List<Int> =
        v.trim().split('.')
            .flatMap { it.split('-') }
            .mapNotNull { it.trim().toIntOrNull() }

    private fun compare(a: List<Int>, b: List<Int>): Int {
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return if (x < y) -1 else 1
        }
        return 0
    }

    /** 判断声明的版本范围是否覆盖目标 MC 版本。 */
    fun mcVersionMatches(range: String, version: String): Boolean {
        val r = range.trim()
        if (r.isEmpty() || r == "*") return true
        val vp = parseVersionParts(version)

        // Maven 区间 [a,b] / [a,) / (,b]
        if (r.startsWith("[") || r.startsWith("(")) {
            val inner = r.substring(1, r.length - 1)
            val parts = inner.split(",")
            if (parts.size == 2) {
                val loIncl = r.startsWith("[")
                val hiIncl = r.endsWith("]")
                var ok = true
                if (parts[0].isNotBlank()) {
                    val c = compare(parseVersionParts(parts[0]), vp)
                    ok = ok && if (loIncl) c <= 0 else c < 0
                }
                if (parts[1].isNotBlank()) {
                    val c = compare(parseVersionParts(parts[1]), vp)
                    ok = ok && if (hiIncl) c >= 0 else c > 0
                }
                return ok
            }
            return false
        }

        // x 通配：1.21.x / 1.x
        if (r.contains('x') || r.contains('X')) {
            val pat = r.split('.').map { it.trim().lowercase() }
            val v = version.split('.').map { it.trim().lowercase() }
            for (i in pat.indices) {
                val p = pat[i]
                if (p == "x") continue
                val cur = v.getOrNull(i) ?: return false
                if (p != cur) return false
            }
            return true
        }

        // 比较运算符
        when {
            r.startsWith(">=") -> return compare(parseVersionParts(r.substring(2)), vp) <= 0
            r.startsWith(">") -> return compare(parseVersionParts(r.substring(1)), vp) < 0
            r.startsWith("<=") -> return compare(parseVersionParts(r.substring(2)), vp) >= 0
            r.startsWith("<") -> return compare(parseVersionParts(r.substring(1)), vp) > 0
            r.startsWith("=") -> return compare(parseVersionParts(r.substring(1)), vp) == 0
        }

        // 连字符区间 1.20-1.21
        if (r.contains("-") && !r.startsWith("-")) {
            val parts = r.split("-", limit = 2)
            return compare(parseVersionParts(parts[0]), vp) <= 0 &&
                compare(parseVersionParts(parts[1]), vp) >= 0
        }

        // 精确匹配
        return compare(parseVersionParts(r), vp) == 0
    }
}
