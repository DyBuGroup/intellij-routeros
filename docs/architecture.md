# Architecture

The plugin is a native IntelliJ Platform language plugin written in Kotlin.

| Package (`dev.dybug.routeros`) | Responsibility |
|--------------------------------|----------------|
| *(root)* | `RosLanguage`, `RosFileType`, `RosIcons`, `RosBundle` |

All user-visible strings live in `src/main/resources/messages/RosBundle.properties`.
