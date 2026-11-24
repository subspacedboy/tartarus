package club.subjugated.overlord_exe.storage

import club.subjugated.overlord_exe.models.ConversationMessage
import club.subjugated.overlord_exe.models.UserProfile
import club.subjugated.overlord_exe.models.UserShareableToken
import org.springframework.data.jpa.repository.JpaRepository

interface UserProfileRepository : JpaRepository<UserProfile, Long> {
    fun findByDid(did: String): UserProfile?
    fun findByHandleIgnoreCase(handle: String): UserProfile?
    fun findByConvoId(convoId: String): UserProfile?
}

interface UserShareableTokenRepository : JpaRepository<UserShareableToken, Long> {
    fun findByUserDidOrderByIsDefaultDescLastUsedAtDescIdDesc(userDid: String): List<UserShareableToken>
    fun findByUserDidAndToken(userDid: String, token: String): UserShareableToken?
    fun findFirstByTokenOrderByLastUsedAtDescIdDesc(token: String): UserShareableToken?
}

interface ConversationMessageRepository : JpaRepository<ConversationMessage, Long> {
    fun findTop20ByConvoIdOrderByCreatedAtDescIdDesc(convoId: String): List<ConversationMessage>
    fun deleteByConvoId(convoId: String)
}
