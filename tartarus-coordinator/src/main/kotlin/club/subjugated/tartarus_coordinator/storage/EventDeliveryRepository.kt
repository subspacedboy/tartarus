package club.subjugated.tartarus_coordinator.storage

import club.subjugated.tartarus_coordinator.models.EventDelivery
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface EventDeliveryRepository : JpaRepository<EventDelivery, Long> {
    fun findByEventIdAndBotId(eventId: Long, botId: Long): EventDelivery?

    @Query(
        "SELECT ed FROM EventDelivery ed " +
            "JOIN FETCH ed.event e " +
            "JOIN ed.bot b " +
            "JOIN ed.contract c " +
            "WHERE b.name = :botName " +
            "AND c.name = :contractName " +
            "AND e.counter > :sinceCounter " +
            "ORDER BY e.counter ASC"
    )
    fun findMissedEventsForBot(
        @Param("botName") botName: String,
        @Param("contractName") contractName: String,
        @Param("sinceCounter") sinceCounter: Int,
    ): List<EventDelivery>

    @Query(
        "SELECT ed FROM EventDelivery ed " +
            "JOIN FETCH ed.event e " +
            "JOIN ed.bot b " +
            "WHERE b.name = :botName " +
            "AND ed.delivered = false " +
            "ORDER BY e.createdAt ASC"
    )
    fun findUndeliveredForBot(@Param("botName") botName: String): List<EventDelivery>
}
