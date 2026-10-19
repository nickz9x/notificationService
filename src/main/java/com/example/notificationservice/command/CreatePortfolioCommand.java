package com.example.notificationservice.command;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class CreatePortfolioCommand extends ListenerAdapter {
    @Override
    public void onSlashCommandInteraction(
            SlashCommandInteractionEvent event
    ) {
        if (!event.getName().equals("new-portfolio")) {
            return;
        }
        String asString = event.getOption("portfolio´s name").getAsString();

        event.reply("Chain Tracker está online! 🟢")
                .setEphemeral(true)
                .queue();
    }
}
