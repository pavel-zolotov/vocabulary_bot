package org.qweco.vocabularybot;

import org.qweco.vocabularybot.db.DatabaseManager;
import org.qweco.vocabularybot.services.CustomTimerTask;
import org.qweco.vocabularybot.services.TimerExecutor;
import org.qweco.vocabularybot.services.Translator;
import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.TelegramBotsApi;
import org.telegram.telegrambots.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.api.methods.send.SendMessage;
import org.telegram.telegrambots.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.api.objects.CallbackQuery;
import org.telegram.telegrambots.api.objects.Message;
import org.telegram.telegrambots.api.objects.Update;
import org.telegram.telegrambots.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.exceptions.TelegramApiException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class TelegramBot extends TelegramLongPollingBot{
    private static final String PHRASE_ADD_DATA = "add_to_vocabulary";
    private static final String PHRASE_REMOVE_DATA = "remove_from_vocabulary";
    private static final String PHRASE_LEARNED_DATA = "learned";
    private static final String PHRASE_SHOW_COMMAND = "📑 Show vocabulary";
    private static final String SETTINGS_COMMAND = "⚙ Settings";
    private static final String SETTINGS_LANG_COMMAND = "🌐 Target language";
    //private static final String SETTINGS_ALERT_INTERVAL_COMMAND = "🕐 Alert interval";
    private static final String SETTINGS_ALERT_SCOPE_COMMAND = "📋 Words in alert";
    private static final String SETTINGS_ALERT_SCOPE_COMMAND_1 = "📋 1";
    private static final String SETTINGS_ALERT_SCOPE_COMMAND_3 = "📋 3";
    private static final String SETTINGS_ALERT_SCOPE_COMMAND_5 = "📋 5";
    private static final String BACK_TO_MENU_COMMAND = "⬅ Back to menu";
    private static final String BACK_TO_SETTINGS_COMMAND = "⬅ Back to settings";

    private static final String ERROR_MSG = "⚠ Something went wrong. Try again later.";
    private static final String DONE_MSG = "✔ Done";

    public static void main (String[] args){
        ApiContextInitializer.init();
        TelegramBotsApi api = new TelegramBotsApi();
        try {
            api.registerBot(new TelegramBot());
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private TelegramBot(){
        super();

        TimerExecutor.getInstance().test(new CustomTimerTask("First day alert", -1) {
            @Override
            public void execute() {
                sendAlerts();
            }
        });

        TimerExecutor.getInstance().startExecutionEveryDayAt(new CustomTimerTask("First day alert", -1) {
            @Override
            public void execute() {
                sendAlerts();
            }
        }, 12, 0, 0); //TODO resolve timezone issue

        TimerExecutor.getInstance().startExecutionEveryDayAt(new CustomTimerTask("Second day alert", -1) {
            @Override
            public void execute() {
                sendAlerts();
            }
        }, 16, 0, 0); //TODO resolve timezone issue

        TimerExecutor.getInstance().startExecutionEveryDayAt(new CustomTimerTask("Third day alert", -1) {
            @Override
            public void execute() {
                sendAlerts();
            }
        }, 20, 0, 0); //TODO resolve timezone issue
    }

    @Override
    public String getBotUsername() {
        return BotUtils.BOT_NAME;
    }

    @Override
    public String getBotToken() {
        return BotConfig.TOKEN;
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasCallbackQuery()) {
                handleIncomingCallbackQuery(update.getCallbackQuery());
            } else if (update.hasMessage() && update.getMessage().hasText() && update.getMessage().isUserMessage()) {
                handleIncomingMessage(update.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleIncomingCallbackQuery(CallbackQuery callbackQuery) {
        try {
            if (callbackQuery.getData().startsWith(PHRASE_ADD_DATA)) {
                //create an answer
                AnswerCallbackQuery answerCallbackQuery = new AnswerCallbackQuery();

                try {
                    Phrase phrase = BotUtils.makePhrase(callbackQuery.getMessage().getReplyToMessage().getText(),
                            callbackQuery.getMessage().getText(),
                            callbackQuery.getData().split(":")[1]);

                    //save phrase to DB
                    DatabaseManager.savePhrase(phrase, callbackQuery.getFrom().getId().toString());

                    //edit message's buttons
                    EditMessageReplyMarkup replyMarkup = new EditMessageReplyMarkup();
                    replyMarkup.setMessageId(callbackQuery.getMessage().getMessageId());
                    replyMarkup.setChatId(callbackQuery.getMessage().getChatId());
                    execute(replyMarkup);

                    answerCallbackQuery.setText(DONE_MSG);
                }catch (DatabaseManager.DatabaseConnectionException e){
                    answerCallbackQuery.setText(ERROR_MSG);
                }

                //send an answer
                answerCallbackQuery.setCallbackQueryId(callbackQuery.getId());
                execute(answerCallbackQuery);
            }else if(callbackQuery.getData().startsWith(PHRASE_REMOVE_DATA)){
                //create an answer
                AnswerCallbackQuery answerCallbackQuery = new AnswerCallbackQuery();

                try {
                    //remove phrase from DB
                    DatabaseManager.removePhrase(callbackQuery.getData().split(":")[1],
                            callbackQuery.getData().split(":")[2],
                            callbackQuery.getFrom().getId().toString());

                    ArrayList<Phrase> phrases = DatabaseManager.loadPhrases(callbackQuery.getFrom().getId().toString());

                    //edit phrases list
                    EditMessageText messageText = new EditMessageText();
                    messageText.setChatId(callbackQuery.getMessage().getChatId());
                    messageText.setMessageId(callbackQuery.getMessage().getMessageId());
                    messageText.setText(buildPhrasesListMessageText(phrases));
                    messageText.enableMarkdown(true);
                    execute(messageText);

                    //edit message's buttons
                    EditMessageReplyMarkup replyMarkup = new EditMessageReplyMarkup();
                    replyMarkup.setMessageId(callbackQuery.getMessage().getMessageId());
                    replyMarkup.setChatId(callbackQuery.getMessage().getChatId());
                    replyMarkup.setReplyMarkup(buildPhrasesListMessageReplyMarkup(phrases));
                    execute(replyMarkup);

                    answerCallbackQuery.setText(DONE_MSG);
                }catch (DatabaseManager.DatabaseConnectionException e){
                    answerCallbackQuery.setText(ERROR_MSG);
                }

                //send an answer
                answerCallbackQuery.setCallbackQueryId(callbackQuery.getId());
                execute(answerCallbackQuery);
            }else if(callbackQuery.getData().startsWith(PHRASE_LEARNED_DATA)){
                //create an answer
                AnswerCallbackQuery answerCallbackQuery = new AnswerCallbackQuery();

                try {
                    //remove phrase from DB
                    DatabaseManager.learnPhrase(callbackQuery.getData().split(":")[1],
                            callbackQuery.getData().split(":")[2],
                            callbackQuery.getFrom().getId().toString());

                    //edit message's buttons
                    EditMessageReplyMarkup replyMarkup = new EditMessageReplyMarkup();
                    replyMarkup.setMessageId(callbackQuery.getMessage().getMessageId());
                    replyMarkup.setChatId(callbackQuery.getMessage().getChatId());
                    execute(replyMarkup);

                    answerCallbackQuery.setText(DONE_MSG);
                }catch (DatabaseManager.DatabaseConnectionException e){
                    answerCallbackQuery.setText(ERROR_MSG);
                }

                //send an answer
                answerCallbackQuery.setCallbackQueryId(callbackQuery.getId());
                execute(answerCallbackQuery);
            }
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void handleIncomingMessage(Message msg) {
        SendMessage s = new SendMessage();
        s.setChatId(msg.getChatId());

        switch (msg.getText()) {
            case "/start": {
                //quick action buttons
                ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
                replyKeyboardMarkup.setResizeKeyboard(true);
                List<KeyboardRow> keyboard = new ArrayList<>();
                KeyboardRow row = new KeyboardRow();
                row.add(PHRASE_SHOW_COMMAND);
                row.add(SETTINGS_COMMAND);
                keyboard.add(row);
                replyKeyboardMarkup.setKeyboard(keyboard);
                s.setReplyMarkup(replyKeyboardMarkup);

                s.setText("Hi there! Send me a foreign words and I will answer with the translation and definition, when it's appropriate.\n" +
                        "Also you can add words to your personal vocabulary. In this case, I'll remind you about them 3 times a day until you learn." +
                        "It's the default interval value, so you can change it in " + SETTINGS_COMMAND + ". \nI'm ready to start!");
                break;
            }
            case BACK_TO_SETTINGS_COMMAND:
            case SETTINGS_COMMAND: {
                //quick action buttons
                ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
                replyKeyboardMarkup.setResizeKeyboard(true);
                List<KeyboardRow> keyboard = new ArrayList<>();
                KeyboardRow row1 = new KeyboardRow();
                //row1.add(SETTINGS_ALERT_INTERVAL_COMMAND);
                row1.add(SETTINGS_ALERT_SCOPE_COMMAND);
                keyboard.add(row1);
                KeyboardRow row2 = new KeyboardRow();
                row2.add(SETTINGS_LANG_COMMAND);
                row2.add(BACK_TO_MENU_COMMAND);
                keyboard.add(row2);
                replyKeyboardMarkup.setKeyboard(keyboard);
                s.setReplyMarkup(replyKeyboardMarkup);
                s.setText("Change some settings here. Defaults:\n" +
//                        "Alert interval: 4 hours (from 12:00 to 20:00)\n" +
                        "Words in alert: 3\n" +
                        "Target language: ru");
                break;
            }
            case BACK_TO_MENU_COMMAND: {
                //quick action buttons
                s.setReplyMarkup(getDefaultReplyKeyboardMarkup());
                s.setText("I'm ready!");
                break;
            }
            case SETTINGS_LANG_COMMAND: {
                ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
                replyKeyboardMarkup.setResizeKeyboard(true);
                List<KeyboardRow> keyboard = new ArrayList<>();

                try {
                    Translator.getSupportedLanguages("en").forEach((String lang) -> {
                        if (keyboard.size() != 0 && keyboard.get(keyboard.size() - 1).size() < 3) {
                            keyboard.get(keyboard.size() - 1).add(BotUtils.countryCode2EmojiFlag(lang)+ " " + lang);
                        } else {
                            KeyboardRow row = new KeyboardRow();
                            row.add(BotUtils.countryCode2EmojiFlag(lang)+ " " + lang);
                            keyboard.add(row);
                        }
                    });

                    KeyboardRow row = new KeyboardRow();
                    row.add(BACK_TO_SETTINGS_COMMAND);
                    keyboard.add(row);

                    replyKeyboardMarkup.setKeyboard(keyboard);
                    s.setReplyMarkup(replyKeyboardMarkup);
                    s.setText("Select a language:");
                }catch (IOException e){
                    e.printStackTrace();
                }
                break;
            }
            //case SETTINGS_ALERT_INTERVAL_COMMAND:
            case SETTINGS_ALERT_SCOPE_COMMAND: {
                ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
                replyKeyboardMarkup.setResizeKeyboard(true);
                List<KeyboardRow> keyboard = new ArrayList<>();


                KeyboardRow row = new KeyboardRow();
                row.add(SETTINGS_ALERT_SCOPE_COMMAND_1);
                row.add(SETTINGS_ALERT_SCOPE_COMMAND_3);
                row.add(SETTINGS_ALERT_SCOPE_COMMAND_5);
                keyboard.add(row);

                replyKeyboardMarkup.setKeyboard(keyboard);
                s.setReplyMarkup(replyKeyboardMarkup);
                s.setText("Select an amount of words in alert:");
                break;
            }
            case SETTINGS_ALERT_SCOPE_COMMAND_1: {
                buildWordScopeSelectedMessage (1, s, msg.getFrom().getId().toString());
                break;
            }
            case SETTINGS_ALERT_SCOPE_COMMAND_3: {
                buildWordScopeSelectedMessage (3, s, msg.getFrom().getId().toString());
                break;
            }
            case SETTINGS_ALERT_SCOPE_COMMAND_5: {
                buildWordScopeSelectedMessage (5, s, msg.getFrom().getId().toString());
                break;
            }
            case PHRASE_SHOW_COMMAND:
                try {
                    ArrayList<Phrase> phrases = DatabaseManager.loadPhrases(msg.getFrom().getId().toString());
                    s.enableMarkdown(true);
                    s.setText(buildPhrasesListMessageText(phrases));
                    s.setReplyMarkup(buildPhrasesListMessageReplyMarkup(phrases));
                } catch (DatabaseManager.DatabaseConnectionException e) {
                    s.setText(ERROR_MSG);
                }
                break;
            default:
                try {
                    AtomicBoolean isCommand = new AtomicBoolean(false);
                    Translator.getSupportedLanguages("en").forEach((String lang) -> {
                        if (msg.getText().equals(BotUtils.countryCode2EmojiFlag(lang)+ " " + lang)){
                            isCommand.set(true);

                            try {
                                DatabaseManager.setUserTargetLang(lang, msg.getFrom().getId().toString());
                                s.setText(DONE_MSG);
                            } catch (DatabaseManager.DatabaseConnectionException e) {
                                e.printStackTrace();
                                s.setText(ERROR_MSG);
                            }
                            s.setReplyMarkup(getDefaultReplyKeyboardMarkup());
                        }
                    });

                    if (!isCommand.get()) {
                        try {
                            String outputLang = DatabaseManager.getUserTargetLang(msg.getFrom().getId().toString());
                            if (outputLang == null || outputLang.equals("")) {
                                outputLang = "ru";
                            }

                            String[] data = Translator.translate(outputLang, msg.getText());
                            String translation = data[0];
                            String inputLang = data[1].split("-")[0]; //get the input lang

                            if (!msg.getText().trim().contains(" ")) { //is it one word?
                                String definition = Translator.getDefinitions(inputLang, msg.getText().trim());
                                if (!definition.equals("")) { //send definition as separate message
                                    SendMessage sDef = new SendMessage();
                                    sDef.setChatId(msg.getChatId());
                                    sDef.setText(definition);
                                    sDef.enableMarkdown(true);
                                    sDef.setReplyToMessageId(msg.getMessageId());
                                    try {
                                        execute(sDef);
                                    } catch (TelegramApiException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }

                            //quick action buttons
                            InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
                            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
                            List<InlineKeyboardButton> row = new ArrayList<>();
                            InlineKeyboardButton button = new InlineKeyboardButton();
                            button.setText("➕ Add to vocabulary");
                            button.setCallbackData(PHRASE_ADD_DATA + ":" + data[1]);
                            row.add(button);
                            rows.add(row);
                            inlineKeyboardMarkup.setKeyboard(rows);
                            s.setReplyMarkup(inlineKeyboardMarkup);

                            s.setText("_" + translation + "_");
                            s.enableMarkdown(true);
                            s.setReplyToMessageId(msg.getMessageId());
                        }catch (DatabaseManager.DatabaseConnectionException e){
                            e.printStackTrace();
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
        }

        try {
            execute(s);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendAlerts() {
        try {
            HashMap<String, ArrayList<Phrase>> usersAlerts = DatabaseManager.loadAlerts();
            if (usersAlerts != null && usersAlerts.size() != 0){
                usersAlerts.forEach((String userId, ArrayList<Phrase> userAlerts) -> {
                    for (Phrase phrase : userAlerts) {
                        SendMessage sendMessage = new SendMessage();
                        sendMessage.enableMarkdown(true);
                        sendMessage.setChatId(userId);
                        String text = "";
                        if (userAlerts.indexOf(phrase) == 0){
                            text += "🔔 Time to repeat new words:\n";
                        }
                        text += "🔹 *"+phrase.source+"*\n";
                        text += "_"+phrase.translation+"_\n";
                        if (phrase.definition != null){
                            text += phrase.definition + "\n";
                        }
                        sendMessage.setText(text);

                        //quick action buttons
                        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
                        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
                        List<InlineKeyboardButton> row = new ArrayList<>();
                        InlineKeyboardButton button = new InlineKeyboardButton();
                        button.setText("✔ Learned");
                        button.setCallbackData(PHRASE_LEARNED_DATA+":"+phrase.id+":"+phrase.lang);
                        row.add(button);
                        rows.add(row);
                        inlineKeyboardMarkup.setKeyboard(rows);
                        sendMessage.setReplyMarkup(inlineKeyboardMarkup);

                        try {
                            execute(sendMessage);
                        } catch (TelegramApiException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        } catch (DatabaseManager.DatabaseConnectionException e) {
            e.printStackTrace();
        }
    }

    private String buildPhrasesListMessageText (ArrayList<Phrase> phrases){
        if (phrases == null || phrases.size() == 0) {
            return "No phrases yet";
        } else {
            StringBuilder stringBuilder = new StringBuilder();
            for (Phrase phrase : phrases) {
                if (phrases.indexOf(phrase) == 0 || !phrases.get(phrases.indexOf(phrase) - 1).lang.equals(phrase.lang)){
                    String langFrom = phrase.lang.split("-")[0];
                    String langTo = phrase.lang.split("-")[1];
                    stringBuilder.append(BotUtils.countryCode2EmojiFlag(langFrom))
                            .append(" → ")
                            .append(BotUtils.countryCode2EmojiFlag(langTo))
                            .append("\n");
                }

                stringBuilder.append(phrases.indexOf(phrase)+1).append(". ");
                if (!phrase.enabled){
                    stringBuilder.append("✓ ");
                }
                stringBuilder.append("*").append(phrase.source).append("*\n_")
                        .append(phrase.translation).append("_\n\n");
            }

            return "Here you are:\n" + stringBuilder.toString() + "\nDelete phrases using buttons below:";
        }
    }

    private void buildWordScopeSelectedMessage (int amount, SendMessage s, String userId){
        try {
            DatabaseManager.setUserWordScope(amount, userId);
            s.setText(DONE_MSG);
        } catch (DatabaseManager.DatabaseConnectionException e) {
            e.printStackTrace();
            s.setText(ERROR_MSG);
        }
        s.setReplyMarkup(getDefaultReplyKeyboardMarkup());
    }

    private InlineKeyboardMarkup buildPhrasesListMessageReplyMarkup(ArrayList<Phrase> phrases) {
        if (phrases == null || phrases.size() == 0) {
            return new InlineKeyboardMarkup();
        } else {
            InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();

            for (Phrase phrase : phrases) {
                InlineKeyboardButton buttonRemove = new InlineKeyboardButton();
                buttonRemove.setText(phrases.indexOf(phrase)+1 + ". ❌");
                buttonRemove.setCallbackData(PHRASE_REMOVE_DATA+":"+phrase.id+":"+phrase.lang);

                if (rows.size() != 0 && rows.get(rows.size()-1).size() < 3){
                    rows.get(rows.size()-1).add(buttonRemove);
                }else {
                    List<InlineKeyboardButton> row = new ArrayList<>();
                    row.add(buttonRemove);
                    rows.add(row);
                }
            }
            inlineKeyboardMarkup.setKeyboard(rows);
            return inlineKeyboardMarkup;
        }
    }

    private ReplyKeyboardMarkup getDefaultReplyKeyboardMarkup() {
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
        replyKeyboardMarkup.setResizeKeyboard(true);
        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        row.add(PHRASE_SHOW_COMMAND);
        row.add(SETTINGS_COMMAND);
        keyboard.add(row);
        replyKeyboardMarkup.setKeyboard(keyboard);
        return replyKeyboardMarkup;
    }
}
