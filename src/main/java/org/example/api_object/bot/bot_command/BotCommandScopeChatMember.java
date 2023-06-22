package org.example.api_object.bot.bot_command;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@EqualsAndHashCode
public class BotCommandScopeChatMember implements BotCommandScope {
    @JsonProperty("type")
    private final String type = "chat_member";

    @JsonProperty("chat_id")
    private String chatId;

    @JsonProperty("user_id")
    private Integer userId;

    private BotCommandScopeChatMember() {
    }
}