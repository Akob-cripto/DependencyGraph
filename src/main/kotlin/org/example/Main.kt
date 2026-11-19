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

    try{
        val config = loadConfig("$configPath/config.xml")
        println(config)

        if (config.repositoryMode == "local") {
            println("\n--- Режим тестирования: Построение графа ---")

            val testRepoPath = "$configPath/test-repo.txt"
            val depth = config.maxDepth.toIntOrNull() ?: 5

            val graph = buildDependencyGraph(
                rootPackage = config.packageName, // Например "A"
                maxDepth = depth,
                repositoryMode = config.repositoryMode,
                indexFilePath = testRepoPath
            )

            for ((pkg, deps) in graph) {
                println("$pkg -> ${if (deps.isEmpty()) "[нет зависимостей]" else deps.joinToString(", ")}")
            }
        } else if (config.repositoryMode == "remote") {
            println("\n--- Этап 2: Поиск прямых зависимостей (remote) ---")

            val indexPath = "$configPath/APKINDEX"

            val dependencies = findDependencies(
                filePath = indexPath,
                targetPackage = config.packageName,
                targetVersion = config.packageVersion
            )

            if (dependencies.isEmpty()) {
                println("Зависимостей нет.")
            } else {
                println("Прямые зависимости для '${config.packageName}':")
                dependencies.forEach { println(" - $it") }
            }
        }
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

/**
 * Строит граф зависимостей с помощью BFS (обход в ширину) с рекурсией.
 *
 * @param rootPackage Начальный пакет.
 * @param maxDepth Максимальная глубина анализа.
 * @param repositoryMode Режим работы ("local" или "remote").
 * @param indexFilePath Путь к файлу тестового репозитория (для local режима).
 * @return Map, где ключ - имя пакета, значение - список его прямых зависимостей.
 */
fun buildDependencyGraph(
    rootPackage: String,
    maxDepth: Int,
    repositoryMode: String,
    indexFilePath: String
): Map<String, List<String>> {

    val graph = mutableMapOf<String, List<String>>()

    val visited = mutableSetOf<String>()

    fun bfsRecursive(currentPackage: String, currentDepth: Int) {
        if (currentDepth > maxDepth) return // Достигли максимальной глубины
        if (visited.contains(currentPackage)) return // Пакет уже посещали (цикл)

        visited.add(currentPackage)

        val dependencies = if (repositoryMode == "local") {
            getLocalDependencies(indexFilePath, currentPackage)
        } else {
            emptyList()
        }

        graph[currentPackage] = dependencies

        for (dep in dependencies) {
            bfsRecursive(dep, currentDepth + 1)
        }
    }

    bfsRecursive(rootPackage, 0)

    return graph
}

/**
 * Читает локальный файл тестового репозитория и возвращает зависимости пакета.
 * Формат строки: "A: B, C"
 */
fun getLocalDependencies(filePath: String, targetPackage: String): List<String> {
    val file = File(filePath)
    if (!file.exists()) throw IllegalArgumentException("Файл тестового репозитория не найден: $filePath")

    val lines = file.readLines()
    for (line in lines) {
        val parts = line.split(":")
        if (parts.size >= 2 && parts[0].trim() == targetPackage) {
            val depsString = parts[1].trim()
            return if (depsString.isEmpty()) emptyList() else depsString.split(",").map { it.trim() }
        }
    }
    return emptyList()
}