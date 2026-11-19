package org.example

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
    val response = findDependencies(configPath + "/APKINDEX", config.packageName, config.packageVersion)
    println()
    response.forEach {println(it)}
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

/** Открывает  файл APKINDEX и извлекает прямые зависимости
 * для заданного пакета и версии.
 *
 * @param filePath Путь к текстовому файлу APKINDEX.
 * @param targetPackage Имя искомого пакета (например, "curl").
 * @param targetVersion Искомая версия (например, "8.5.0-r0").
 * @return Список строк с зависимостями. Если зависимостей нет, возвращает пустой список.
 * @throws IllegalArgumentException Если пакет или версия не найдены в файле.
 */
fun findDependencies(filePath: String, targetPackage: String, targetVersion: String): List<String> {
    val file = File(filePath)
    if (!file.exists()) {
        throw IllegalArgumentException("Файл индекса не найден: $filePath")
    }

    val lines = file.readLines()

    var currentPackage = ""
    var currentVersion = ""
    var inTargetBlock = false

    for (line in lines) {
        if (line.isBlank()) {
            inTargetBlock = false
            continue
        }

        when {
            line.startsWith("P:") -> {
                currentPackage = line.substring(2).trim()
                // Если имя совпало, начинаем отслеживать блок
                inTargetBlock = (currentPackage == targetPackage)
            }

            line.startsWith("V:") -> {
                currentVersion = line.substring(2).trim()
                if (inTargetBlock && currentVersion != targetVersion) {
                    inTargetBlock = false
                }
            }

            line.startsWith("D:") -> {
                if (inTargetBlock) {
                    val depsString = line.substring(2).trim()
                    return if (depsString.isEmpty()) {
                        emptyList() // Пакет есть, но зависимостей нет
                    } else {
                        depsString.split(" ").filter { it.isNotBlank() }
                    }
                }
            }
        }
    }

    if (inTargetBlock) {
        return emptyList()
    }

    throw IllegalArgumentException("Пакет '$targetPackage' версии '$targetVersion' не найден в индексе.")
}