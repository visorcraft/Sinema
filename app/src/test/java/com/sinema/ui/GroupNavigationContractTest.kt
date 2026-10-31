package com.sinema.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class GroupNavigationContractTest {
    @Test
    fun `home group cards open their group detail screen`() {
        val source = String(Files.readAllBytes(Paths.get("src/main/java/com/sinema/ui/MainActivity.kt")))
        assertTrue(source.contains("is GroupItem -> startActivity(GroupScenesActivity.intent(requireContext(), item))"))
    }
}
