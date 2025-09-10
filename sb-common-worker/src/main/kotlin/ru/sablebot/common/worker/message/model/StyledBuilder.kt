package ru.sablebot.common.worker.message.model

import dev.minn.jda.ktx.messages.InlineMessage
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder


fun InlineMessage<*>.styled(contentText: String, prefix: String) {
    val styled = createStyledContent(contentText, prefix)

    if (content != null) {
        content += "\n"
        content += styled
    } else
        content = styled
}

fun MessageCreateBuilder.styled(contentText: String, prefix: String) {
    val styled = createStyledContent(contentText, prefix)

    addContent("\n")
    addContent(styled)
}

fun createStyledContent(content: String, prefix: String) = "$prefix | $content"