package ru.sablebot.common.service

import net.dv8tion.jda.api.entities.User
import ru.sablebot.common.persistence.entity.LocalUser

interface UserService {
    fun get(user: User): LocalUser?

    fun getById(userId: String): LocalUser?

    fun save(user: LocalUser): LocalUser

    fun isApplicable(user: User): Boolean
}