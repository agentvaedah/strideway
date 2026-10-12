package id.web.agentvaedah.strideway.core.track

class TrackParseException(message: String) : Exception(message)

class XmlNode(
    val name: String,
    val attrs: Map<String, String>,
    val children: List<XmlNode>,
    val text: String,
) {
    fun child(name: String): XmlNode? = children.firstOrNull { it.name == name }

    fun children(name: String): List<XmlNode> = children.filter { it.name == name }

    fun descendants(name: String): List<XmlNode> {
        val out = ArrayList<XmlNode>()
        fun walk(n: XmlNode) {
            for (c in n.children) {
                if (c.name == name) out += c
                walk(c)
            }
        }
        walk(this)
        return out
    }

    fun firstDescendant(name: String): XmlNode? {
        for (c in children) {
            if (c.name == name) return c
            c.firstDescendant(name)?.let { return it }
        }
        return null
    }
}

/**
 * Small XML reader for track files. StAX is missing on Android and XmlPullParser is missing
 * on the JVM, so :core carries its own. Namespace prefixes are dropped from element and attribute names.
 */
object MiniXml {
    private class Builder(val name: String, val attrs: Map<String, String>) {
        val children = ArrayList<XmlNode>()
        val text = StringBuilder()
        fun build() = XmlNode(name, attrs, children, text.toString().trim())
    }

    fun parse(xml: String): XmlNode {
        val s = xml.removePrefix("﻿")
        val stack = ArrayList<Builder>()
        var root: XmlNode? = null
        var i = 0
        val n = s.length
        while (i < n) {
            val c = s[i]
            if (c != '<') {
                val end = s.indexOf('<', i).let { if (it < 0) n else it }
                stack.lastOrNull()?.text?.append(decodeEntities(s.substring(i, end)))
                i = end
                continue
            }
            when {
                s.startsWith("<!--", i) -> i = indexAfter(s, "-->", i)
                s.startsWith("<![CDATA[", i) -> {
                    val end = s.indexOf("]]>", i)
                    if (end < 0) throw TrackParseException("CDATA tidak ditutup")
                    stack.lastOrNull()?.text?.append(s, i + 9, end)
                    i = end + 3
                }
                s.startsWith("<?", i) -> i = indexAfter(s, "?>", i)
                s.startsWith("<!", i) -> i = indexAfter(s, ">", i)
                s.startsWith("</", i) -> {
                    val end = s.indexOf('>', i)
                    if (end < 0) throw TrackParseException("Tag penutup rusak")
                    val name = localName(s.substring(i + 2, end).trim())
                    val top = stack.removeLastOrNull() ?: throw TrackParseException("Tag </$name> tanpa pembuka")
                    if (top.name != name) throw TrackParseException("Tag <${top.name}> ditutup oleh </$name>")
                    val node = top.build()
                    if (stack.isEmpty()) root = node else stack.last().children += node
                    i = end + 1
                }
                else -> {
                    val end = findTagEnd(s, i)
                    val selfClosing = s[end - 1] == '/'
                    val body = s.substring(i + 1, if (selfClosing) end - 1 else end)
                    val (name, attrs) = parseTag(body)
                    val b = Builder(name, attrs)
                    if (selfClosing) {
                        val node = b.build()
                        if (stack.isEmpty()) root = node else stack.last().children += node
                    } else {
                        stack += b
                    }
                    i = end + 1
                }
            }
            if (root != null && stack.isEmpty()) break
        }
        return root ?: throw TrackParseException("Dokumen XML kosong atau tidak lengkap")
    }

    private fun indexAfter(s: String, token: String, from: Int): Int {
        val end = s.indexOf(token, from)
        if (end < 0) throw TrackParseException("XML terpotong")
        return end + token.length
    }

    private fun findTagEnd(s: String, from: Int): Int {
        var quote: Char? = null
        var i = from + 1
        while (i < s.length) {
            val c = s[i]
            if (quote != null) {
                if (c == quote) quote = null
            } else if (c == '"' || c == '\'') {
                quote = c
            } else if (c == '>') {
                return i
            }
            i++
        }
        throw TrackParseException("Tag tidak ditutup")
    }

    private fun parseTag(body: String): Pair<String, Map<String, String>> {
        var i = 0
        while (i < body.length && !body[i].isWhitespace()) i++
        val name = localName(body.substring(0, i))
        val attrs = LinkedHashMap<String, String>()
        while (i < body.length) {
            while (i < body.length && body[i].isWhitespace()) i++
            if (i >= body.length) break
            val eq = body.indexOf('=', i)
            if (eq < 0) break
            val key = localName(body.substring(i, eq).trim())
            var j = eq + 1
            while (j < body.length && body[j].isWhitespace()) j++
            if (j >= body.length) break
            val q = body[j]
            if (q != '"' && q != '\'') throw TrackParseException("Atribut $key tanpa tanda kutip")
            val close = body.indexOf(q, j + 1)
            if (close < 0) throw TrackParseException("Atribut $key tidak ditutup")
            attrs[key] = decodeEntities(body.substring(j + 1, close))
            i = close + 1
        }
        return name to attrs
    }

    private fun localName(qName: String): String = qName.substringAfter(':')

    fun decodeEntities(raw: String): String {
        if ('&' !in raw) return raw
        val sb = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '&') {
                val semi = raw.indexOf(';', i)
                if (semi > i) {
                    val ent = raw.substring(i + 1, semi)
                    val decoded = when {
                        ent == "lt" -> "<"
                        ent == "gt" -> ">"
                        ent == "amp" -> "&"
                        ent == "quot" -> "\""
                        ent == "apos" -> "'"
                        ent.startsWith("#x") -> ent.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
                        ent.startsWith("#") -> ent.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) }
                        else -> null
                    }
                    if (decoded != null) {
                        sb.append(decoded)
                        i = semi + 1
                        continue
                    }
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    fun escape(text: String): String = buildString(text.length) {
        for (c in text) when (c) {
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '&' -> append("&amp;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> append(c)
        }
    }
}
