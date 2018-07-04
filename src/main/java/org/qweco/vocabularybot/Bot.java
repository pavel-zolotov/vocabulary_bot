package org.qweco.vocabularybot;

import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.TelegramBotsApi;
import org.telegram.telegrambots.api.methods.send.SendMessage;
import org.telegram.telegrambots.api.objects.Message;
import org.telegram.telegrambots.api.objects.Update;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.exceptions.TelegramApiException;

import java.io.IOException;

public class Bot extends TelegramLongPollingBot {
    public static void main (String[] args){
        ApiContextInitializer.init();
        TelegramBotsApi api = new TelegramBotsApi();
        try {
            api.registerBot(new Bot());
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage() && update.getMessage().hasText()) {
                handleIncomingMessage(update.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getBotUsername() {
        return BotConfig.USER;
    }

    @Override
    public String getBotToken() {
        return BotConfig.TOKEN;
    }

    @SuppressWarnings("deprecation")
    private void handleIncomingMessage(Message msg) {
        SendMessage s = new SendMessage();
        s.setChatId(msg.getChatId());
        try {
            s.setText(Translator.translate("ru", msg.getText()));
            try {
                sendMessage(s);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
