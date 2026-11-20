package org.example

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.system.exitProcess

const val MIN_PARTS_FOR_PACKAGE = 2
const val PACKAGE_INDEX = 0

/**
 * Точка входа в приложение.
 * Парсит аргументы командной строки и загружает конфигурацию.
 */
fun main(args: Array<String>) {
    val configPath: String? = args.getOrNull(1)
    if (configPath.isNullOrBlank()) {
        throw IllegalArgumentException("Please provide a config file path")
    }
    try {
        val config = loadConfig("$configPath/config.xml")
        println(config)

        if (config.repositoryMode == "local") {
            handleLocalMode(config, configPath)
        } else if (config.repositoryMode == "remote") {
            handleRemoteMode(config, configPath)
        }
    } catch (e: Exception) {
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
) {
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

    var currentPackage = ""
    var currentVersion = ""
    var inTargetBlock = false

    for (line in file.readLines()) {
        if (line.isBlank()) {
            inTargetBlock = false
            continue
        }
        if (line.startsWith("P:")) {
            currentPackage = line.substring(2).trim()
            inTargetBlock = (currentPackage == targetPackage)
        } else if (line.startsWith("V:")) {
            currentVersion = line.substring(2).trim()
            if (inTargetBlock && currentVersion != targetVersion) {
                inTargetBlock = false
            }
        } else if (line.startsWith("D:") && inTargetBlock) {
            val depsString = line.substring(2).trim()
            return if (depsString.isEmpty()) emptyList() else depsString.split(" ").filter { it.isNotBlank() }
        }
    }
    if (inTargetBlock) return emptyList()
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
    rootPackage: String, maxDepth: Int, repositoryMode: String, indexFilePath: String
): Map<String, List<String>> {

    val graph = mutableMapOf<String, List<String>>()

    val visited = mutableSetOf<String>()

    fun bfsRecursive(currentPackage: String, currentDepth: Int) {
        if (currentDepth > maxDepth) return
        if (visited.contains(currentPackage)) return

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
        if (parts.size >= MIN_PARTS_FOR_PACKAGE && parts[PACKAGE_INDEX].trim() == targetPackage) {
            val depsString = parts[1].trim()
            return if (depsString.isEmpty()) emptyList() else depsString.split(",").map { it.trim() }
        }
    }
    return emptyList()
}


/**
 * Находит обратные зависимости для заданного пакета.
 * Использует BFS с рекурсией для поиска всех пакетов, которые (прямо или косвенно)
 * зависят от targetPackage.
 *
 * @param graph Уже построенный граф зависимостей.
 * @param targetPackage Пакет, для которого ищем обратные зависимости.
 * @param maxDepth Максимальная глубина поиска.
 * @return Множество имен пакетов, которые зависят от targetPackage.
 */
fun findReverseDependencies(
    graph: Map<String, List<String>>, targetPackage: String, maxDepth: Int
): Set<String> {
    val reverseResult = mutableSetOf<String>()
    val visited = mutableSetOf<String>()

    fun bfsReverse(currentPackage: String, currentDepth: Int) {
        if (currentDepth > maxDepth) return
        if (visited.contains(currentPackage)) return

        visited.add(currentPackage)

        for ((pkg, deps) in graph) {
            if (deps.contains(currentPackage)) {
                reverseResult.add(pkg)
                bfsReverse(pkg, currentDepth + 1)
            }
        }
    }

    bfsReverse(targetPackage, 0)

    reverseResult.remove(targetPackage)

    return reverseResult
}


/**
 * Генерирует текстовое представление графа на языке PlantUML.
 */
fun generatePlantUml(graph: Map<String, List<String>>): String {
    val sb = StringBuilder()
    sb.appendLine("@startuml")
    sb.appendLine("skinparam linetype ortho")

    for ((pkg, deps) in graph) {
        if (deps.isEmpty()) {
            sb.appendLine("class $pkg")
        } else {
            for (dep in deps) {
                sb.appendLine("$pkg --> $dep")
            }
        }
    }
    sb.appendLine("@enduml")
    return sb.toString()
}


/**
 * Сохраняет PlantUML в файл и пытается сгенерировать SVG с помощью plantuml.jar.
 */
fun saveAndRenderGraph(plantUmlText: String, outputFileName: String) {
    val baseName = outputFileName.substringBeforeLast(".")
    val pumlFile = File("$baseName.puml")
    pumlFile.writeText(plantUmlText)
    println("Текстовое представление сохранено в: ${pumlFile.absolutePath}")

    val plantUmlJar = File("plantuml.jar")
    if (plantUmlJar.exists()) {
        println("Генерация SVG...")
        try {
            val process = ProcessBuilder(
                "java", "-jar", plantUmlJar.absolutePath, "-tsvg", pumlFile.absolutePath
            ).start()
            process.waitFor()
            println("SVG сохранен в: ${File("$baseName.svg").absolutePath}")
        } catch (e: Exception) {
            println("Ошибка при вызове plantuml.jar: ${e.message}")
        }
    } else {
        println("Внимание: plantuml.jar не найден в корне проекта. Пропускаем генерацию SVG.")
        println("Вы можете сгенерировать картинку вручную на сайте plantuml.com, вставив туда текст из ${pumlFile.name}")
    }
}

/**
 * Обрабатывает локальный режим работы приложения.
 * Строит граф зависимостей, выводит его в консоль, анализирует обратные зависимости
 * и сохраняет результат в файл.
 *
 * @param config Объект конфигурации с настройками.
 * @param configPath Путь к директории, где находятся файлы конфигурации и тестовый репозиторий.
 */
private fun handleLocalMode(config: Config, configPath: String) {
    println("\n--- Режим тестирования: Построение графа ---")
    val testRepoPath = "$configPath/test-repo.txt"
    val depth = config.maxDepth.toIntOrNull() ?: 5
    val graph = buildDependencyGraph(
        rootPackage = config.packageName,
        maxDepth = depth,
        repositoryMode = config.repositoryMode,
        indexFilePath = testRepoPath
    )
    printGraph(graph)
    analyzeReverseDependencies(graph, depth)
    generateAndSaveOutput(graph, config.outputFileName)
}

/**
 * Выводит в консоль текстовое представление графа зависимостей.
 * Для каждого пакета отображает его прямые зависимости.
 *
 * @param graph Map, представляющая граф зависимостей, где ключ — имя пакета, а значение — список его зависимостей.
 */
private fun printGraph(graph: Map<String, List<String>>) {
    for ((pkg, deps) in graph) {
        val depString = if (deps.isEmpty()) "[нет зависимостей]" else deps.joinToString(", ")
        println("$pkg -> $depString")
    }
}

/**
 * Анализирует и выводит в консоль обратные зависимости (кто зависит от заданных пакетов).
 * Использует список тестовых пакетов для демонстрации работы.
 *
 * @param graph Уже построенный граф зависимостей.
 * @param depth Максимальная глубина поиска обратных зависимостей.
 */
private fun analyzeReverseDependencies(graph: Map<String, List<String>>, depth: Int) {
    println("\n--- Этап 4: Обратные зависимости ---")
    val testPackages = listOf("D", "G", "A")
    for (target in testPackages) {
        val reverseDeps = findReverseDependencies(graph, target, depth)
        if (reverseDeps.isEmpty()) {
            println("От '$target' никто не зависит.")
        } else {
            println("От '$target' зависят: ${reverseDeps.joinToString(", ")}")
        }
    }
}

/**
 * Генерирует текстовое представление графа в формате PlantUML,
 * выводит его в консоль и передает на сохранение в файл.
 *
 * @param graph Граф зависимостей.
 * @param outputFileName Имя файла для сохранения результата.
 */
private fun generateAndSaveOutput(graph: Map<String, List<String>>, outputFileName: String) {
    println("\n--- Этап 5: Генерация PlantUML ---")
    val pumlText = generatePlantUml(graph)
    println(pumlText)
    saveAndRenderGraph(pumlText, outputFileName)
}

/**
 * Обрабатывает удаленный режим работы приложения.
 * Ищет прямые зависимости для заданного пакета и версии в файле индекса (APKINDEX)
 * и выводит их в консоль.
 *
 * @param config Объект конфигурации с настройками.
 * @param configPath Путь к директории, где находится файл индекса (APKINDEX).
 */
private fun handleRemoteMode(config: Config, configPath: String) {
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