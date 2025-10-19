package ru.sablebot.common.service.impl

import net.dv8tion.jda.api.entities.User
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.sablebot.common.persistence.entity.LocalUser
import ru.sablebot.common.persistence.repository.LocalUserRepository
import ru.sablebot.common.service.UserService

@Service
class UserServiceImpl : UserService {
    @Autowired
    private lateinit var repository: LocalUserRepository

    @Transactional(readOnly = true)
    override fun get(user: User): LocalUser? = getById(user.id)

    @Transactional(readOnly = true)
    override fun getById(userId: String): LocalUser? = repository.findByUserId(userId)

    @Transactional
    override fun save(user: LocalUser): LocalUser = repository.save(user)

    override fun isApplicable(user: User): Boolean = !user.isBot
}