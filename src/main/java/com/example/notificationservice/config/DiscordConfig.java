package com.example.notificationservice.config;

import com.example.notificationservice.command.PingCommand;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DiscordConfig {

    @Bean
    public JDA jda(
            @Value("${notification.discord.token}") String token
    ) throws InterruptedException {

        JDA jda = JDABuilder.createDefault(token)
                .addEventListeners(new PingCommand())
                .build();

        jda.awaitReady();

        jda.upsertCommand("ping", "Verifica se o bot está online")
                .queue();

        return jda;
    }

}