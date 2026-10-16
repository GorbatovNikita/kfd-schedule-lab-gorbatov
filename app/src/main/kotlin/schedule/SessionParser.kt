package schedule

import java.time.LocalDate
import java.time.format.DateTimeParseException

sealed interface ParseResult {
    data class Accepted(val session: Session) : ParseResult
    data class Rejected(val lineNumber: Long, val reason: ParseReason) : ParseResult
}

sealed interface ParseReason {
    data object InvalidFieldSeparator : ParseReason
    data class WrongFieldCount(val actual: Int) : ParseReason
    data class EmptyRequiredField(val field: String) : ParseReason
    data class InvalidDate(val value: String) : ParseReason
    data class UnknownSessionType(val value: String) : ParseReason
    data class TooFewExamTeachers(val count: Int) : ParseReason
    data class ControlCharacter(val field: String, val value: String) : ParseReason
    data class OuterWhitespace(val field: String, val value: String) : ParseReason
    data class InvalidCancelledField(val field: String, val value: String) : ParseReason
    data class InvalidLocation(val value: String) : ParseReason
    data class InvalidTeacher(val value: String) : ParseReason
    data class DuplicateTeacher(val value: String) : ParseReason
}

private val fieldNames = listOf("date", "type", "topic", "location", "teachers")

fun parseSessionLine(lineNumber: Long, line: String): ParseResult {
    if ('|' !in line) return ParseResult.Rejected(lineNumber, ParseReason.InvalidFieldSeparator)
    val fields = line.split('|')
    if (fields.size != 5) {
        return ParseResult.Rejected(lineNumber, ParseReason.WrongFieldCount(fields.size))
    }
    fields.forEachIndexed { index, value ->
        if (value.isBlank()) {
            return ParseResult.Rejected(lineNumber, ParseReason.EmptyRequiredField(fieldNames[index]))
        }
    }

    val (dateText, typeText, topic, location, teachersText) = fields
    if (!Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(dateText)) {
        return ParseResult.Rejected(lineNumber, ParseReason.InvalidDate(dateText))
    }
    val date = try {
        LocalDate.parse(dateText)
    } catch (_: DateTimeParseException) {
        return ParseResult.Rejected(lineNumber, ParseReason.InvalidDate(dateText))
    }
    if (date.year !in 1..9999) return ParseResult.Rejected(lineNumber, ParseReason.InvalidDate(dateText))

    val type = SessionType.entries.firstOrNull { it.name == typeText }
        ?: return ParseResult.Rejected(lineNumber, ParseReason.UnknownSessionType(typeText))
    val teacherNames = teachersText.split(',')
    if (type == SessionType.EXAM) {
        val count = teacherNames.count { it.isNotBlank() && it != "-" }
        if (count < 2) return ParseResult.Rejected(lineNumber, ParseReason.TooFewExamTeachers(count))
    }

    val checkedFields = listOf("topic" to topic, "location" to location)
    checkedFields.forEach { (name, value) ->
        if (value.any(Char::isISOControl)) {
            return ParseResult.Rejected(lineNumber, ParseReason.ControlCharacter(name, value))
        }
    }
    teacherNames.forEach { name ->
        if (name.any(Char::isISOControl)) {
            return ParseResult.Rejected(lineNumber, ParseReason.ControlCharacter("teachers", name))
        }
    }
    checkedFields.forEach { (name, value) ->
        if (value != value.trim()) {
            return ParseResult.Rejected(lineNumber, ParseReason.OuterWhitespace(name, value))
        }
    }
    teacherNames.forEach { name ->
        if (name != name.trim()) {
            return ParseResult.Rejected(lineNumber, ParseReason.OuterWhitespace("teachers", name))
        }
    }

    if (type == SessionType.CANCELLED) {
        if (location != "-") {
            return ParseResult.Rejected(lineNumber, ParseReason.InvalidCancelledField("location", location))
        }
        if (teachersText != "-") {
            return ParseResult.Rejected(lineNumber, ParseReason.InvalidCancelledField("teachers", teachersText))
        }
        return tryAccepted(lineNumber) { Cancelled(date, topic) }
    }

    if (location == "-") {
        return ParseResult.Rejected(lineNumber, ParseReason.InvalidLocation(location))
    }
    teacherNames.forEach { name ->
        if (name.isBlank() || name == "-") {
            return ParseResult.Rejected(lineNumber, ParseReason.InvalidTeacher(name))
        }
    }
    val seen = mutableSetOf<String>()
    teacherNames.forEach { name ->
        if (!seen.add(name)) {
            return ParseResult.Rejected(lineNumber, ParseReason.DuplicateTeacher(name))
        }
    }

    return try {
        val session = when (type) {
            SessionType.LECTURE -> Lecture(date, topic, location, teacherNames)
            SessionType.PRACTICE -> Practice(date, topic, location, teacherNames)
            SessionType.EXAM -> Exam(date, topic, location, teacherNames)
            SessionType.CANCELLED -> error("handled above")
        }
        ParseResult.Accepted(session)
    } catch (_: IllegalArgumentException) {
        // Domain rules are checked above; this is a final guard against model drift.
        ParseResult.Rejected(lineNumber, ParseReason.InvalidLocation(location))
    }
}

private inline fun tryAccepted(lineNumber: Long, factory: () -> Session): ParseResult = try {
    ParseResult.Accepted(factory())
} catch (_: IllegalArgumentException) {
    ParseResult.Rejected(lineNumber, ParseReason.InvalidCancelledField("topic", ""))
}

fun formatParseError(result: ParseResult.Rejected): String {
    val prefix = "line ${result.lineNumber}: "
    val message = when (val reason = result.reason) {
        ParseReason.InvalidFieldSeparator -> "invalid field separator"
        is ParseReason.WrongFieldCount -> "got ${reason.actual} fields, expected 5"
        is ParseReason.EmptyRequiredField -> "empty required field \"${escapeErrorValue(reason.field)}\""
        is ParseReason.InvalidDate -> "invalid date \"${escapeErrorValue(reason.value)}\""
        is ParseReason.UnknownSessionType -> "unknown session type \"${escapeErrorValue(reason.value)}\""
        is ParseReason.TooFewExamTeachers -> "type \"EXAM\" requires at least 2 teachers, got ${reason.count}"
        is ParseReason.ControlCharacter -> "field \"${escapeErrorValue(reason.field)}\" contains a control character in \"${escapeErrorValue(reason.value)}\""
        is ParseReason.OuterWhitespace -> "field \"${escapeErrorValue(reason.field)}\" has leading or trailing whitespace in \"${escapeErrorValue(reason.value)}\""
        is ParseReason.InvalidCancelledField -> "type \"CANCELLED\" requires \"${escapeErrorValue(reason.field)}\" to be \"-\", got \"${escapeErrorValue(reason.value)}\""
        is ParseReason.InvalidLocation -> "invalid location \"${escapeErrorValue(reason.value)}\""
        is ParseReason.InvalidTeacher -> "invalid teacher \"${escapeErrorValue(reason.value)}\""
        is ParseReason.DuplicateTeacher -> "duplicate teacher \"${escapeErrorValue(reason.value)}\""
    }
    return prefix + message
}

private fun escapeErrorValue(value: String): String = buildString {
    value.forEach { char ->
        when {
            char == '\\' -> append("\\\\")
            char == '"' -> append("\\\"")
            char.code < 0x20 || char.code > 0x7e -> append("\\u").append(char.code.toString(16).uppercase().padStart(4, '0'))
            else -> append(char)
        }
    }
}
