package ru.sablebot.common.worker.command.model.dsl

interface ApplicationCommandDeclarationWrapper

interface SlashCommandDeclarationWrapper {
    fun command(): SlashCommandDeclarationBuilder
}

/*
TODO
interface UserCommandDeclarationWrapper : ApplicationCommandDeclarationWrapper {
    fun command(): UserCommandDeclarationBuilder
}*/
