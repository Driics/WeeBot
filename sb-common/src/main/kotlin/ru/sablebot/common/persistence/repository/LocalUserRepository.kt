package ru.sablebot.common.persistence.repository

import org.springframework.stereotype.Repository
import ru.sablebot.common.persistence.entity.LocalUser
import ru.sablebot.common.persistence.repository.base.FeaturedUserRepository

@Repository
interface LocalUserRepository : FeaturedUserRepository<LocalUser>