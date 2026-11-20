package org.example

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File

/**
 * Тесты для проверки корректности загрузки и валидации конфигурации.
 */
class ConfigTest {

    /**
     * Проверяет, что валидный XML-файл конфигурации успешно загружается,
     * и все поля объекта [Config] заполняются правильными значениями.
     */
    @Test
    fun `should load valid config correctly`() {
        val tempFile = File.createTempFile("test_config", ".xml")

        /** Длинный URL, разбитый на части для соблюдения лимита длины строки. */
        val longUrl = "https://dl-cdn.alpinelinux.org/alpine/v3.19/main/x86_64" +
                "/some/very/long/path/to/avoid/long/lines"

        val xmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <config>
                <packageName>curl</packageName>
                <repositoryUrl>$longUrl</repositoryUrl>
                <repositoryMode>remote</repositoryMode>
                <packageVersion>8.14.1-r2</packageVersion>
                <outputFileName>graph.png</outputFileName>
                <maxDepth>5</maxDepth>
            </config>
        """.trimIndent()
        tempFile.writeText(xmlContent)

        try {
            val config = loadConfig(tempFile.absolutePath)

            assertEquals("curl", config.packageName)
            assertEquals(longUrl, config.repositoryUrl)
            assertEquals("remote", config.repositoryMode)
            assertEquals("8.14.1-r2", config.packageVersion)
            assertEquals("graph.png", config.outputFileName)
            assertEquals("5", config.maxDepth)

        } finally {
            tempFile.delete()
        }
    }

    /**
     * Проверяет, что при попытке загрузить несуществующий файл
     * выбрасывается исключение [IllegalArgumentException] с правильным сообщением.
     */
    @Test
    fun `should throw exception when file does not exist`() {
        val invalidPath = "/path/to/unknown/file.xml"
        val expectedMessage = "Файл конфигурации не найден: $invalidPath"

        val exception = assertThrows<IllegalArgumentException> {
            loadConfig(invalidPath)
        }

        assertEquals(expectedMessage, exception.message)
    }
}