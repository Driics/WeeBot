package ru.sablebot.common.worker.command.model.dsl

interface ApplicationCommandDeclarationWrapper

interface SlashCommandDeclarationWrapper : ApplicationCommandDeclarationWrapper {
    fun command(): SlashCommandDeclarationBuilder
}

/*
TODO
interface UserCommandDeclarationWrapper : ApplicationCommandDeclarationWrapper {
    fun command(): UserCommandDeclarationBuilder
}*/
