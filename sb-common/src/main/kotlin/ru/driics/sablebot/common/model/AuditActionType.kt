package ru.driics.sablebot.common.model

import java.awt.Color

enum class AuditActionType(hex: String? = null) {
    BOT_ADD,
    BOT_LEAVE,
    MEMBER_JOIN("#7DE848"),
    MEMBER_NAME_CHANGE("#7F9BFF"),
    MEMBER_LEAVE("#EAD967"),
    MEMBER_WARN("#FFCA59"),
    MEMBER_BAN("#FF686B"),
    MEMBER_UNBAN("#85EA8A"),
    MEMBER_KICK("#FFA154"),
    MEMBER_MUTE("#FFCA59"),
    MEMBER_UNMUTE("#85EA8A"),
    MESSAGE_DELETE("#FF6D96"),
    MESSAGES_CLEAR("#FF6D96"),
    MESSAGE_EDIT("#60AFFF"),
    VOICE_JOIN("#AD84E8"),
    VOICE_MOVE("#AD84E8"),
    VOICE_LEAVE("#E5ACA0");

    val color = hex?.let { Color.decode(it) }
}