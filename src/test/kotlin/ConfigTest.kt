package org.example

import org.example.org.example.loadConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File

class ConfigTest {

    @Test
    fun `should load valid config correctly`() {
        val tempFile = File.createTempFile("test_config", ".xml")
        val xmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <config>
                <packageName>curl</packageName>
                <repositoryUrl>https://dl-cdn.alpinelinux.org/alpine/v3.19/main/x86_64</repositoryUrl>
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
            assertEquals("https://dl-cdn.alpinelinux.org/alpine/v3.19/main/x86_64", config.repositoryUrl)
            assertEquals("remote", config.repositoryMode)
            assertEquals("8.14.1-r2", config.packageVersion)
            assertEquals("graph.png", config.outputFileName)
            assertEquals("5", config.maxDepth)

        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `should throw exception when file does not exist`() {
        val exception = assertThrows<IllegalArgumentException> {
            loadConfig("/path/to/unknown/file.xml")
        }

        assertEquals("Файл конфигурации не найден: /path/to/unknown/file.xml", exception.message)
    }
}