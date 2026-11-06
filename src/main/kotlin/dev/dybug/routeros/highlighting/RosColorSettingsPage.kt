package dev.dybug.routeros.highlighting

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import dev.dybug.routeros.RosBundle
import dev.dybug.routeros.RosIcons
import javax.swing.Icon

class RosColorSettingsPage : ColorSettingsPage {
    override fun getIcon(): Icon = RosIcons.FILE

    override fun getHighlighter(): SyntaxHighlighter = RosSyntaxHighlighter()

    override fun getDemoText(): String = DEMO

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey> =
        mapOf("cmd" to RosHighlighterColors.COMMAND, "val" to RosHighlighterColors.VALUE)

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DESCRIPTORS

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    override fun getDisplayName(): String = RosBundle.message("color.settings.name")

    private companion object {
        val DESCRIPTORS = arrayOf(
            descriptor("color.comment", RosHighlighterColors.COMMENT),
            descriptor("color.script.command", RosHighlighterColors.SCRIPT_COMMAND),
            descriptor("color.keyword", RosHighlighterColors.KEYWORD),
            descriptor("color.path", RosHighlighterColors.PATH),
            descriptor("color.slash", RosHighlighterColors.SLASH),
            descriptor("color.command", RosHighlighterColors.COMMAND),
            descriptor("color.property", RosHighlighterColors.PROPERTY),
            descriptor("color.value", RosHighlighterColors.VALUE),
            descriptor("color.string", RosHighlighterColors.STRING),
            descriptor("color.escape", RosHighlighterColors.ESCAPE),
            descriptor("color.invalid.escape", RosHighlighterColors.INVALID_ESCAPE),
            descriptor("color.variable", RosHighlighterColors.VARIABLE),
            descriptor("color.number", RosHighlighterColors.NUMBER),
            descriptor("color.literal", RosHighlighterColors.LITERAL),
            descriptor("color.boolean", RosHighlighterColors.BOOLEAN),
            descriptor("color.operator", RosHighlighterColors.OPERATOR),
            descriptor("color.braces", RosHighlighterColors.BRACES),
            descriptor("color.brackets", RosHighlighterColors.BRACKETS),
            descriptor("color.parentheses", RosHighlighterColors.PARENTHESES),
            descriptor("color.semicolon", RosHighlighterColors.SEMICOLON),
            descriptor("color.comma", RosHighlighterColors.COMMA),
            descriptor("color.bad.character", RosHighlighterColors.BAD_CHARACTER),
        )

        fun descriptor(key: String, attributes: TextAttributesKey) =
            AttributesDescriptor(RosBundle.lazyMessage(key), attributes)

        val DEMO = """
            # 2026-01-01 12:00:00 by RouterOS 7.26
            /interface bridge
            <cmd>add</cmd> name=bridge1 vlan-filtering=yes comment="LAN \"bridge\""
            /ip address
            <cmd>add</cmd> address=192.168.88.1/24 interface=bridge1
            /ip service
            <cmd>set</cmd> <val>www</val> address="${'$'}lanAddress.0/24" port=8080
            /ip firewall filter
            <cmd>add</cmd> action=accept chain=input connection-state=established,related \
                comment="defconf: accept established"

            :local count 0
            :global greet do={ :return ("Hello, " . ${'$'}1) }
            :foreach id in=[/interface find where running && !disabled] do={
                :local name [/interface <cmd>get</cmd> ${'$'}id name]
                :if ([:len ${'$'}name] > 3 and ${'$'}count < 10) do={
                    :set count (${'$'}count + 1)
                    :put "iface=${'$'}name total=${'$'}(${'$'}count * 2)\n"
                } else={ :log warning "skip \q" }
            }
            :delay 1h30m; :put [${'$'}greet "world"]; :put (10.0.0.1 + 1)
        """.trimIndent()
    }
}
