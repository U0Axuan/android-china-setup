package io.github.u0axuan.androidchinasetup

import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser
import org.jsoup.Jsoup

/** A safe, readable representation of untrusted upstream Markdown, without a WebView. */
data class ReadmePart(val type: Type, val text: String = "", val depth: Int = 0) {
    enum class Type { HEADING, PARAGRAPH, BULLET, QUOTE, CODE, RULE }
}

object ReadmeDocument {
    private val parser = Parser.builder().build()

    private fun htmlText(raw: String): String {
        val doc = Jsoup.parseBodyFragment(raw)
        doc.select("script,style,iframe,object,embed,svg,img,picture,source,video,form,button,nav").remove()
        return doc.body().text()
    }

    private fun inline(parent: Node): String {
        val output = StringBuilder()
        fun walk(node: Node) {
            when (node) {
                is Text -> output.append(node.literal)
                is Code -> output.append(node.literal)
                is HtmlInline -> {
                    val safe = htmlText(node.literal)
                    if (safe.isNotBlank()) output.append(safe)
                }
                is Image -> Unit // Screenshots, badges and inline media do not become UI text.
                is HardLineBreak -> output.append('\n')
                is SoftLineBreak -> output.append(' ')
                else -> {
                    var child = node.firstChild
                    while (child != null) {
                        walk(child)
                        child = child.next
                    }
                }
            }
        }
        var child = parent.firstChild
        while (child != null) {
            walk(child)
            child = child.next
        }
        return output.toString().replace(Regex("[ \t]{2,}"), " ").trim()
    }

    fun parse(raw: String): List<ReadmePart> {
        if (raw.isBlank()) return emptyList()
        val result = ArrayList<ReadmePart>()
        fun add(type: ReadmePart.Type, text: String = "", depth: Int = 0) {
            if (result.size >= 180 || (text.isBlank() && type != ReadmePart.Type.RULE)) return
            result.add(ReadmePart(type, text.take(8000), depth))
        }
        fun visit(node: Node, listPrefix: String = "", depth: Int = 0) {
            if (result.size >= 180) return
            when (node) {
                is Heading -> add(ReadmePart.Type.HEADING, inline(node), node.level)
                is Paragraph -> add(if (listPrefix.isNotBlank()) ReadmePart.Type.BULLET
                    else ReadmePart.Type.PARAGRAPH, listPrefix + inline(node), depth)
                is HtmlBlock -> add(ReadmePart.Type.PARAGRAPH, htmlText(node.literal))
                is FencedCodeBlock -> add(ReadmePart.Type.CODE, node.literal.trimEnd())
                is IndentedCodeBlock -> add(ReadmePart.Type.CODE, node.literal.trimEnd())
                is BlockQuote -> {
                    var child = node.firstChild
                    while (child != null) {
                        if (child is Paragraph) add(ReadmePart.Type.QUOTE, inline(child))
                        else visit(child, "", depth)
                        child = child.next
                    }
                }
                is BulletList, is OrderedList -> {
                    val numbered = node is OrderedList
                    var index = 1
                    var item = node.firstChild
                    while (item != null) {
                        if (item is ListItem) {
                            val prefix = if (numbered) index.toString() + ". " else "• "
                            var child = item.firstChild
                            while (child != null) {
                                if (child is Paragraph) visit(child, prefix, depth + 1)
                                else visit(child, "", depth + 1)
                                child = child.next
                            }
                        }
                        index++
                        item = item.next
                    }
                }
                is ThematicBreak -> add(ReadmePart.Type.RULE)
                else -> {
                    var child = node.firstChild
                    while (child != null) {
                        visit(child, listPrefix, depth)
                        child = child.next
                    }
                }
            }
        }
        return runCatching {
            var child = parser.parse(raw.take(48000)).firstChild
            while (child != null) {
                visit(child)
                child = child.next
            }
            result.toList()
        }.getOrElse { listOf(ReadmePart(ReadmePart.Type.PARAGRAPH,
            htmlText(raw.take(2000)))) }
    }
}
