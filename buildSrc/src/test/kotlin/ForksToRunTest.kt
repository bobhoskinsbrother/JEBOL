import kotlin.test.Test
import kotlin.test.assertEquals

class ForksToRunTest {

    @Test
    fun `runs what memory affords between the fewest asked and the most wanted`() {
        assertEquals(3, forksToRun(affordable = 3, fewest = 2, wanted = 5))
        assertEquals(5, forksToRun(affordable = 9, fewest = 2, wanted = 5))
        assertEquals(2, forksToRun(affordable = 0, fewest = 2, wanted = 5))
    }

    @Test
    fun `runs the fewest asked when memory wants fewer still, rather than refusing`() {
        assertEquals(2, forksToRun(affordable = 1, fewest = 2, wanted = 1))
        assertEquals(2, forksToRun(affordable = 0, fewest = 2, wanted = 1))
    }

    @Test
    fun `a stage that asks for one runs one when nothing more is affordable`() {
        assertEquals(1, forksToRun(affordable = 0, fewest = 1, wanted = 1))
    }
}
