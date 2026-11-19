package org.example.org.example

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.system.exitProcess

/**
 * Точка входа в приложение.
 * Парсит аргументы командной строки и загружает конфигурацию.
 */
fun main(args: Array<String>) {
    val configPath: String? = args.getOrNull(1)
    if(configPath.isNullOrBlank()) {
        throw IllegalArgumentException("Please provide a config file path")
    }
    var config: Config? = null
    try{
        config = loadConfig(configPath + "/config.xml")
        println(config)
    } catch(e: Exception) {
        println(e.message)
        exitProcess(1)
    }
}

/**
 * Класс, представляющий конфигурацию приложения.
 */
class Config(
    val packageName: String,
    val repositoryUrl: String,
    val repositoryMode: String,
    val packageVersion: String,
    val outputFileName: String,
    val maxDepth: String
){
    override fun toString(): String {
        return """
            packageName = $packageName
            repositoryUrl = $repositoryUrl
            repositoryMode = $repositoryMode
            packageVersion = $packageVersion
            outputFileName = $outputFileName
            maxDepth = $maxDepth
        """.trimIndent()
    }
}

/**
 * Загружает и валидирует конфигурацию из XML-файла.
 *
 * @param filePath Путь к файлу конфигурации.
 * @return Объект [Config] с параметрами.
 * @throws IllegalArgumentException Если файл не найден, отсутствует тег или значение пустое.
 */
fun loadConfig(filePath: String): Config {
    val file = File(filePath)
    if (!file.exists()) {
        throw IllegalArgumentException("Файл конфигурации не найден: $filePath")
    }

    val factory = DocumentBuilderFactory.newInstance()
    val builder = factory.newDocumentBuilder()

    val document = builder.parse(file)

    fun getTagValue(tagName: String): String {
        val nodes = document.getElementsByTagName(tagName)

        if (nodes.length == 0) {
            throw IllegalArgumentException("В конфиге отсутствует обязательный тег <$tagName>")
        }

        val text = nodes.item(0).textContent.trim()

        if (text.isEmpty()) {
            throw IllegalArgumentException("Тег <$tagName> пустой")
        }

        return text
    }

    return Config(
        packageName = getTagValue("packageName"),
        repositoryUrl = getTagValue("repositoryUrl"),
        repositoryMode = getTagValue("repositoryMode"),
        packageVersion = getTagValue("packageVersion"),
        outputFileName = getTagValue("outputFileName"),
        maxDepth = getTagValue("maxDepth")
    )
}