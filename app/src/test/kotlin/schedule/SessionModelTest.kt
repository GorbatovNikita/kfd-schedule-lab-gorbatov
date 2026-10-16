package schedule

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class SessionModelTest {
    private val date = LocalDate.of(2026, 10, 5)

    @Test
    fun `all four session types can be created`() {
        assertIs<Lecture>(Lecture(date, "Lecture topic", "Room 1", listOf("Ivanov")))
        assertIs<Practice>(Practice(date, "Practice topic", "Room 2", listOf("Petrov")))
        assertIs<Exam>(Exam(date, "Exam topic", "Room 3", listOf("Ivanov", "Petrov")))
        assertIs<Cancelled>(Cancelled(date, "Cancelled topic"))
    }

    @Test
    fun `constructors reject invalid domain values`() {
        assertFailsWith<IllegalArgumentException> { Lecture(date, "  ", "Room 1", listOf("Ivanov")) }
        assertFailsWith<IllegalArgumentException> { Lecture(LocalDate.of(0, 1, 1), "Topic", "Room 1", listOf("Ivanov")) }
        assertFailsWith<IllegalArgumentException> { Practice(date, "Topic", "Room 1", emptyList()) }
        assertFailsWith<IllegalArgumentException> { Exam(date, "Topic", "Room 1", listOf("Ivanov")) }
        assertFailsWith<IllegalArgumentException> { Exam(date, "Topic", "Room 1", listOf("Ivanov", "Ivanov")) }
    }

    @Test
    fun `teacher collection is copied and cannot be mutated through property`() {
        val names = mutableListOf("Ivanov", "Petrov")
        val exam = Exam(date, "Topic", "Room 1", names)
        names.clear()
        assertEquals(listOf("Ivanov", "Petrov"), exam.teachers)
        @Suppress("UNCHECKED_CAST")
        assertFailsWith<UnsupportedOperationException> { (exam.teachers as MutableList<String>).clear() }
    }

    @Test
    fun `repository returns immutable snapshots and audit records each add call`() {
        val repository = AuditedSessionRepository()
        val lecture = Lecture(date, "Topic", "Room 1", listOf("Ivanov"))
        val snapshot = repository.all()
        repository.add(lecture)
        repository.addAll(listOf(lecture, lecture))
        repository.addAll(emptyList())
        assertEquals(0, snapshot.size)
        assertEquals(3, repository.all().size)
        assertEquals(3, repository.addedCount)
        assertEquals(listOf(AuditEvent("add", 1), AuditEvent("addAll", 2), AuditEvent("addAll", 0)), repository.journal)
        @Suppress("UNCHECKED_CAST")
        assertFailsWith<UnsupportedOperationException> { (repository.all() as MutableList<Session>).clear() }
    }
}

class SessionParserTest {
    @Test
    fun `parser prioritizes date error over unknown type`() {
        val result = parseSessionLine(7, "2026-13-45|SEMINAR|Topic|Room 1|Ivanov")
        assertEquals("line 7: invalid date \"2026-13-45\"", formatParseError(assertIs<ParseResult.Rejected>(result)))
    }

    @Test
    fun `parser preserves trailing fields and reports exact field count`() {
        val result = parseSessionLine(2, "2026-10-05|LECTURE|Topic|Room 1|")
        assertEquals("line 2: empty required field \"teachers\"", formatParseError(assertIs<ParseResult.Rejected>(result)))
        val tooMany = parseSessionLine(3, "2026-10-05|LECTURE|Topic|Room 1|Ivanov|extra")
        assertEquals("line 3: got 6 fields, expected 5", formatParseError(assertIs<ParseResult.Rejected>(tooMany)))
    }

    @Test
    fun `duplicate teacher names are rejected`() {
        val result = parseSessionLine(1, "2026-10-05|EXAM|Topic|Room 1|Ivanov,Ivanov")
        assertEquals("line 1: duplicate teacher \"Ivanov\"", formatParseError(assertIs<ParseResult.Rejected>(result)))
    }
}
