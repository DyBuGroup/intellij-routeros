package dev.dybug.routeros.formatting

import com.intellij.application.options.CodeStyleAbstractConfigurable
import com.intellij.application.options.CodeStyleAbstractPanel
import com.intellij.application.options.TabbedLanguageCodeStylePanel
import com.intellij.lang.Language
import com.intellij.psi.codeStyle.CodeStyleConfigurable
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizableOptions
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.codeStyle.CustomCodeStyleSettings
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider
import dev.dybug.routeros.RosBundle
import dev.dybug.routeros.RosLanguage

class RosLanguageCodeStyleSettingsProvider : LanguageCodeStyleSettingsProvider() {
    override fun getLanguage(): Language = RosLanguage

    override fun createCustomSettings(settings: CodeStyleSettings): CustomCodeStyleSettings = RosCodeStyleSettings(settings)

    override fun createConfigurable(baseSettings: CodeStyleSettings, modelSettings: CodeStyleSettings): CodeStyleConfigurable =
        object : CodeStyleAbstractConfigurable(baseSettings, modelSettings, RosBundle.message("color.settings.name")) {
            override fun createPanel(settings: CodeStyleSettings): CodeStyleAbstractPanel =
                object : TabbedLanguageCodeStylePanel(RosLanguage, currentSettings, settings) {
                    override fun initTabs(settings: CodeStyleSettings) {
                        addIndentOptionsTab(settings)
                        addSpacesTab(settings)
                        addBlankLinesTab(settings)
                    }
                }
        }

    override fun getConfigurableDisplayName(): String = RosBundle.message("color.settings.name")

    override fun customizeDefaults(commonSettings: CommonCodeStyleSettings, indentOptions: CommonCodeStyleSettings.IndentOptions) {
        indentOptions.INDENT_SIZE = 4
        indentOptions.CONTINUATION_INDENT_SIZE = 4
        indentOptions.TAB_SIZE = 4
        commonSettings.KEEP_BLANK_LINES_IN_CODE = 2
    }

    override fun customizeSettings(consumer: CodeStyleSettingsCustomizable, settingsType: SettingsType) {
        when (settingsType) {
            SettingsType.SPACING_SETTINGS -> {
                consumer.showCustomOption(
                    RosCodeStyleSettings::class.java, "SPACE_AROUND_OPERATORS",
                    RosBundle.message("codestyle.space.around.operators"), CodeStyleSettingsCustomizableOptions.getInstance().SPACES_AROUND_OPERATORS,
                )
                consumer.showCustomOption(
                    RosCodeStyleSettings::class.java, "SPACE_WITHIN_BRACES",
                    RosBundle.message("codestyle.space.within.braces"), CodeStyleSettingsCustomizableOptions.getInstance().SPACES_WITHIN,
                )
            }
            SettingsType.BLANK_LINES_SETTINGS -> consumer.showStandardOptions("KEEP_BLANK_LINES_IN_CODE")
            SettingsType.INDENT_SETTINGS -> consumer.showCustomOption(
                RosCodeStyleSettings::class.java, "INDENT_SECTION_COMMANDS",
                RosBundle.message("codestyle.indent.sections"), null,
            )
            else -> Unit
        }
    }

    override fun getIndentOptionsEditor() = com.intellij.application.options.SmartIndentOptionsEditor()

    override fun getCodeSample(settingsType: SettingsType): String = """
        # 2026-01-01 by RouterOS 7.26
        /interface bridge
        add name=bridge1 vlan-filtering=yes
        /ip address
        add address=192.168.88.1/24 interface=bridge1 comment="LAN"

        :global greet do={
        :local name ${'$'}1
        :if ([:len ${'$'}name]>0) do={:return ("Hello, ".${'$'}name)} else={:return "Hello"}
        }
        :foreach i in=[/interface find] do={ :put [/interface get ${'$'}i name] }
    """.trimIndent()
}
