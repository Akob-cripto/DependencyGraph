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