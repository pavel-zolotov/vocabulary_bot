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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        return BotConfig.USER;
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
                //send an answer
                AnswerCallbackQuery answerCallbackQuery = new AnswerCallbackQuery();
                answerCallbackQuery.setText("✔ Done");
                answerCallbackQuery.setCallbackQueryId(callbackQuery.getId());
                execute(answerCallbackQuery);

                //edit message's buttons
                EditMessageReplyMarkup replyMarkup = new EditMessageReplyMarkup();
                replyMarkup.setMessageId(callbackQuery.getMessage().getMessageId());
                replyMarkup.setChatId(callbackQuery.getMessage().getChatId());
                execute(replyMarkup);

                //save phrase to DB
                Phrase phrase = new Phrase(callbackQuery.getMessage().getReplyToMessage().getText(),
                        callbackQuery.getMessage().getText());
                savePhrase(phrase, callbackQuery.getFrom().getId());
            }else if(callbackQuery.getData().startsWith(PHRASE_REMOVE_DATA)){
                //send an answer
                AnswerCallbackQuery answerCallbackQuery = new AnswerCallbackQuery();
                answerCallbackQuery.setText("✔ Done");
                answerCallbackQuery.setCallbackQueryId(callbackQuery.getId());
                execute(answerCallbackQuery);

                removePhrase(callbackQuery.getData().split(":")[1], callbackQuery.getFrom().getId());
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
                replyKeyboardMarkup.setOneTimeKeyboard(true);
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
                replyKeyboardMarkup.setOneTimeKeyboard(true);
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
                replyKeyboardMarkup.setOneTimeKeyboard(true);
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
                ArrayList<Phrase> phrases = loadPhrases(msg.getFrom().getId());
                if (phrases == null || phrases.size() == 0) {
                    s.setText("No phrases yet");
                } else {
                    //quick action buttons
                    InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
                    List<List<InlineKeyboardButton>> rows = new ArrayList<>();

                    for (Phrase phrase : phrases) {
                        List<InlineKeyboardButton> row = new ArrayList<>();
                        InlineKeyboardButton button = new InlineKeyboardButton();
                        button.setText("*"+phrase.source+"*\n_"+phrase.translation+"_"); //telegram markdown
                        button.setCallbackData("");

                        InlineKeyboardButton buttonRemove = new InlineKeyboardButton();
                        buttonRemove.setText("❎");
                        buttonRemove.setCallbackData(PHRASE_REMOVE_DATA+":"+phrase.id);
                        row.add(button);
                        rows.add(row);
                    }
                    inlineKeyboardMarkup.setKeyboard(rows);
                    s.setReplyMarkup(inlineKeyboardMarkup);
                    s.setText("Here you are:");
                }
                break;
            default:
                try {
                    String translation = Translator.translate("ru", msg.getText());

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

                    s.setText(translation);
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


    private void savePhrase (Phrase phrase, int userId){
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
            pushedRef.setValue(phrase, (DatabaseError error, DatabaseReference reference) -> {
                if (error != null){
                    error.toException().printStackTrace();
                }
                FirebaseApp.getInstance().delete();
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private ArrayList<Phrase> loadPhrases (int userId){
        try {
            ArrayList<Phrase> results = new ArrayList<>();

            // Fetch the service account key JSON file contents
            FileInputStream serviceAccount = new FileInputStream("vocabulary-bot-firebase-adminsdk-nopvk-fa58da275b.json");

            // Initialize the app with a custom auth variable, limiting the server's access
            Map<String, Object> auth = new HashMap<>();
            auth.put("uid", userId);

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
            ref.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    for (DataSnapshot phrase: dataSnapshot.getChildren()){
                        String id = phrase.getKey();
                        String source = phrase.child("source").getValue().toString();
                        String translation = phrase.child("translation").getValue().toString();
                        String definition = phrase.child("definition").getValue().toString();
                        results.add(new Phrase(id, source, translation, definition));
                        FirebaseApp.getInstance().delete();
                    }
                }

                @Override
                public void onCancelled(DatabaseError error) {
                    error.toException().printStackTrace();
                    FirebaseApp.getInstance().delete();
                }
            });

            return results;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void removePhrase (String phraseId, int userId){
        try {
            // Fetch the service account key JSON file contents
            FileInputStream serviceAccount = new FileInputStream("vocabulary-bot-firebase-adminsdk-nopvk-fa58da275b.json");

            // Initialize the app with a custom auth variable, limiting the server's access
            Map<String, Object> auth = new HashMap<>();
            auth.put("uid", userId);

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
            ref.removeValueAsync();
        } catch (IOException e) {
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
}
