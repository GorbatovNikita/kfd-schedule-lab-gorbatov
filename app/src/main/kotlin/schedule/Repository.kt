package schedule

import java.util.Collections

interface SessionRepository {
    fun add(session: Session)
    fun addAll(sessions: List<Session>)
    fun all(): List<Session>
}

class InMemorySessionRepository : SessionRepository {
    private val sessions = mutableListOf<Session>()

    override fun add(session: Session) { sessions.add(session) }
    override fun addAll(sessions: List<Session>) { this.sessions.addAll(sessions) }
    override fun all(): List<Session> = Collections.unmodifiableList(ArrayList(sessions))
}

data class AuditEvent(val operation: String, val count: Int)

class AuditedSessionRepository(
    private val delegate: SessionRepository = InMemorySessionRepository(),
) : SessionRepository by delegate {
    private val mutableJournal = mutableListOf<AuditEvent>()
    val journal: List<AuditEvent> get() = Collections.unmodifiableList(ArrayList(mutableJournal))
    val addedCount: Int get() = mutableJournal.sumOf(AuditEvent::count)

    override fun add(session: Session) {
        delegate.add(session)
        mutableJournal.add(AuditEvent("add", 1))
    }

    override fun addAll(sessions: List<Session>) {
        delegate.addAll(sessions)
        mutableJournal.add(AuditEvent("addAll", sessions.size))
    }
}
