import org.junit.Assert.*
import org.junit.Test
// import your actual MatatuPage class and dependencies

class MatatuPageTest {
    @Test
    fun testFormatContinueStraightInstruction() {
        // This assumes you have a function to format instructions
        val result = formatInstruction("continue straight", "39m")
        assertEquals("Continue straight for 39m", result)
    }

    @Test
    fun testFormatTurnRightInstruction() {
        val result = formatInstruction("turn right", "50m")
        assertEquals("In 50m turn right", result)
    }
}

// Dummy function for demonstration. Replace with actual implementation or import.
fun formatInstruction(instruction: String, distanceStr: String): String {
    return if (instruction.equals("continue straight", ignoreCase = true)) {
        "Continue straight for $distanceStr"
    } else {
        "In $distanceStr $instruction"
    }
}
