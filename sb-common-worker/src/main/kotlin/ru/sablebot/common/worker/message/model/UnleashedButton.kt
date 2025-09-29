package ru.sablebot.common.worker.message.model

import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle
import java.util.*

object UnleashedButton {
    // Just a dummy component ID, this SHOULD HOPEFULLY be replaced by a proper ID down the road when used with InteractivityManager
    private const val DO_NOT_USE_THIS_COMPONENT_ID = "DO_NOT_USE_THIS"

    // Just a dummy component url, this SHOULD HOPEFULLY be replaced by a proper URL down the road
    private const val DO_NOT_USE_THIS_LINK_URL =
        "https://google.com/"


    fun of(
        style: ButtonStyle,
        label: String? = null,
        emoji: Emoji? = null
    ): Button {
        // In recent JDA updates, JDA trips a check if the label && emoji are empty
        // This is bad for us, because we use this as a builder and, in some things, we set the emoji after the button is created, which
        // completely borks out any buttons that have an empty label + button
        //
        // To work around this, we set a " " label to bypass the check
        // This MUST be refactored later, because if JDA changes the check again, this WILL break!
        if (style == ButtonStyle.LINK)
            return Button.of(style, DO_NOT_USE_THIS_LINK_URL, label.let { if (it.isNullOrEmpty()) " " else it }, emoji)
        return Button.of(
            style,
            DO_NOT_USE_THIS_COMPONENT_ID + ":" + UUID.randomUUID().toString(),
            label.let { if (it.isNullOrEmpty()) " " else it },
            emoji
        )
    }
}