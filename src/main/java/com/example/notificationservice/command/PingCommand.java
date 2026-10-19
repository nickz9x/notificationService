package com.example.notificationservice.command;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class PingCommand extends ListenerAdapter {

    @Override
    public void onSlashCommandInteraction(
            SlashCommandInteractionEvent event
    ) {
        if (!event.getName().equals("ping")) {
            return;
        }

        event.reply("Chain Tracker está online! 🟢")
                .setEphemeral(true)
                .queue();
    }
}