package org.qweco.vocabularybot;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

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
import org.telegram.telegrambots.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.exceptions.TelegramApiException;
import org.telegram.telegrambots.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.logging.BotLogger;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class Bot extends TelegramLongPollingBot {
    final private static String PHRASE_ADD_DATA = "add_to_vocabulary";
    final private static String PHRASE_REMOVE_DATA = "remove_from_vocabulary";
    final private static String PHRASE_SHOW_COMMAND = "📑 Show vocabulary";
    final private static String SETTINGS_COMMAND = "⚙ Settings";
    final private static String SETTINGS_LANG_COMMAND = "🌐 Language";
    final private static String SETTINGS_ALERT_INTERVAL_COMMAND = "🕐 Alert interval";
    final private static String SETTINGS_ALERT_SCOPE_COMMAND = "📋 Scope of words per alert";
    final private static String SETTINGS_BACK = "⬅ Back";

    public static void main (String[] args){
        ApiContextInitializer.init();
        TelegramBotsApi api = new TelegramBotsApi();
        try {
            api.registerBot(new Bot());
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private Bot(){
        super();

        TimerExecutor.getInstance().startExecutionEveryDayAt(new CustomTimerTask("First day alert", -1) {
            @Override
            public void execute() {
                sendAlerts();
            }
        }, 12, 0, 0);

        TimerExecutor.getInstance().startExecutionEveryDayAt(new CustomTimerTask("Second day alert", -1) {
            @Override
            public void execute() {
                sendAlerts();
            }
        }, 16, 0, 0);

        TimerExecutor.getInstance().startExecutionEveryDayAt(new CustomTimerTask("Third day alert", -1) {
            @Override
            public void execute() {
                sendAlerts();
            }
        }, 20, 0, 0);
    }

    @Override
    public String getBotUsername() {
        return "Vocabulary Bot";
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
            if (callbackQuery.getData().equals(PHRASE_ADD_DATA)) {
                //create an answer
                AnswerCallbackQuery answerCallbackQuery = new AnswerCallbackQuery();

                Phrase phrase;
                if (callbackQuery.getMessage().getText().split("_").length == 3){ //if contains definition
                    phrase = new Phrase(callbackQuery.getMessage().getReplyToMessage().getText(),
                            callbackQuery.getMessage().getText().split("_")[1],
                            callbackQuery.getMessage().getText().split("_")[2]);
                }else {
                    phrase = new Phrase(callbackQuery.getMessage().getReplyToMessage().getText(),
                            callbackQuery.getMessage().getText().split("_")[1]);
                }

                try {
                    //save phrase to DB
                    savePhrase(phrase, callbackQuery.getFrom().getId());

                    //edit message's buttons
                    EditMessageReplyMarkup replyMarkup = new EditMessageReplyMarkup();
                    replyMarkup.setMessageId(callbackQuery.getMessage().getMessageId());
                    replyMarkup.setChatId(callbackQuery.getMessage().getChatId());
                    execute(replyMarkup);

                    answerCallbackQuery.setText("✔ Done");
                }catch (DatabaseConnectionException e){
                    answerCallbackQuery.setText("⚠ Something went wrong. Try again later.");
                }

                //send an answer
                answerCallbackQuery.setCallbackQueryId(callbackQuery.getId());
                execute(answerCallbackQuery);
            }else if(callbackQuery.getData().startsWith(PHRASE_REMOVE_DATA)){
                //create an answer
                AnswerCallbackQuery answerCallbackQuery = new AnswerCallbackQuery();

                try {
                    //remove phrase from DB
                    removePhrase(callbackQuery.getData().split(":")[1], callbackQuery.getFrom().getId());

                    ArrayList<Phrase> phrases = loadPhrases(callbackQuery.getFrom().getId());

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

                    answerCallbackQuery.setText("✔ Done");
                }catch (DatabaseConnectionException e){
                    answerCallbackQuery.setText("⚠ Something went wrong. Try again later.");
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
            case SETTINGS_COMMAND: {
                //quick action buttons
                ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
                replyKeyboardMarkup.setResizeKeyboard(true);
                List<KeyboardRow> keyboard = new ArrayList<>();
                KeyboardRow row1 = new KeyboardRow();
                row1.add(SETTINGS_ALERT_INTERVAL_COMMAND);
                row1.add(SETTINGS_ALERT_SCOPE_COMMAND);
                keyboard.add(row1);
                KeyboardRow row2 = new KeyboardRow();
                row2.add(SETTINGS_LANG_COMMAND);
                row2.add(SETTINGS_BACK);
                keyboard.add(row2);
                replyKeyboardMarkup.setKeyboard(keyboard);
                s.setReplyMarkup(replyKeyboardMarkup);
                s.setText("Change some settings here");
                break;
            }
            case SETTINGS_BACK: {
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
                s.setText("I'm ready!");
                break;
            }
            case PHRASE_SHOW_COMMAND:
                try {
                    ArrayList<Phrase> phrases = loadPhrases(msg.getFrom().getId());
                    s.enableMarkdown(true);
                    s.setText(buildPhrasesListMessageText(phrases));
                    s.setReplyMarkup(buildPhrasesListMessageReplyMarkup(phrases));
                } catch (DatabaseConnectionException e) {
                    s.setText("⚠ Something went wrong. Try again later.");
                }
                break;
            default:
                try {
                    String[] data = Translator.translate("ru", msg.getText());
                    String translation = data[0];
                    String lang = data[1].split("-")[0]; //get the input lang

                    String text = "_"+translation+"_";
                    if (!msg.getText().trim().contains(" ")){ //is one word?
                        String definition = Translator.getDefinitions(lang, msg.getText().trim());
                        text += "\n"+definition;
                    }

                    //quick action buttons
                    InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
                    List<List<InlineKeyboardButton>> rows = new ArrayList<>();
                    List<InlineKeyboardButton> row = new ArrayList<>();
                    InlineKeyboardButton button = new InlineKeyboardButton();
                    button.setText("➕ Add to vocabulary");
                    button.setCallbackData(PHRASE_ADD_DATA);
                    row.add(button);
                    rows.add(row);
                    inlineKeyboardMarkup.setKeyboard(rows);
                    s.setReplyMarkup(inlineKeyboardMarkup);

                    s.setText(text);
                    s.enableMarkdown(true);
                    s.setReplyToMessageId(msg.getMessageId());
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


    private void savePhrase (Phrase phrase, int userId) throws DatabaseConnectionException {
        try {
            // Fetch the service account key JSON file contents
            FileInputStream serviceAccount = new FileInputStream("vocabulary-bot-firebase-adminsdk-nopvk-fa58da275b.json");

            // Initialize the app with a custom auth variable, limiting the server's access
            Map<String, Object> auth = new HashMap<>();
            auth.put("uid", String.valueOf(userId));

            FirebaseOptions options = new FirebaseOptions.Builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .setDatabaseUrl("https://vocabulary-bot.firebaseio.com/")
                    .setDatabaseAuthVariableOverride(auth)
                    .build();
            FirebaseApp.initializeApp(options);

            // The app only has access as defined in the Security Rules
            DatabaseReference ref = FirebaseDatabase
                    .getInstance()
                    .getReference("users").child(String.valueOf(userId)).child("en-ru"); //TODO change lang path

            // Generate a reference to a new location and add some data using push()
            DatabaseReference pushedRef = ref.push();

            CountDownLatch done = new CountDownLatch(1);
            final AtomicBoolean isSucceed = new AtomicBoolean(false);
            pushedRef.setValue(phrase, (DatabaseError error, DatabaseReference reference) -> {
                if (error == null){
                    isSucceed.set(true);
                }else{
                    error.toException().printStackTrace();
                }
                done.countDown();
            });

            done.await();
            FirebaseApp.getInstance().delete();
            if (!isSucceed.get()){
                throw new DatabaseConnectionException();
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private ArrayList<Phrase> loadPhrases (int userId) throws DatabaseConnectionException {
        try {
            ArrayList<Phrase> results = new ArrayList<>();

            // Fetch the service account key JSON file contents
            FileInputStream serviceAccount = new FileInputStream("vocabulary-bot-firebase-adminsdk-nopvk-fa58da275b.json");

            // Initialize the app with a custom auth variable, limiting the server's access
            Map<String, Object> auth = new HashMap<>();
            auth.put("uid", String.valueOf(userId));

            FirebaseOptions options = new FirebaseOptions.Builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .setDatabaseUrl("https://vocabulary-bot.firebaseio.com/")
                    .setDatabaseAuthVariableOverride(auth)
                    .build();
            FirebaseApp.initializeApp(options);

            // The app only has access as defined in the Security Rules
            DatabaseReference ref = FirebaseDatabase
                    .getInstance()
                    .getReference("users").child(String.valueOf(userId)).child("en-ru"); //TODO change lang path

            CountDownLatch done = new CountDownLatch(1);
            final AtomicBoolean isSucceed = new AtomicBoolean(false);
            ref.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    for (DataSnapshot phrase : dataSnapshot.getChildren()) {
                        String id = phrase.getKey();
                        String source = phrase.child("source").getValue().toString();
                        String translation = phrase.child("translation").getValue().toString();
                        if (phrase.hasChild("definition")) {
                            String definition = phrase.child("definition").getValue().toString();
                            results.add(new Phrase(id, source, translation, definition));
                        }else {
                            results.add(new Phrase(id, source, translation, null));
                        }
                    }
                    isSucceed.set(true);
                    done.countDown();
                }

                @Override
                public void onCancelled(DatabaseError error) {
                    error.toException().printStackTrace();
                    done.countDown();
                }
            });
            done.await();
            FirebaseApp.getInstance().delete();
            if (!isSucceed.get()){
                throw new DatabaseConnectionException();
            }
            return results;
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void removePhrase (String phraseId, int userId) throws DatabaseConnectionException {
        try {
            // Fetch the service account key JSON file contents
            FileInputStream serviceAccount = new FileInputStream("vocabulary-bot-firebase-adminsdk-nopvk-fa58da275b.json");

            // Initialize the app with a custom auth variable, limiting the server's access
            Map<String, Object> auth = new HashMap<>();
            auth.put("uid", String.valueOf(userId));

            FirebaseOptions options = new FirebaseOptions.Builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .setDatabaseUrl("https://vocabulary-bot.firebaseio.com/")
                    .setDatabaseAuthVariableOverride(auth)
                    .build();
            FirebaseApp.initializeApp(options);

            // The app only has access as defined in the Security Rules
            DatabaseReference ref = FirebaseDatabase
                    .getInstance()
                    .getReference("users").child(String.valueOf(userId)).child("en-ru").child(phraseId); //TODO change lang path

            CountDownLatch done = new CountDownLatch(1);
            final AtomicBoolean isSucceed = new AtomicBoolean(false);
            ref.removeValue((error, ref1) -> {
                if (error == null){
                    isSucceed.set(true);
                }else{
                    error.toException().printStackTrace();
                }
                done.countDown();
            });

            done.await();
            FirebaseApp.getInstance().delete();
            if (!isSucceed.get()){
                throw new DatabaseConnectionException();
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void sendAlerts() {
        /*List<Phrase> phrases = loadPhrases();
        for (Phrase phrase : sendAlerts) {
            synchronized (Thread.currentThread()) {
                try {
                    Thread.currentThread().wait(35);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            String[] userOptions = DatabaseManager.getInstance().getUserWeatherOptions(weatherAlert.getUserId());
            String weather = WeatherService.getInstance().fetchWeatherAlert(weatherAlert.getCityId(),
                    weatherAlert.getUserId(), userOptions[0], userOptions[1]);
            SendMessage sendMessage = new SendMessage();
            sendMessage.enableMarkdown(true);
            sendMessage.setChatId(String.valueOf(weatherAlert.getUserId()));
            sendMessage.setText(weather);
            try {
                sendMessage(sendMessage);
            } catch (TelegramApiRequestException e) {
                e.printStackTrace();
                if (e.getApiResponse().contains("Can't access the chat") || e.getApiResponse().contains("Bot was blocked by the user")) {
                    DatabaseManager.getInstance().deleteAlertsForUser(weatherAlert.getUserId());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }*/
    }

    private String buildPhrasesListMessageText (ArrayList<Phrase> phrases){
        if (phrases == null || phrases.size() == 0) {
            return "No phrases yet";
        } else {
            StringBuilder stringBuilder = new StringBuilder();
            for (Phrase phrase : phrases) {
                stringBuilder.append(phrases.indexOf(phrase)+1).append(". ")
                        .append("*").append(phrase.source).append("*\n_")
                        .append(phrase.translation).append("_\n\n");
            }

            return "Here you are:\n" + stringBuilder.toString() + "\nDelete phrases using buttons below:";
        }
    }

    private InlineKeyboardMarkup buildPhrasesListMessageReplyMarkup(ArrayList<Phrase> phrases) {
        if (phrases == null || phrases.size() == 0) {
            return new InlineKeyboardMarkup();
        } else {
            InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();

            for (Phrase phrase : phrases) {
                List<InlineKeyboardButton> row = new ArrayList<>();
                InlineKeyboardButton buttonRemove = new InlineKeyboardButton();
                buttonRemove.setText(phrases.indexOf(phrase)+1 + ". ❌");
                buttonRemove.setCallbackData(PHRASE_REMOVE_DATA+":"+phrase.id);
                row.add(buttonRemove);
                rows.add(row);
            }
            inlineKeyboardMarkup.setKeyboard(rows);
            return inlineKeyboardMarkup;
        }
    }

    private class DatabaseConnectionException extends Exception{}
}
