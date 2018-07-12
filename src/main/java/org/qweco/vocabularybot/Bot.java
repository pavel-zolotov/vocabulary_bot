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
import org.telegram.telegrambots.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.exceptions.TelegramApiException;
import org.telegram.telegrambots.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.logging.BotLogger;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

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
        TimerExecutor.getInstance().test(new CustomTimerTask("First day alert", -1) {
            @Override
            public void execute() {
                sendAlerts();
            }
        });

        /*TimerExecutor.getInstance().startExecutionEveryDayAt(new CustomTimerTask("First day alert", -1) {
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
        }, 20, 0, 0);*/
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
            if (callbackQuery.getData().startsWith(PHRASE_ADD_DATA)) {
                //create an answer
                AnswerCallbackQuery answerCallbackQuery = new AnswerCallbackQuery();

                String sourceText = callbackQuery.getMessage().getReplyToMessage().getText();
                String fullLang = callbackQuery.getData().split(":")[1];
                String inputLang = fullLang.split("-")[0]; //get the input lang

                Phrase phrase = null;
                if (!sourceText.trim().contains(" ")) { //is one word?
                    String definition = Translator.getDefinitions(inputLang, sourceText.trim());
                    if (!definition.equals("")) {
                        phrase = new Phrase(sourceText,
                                callbackQuery.getMessage().getText(),
                                definition,
                                fullLang, 0, 0, true);
                    }
                }

                if (phrase == null){
                    phrase = new Phrase(callbackQuery.getMessage().getReplyToMessage().getText(),
                            callbackQuery.getMessage().getText(),
                            fullLang, 0, 0, true);
                }

                try {
                    //save phrase to DB
                    savePhrase(phrase, callbackQuery.getFrom().getId().toString());

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
                    removePhrase(callbackQuery.getData().split(":")[1],
                            callbackQuery.getData().split(":")[2],
                            callbackQuery.getFrom().getId().toString());

                    ArrayList<Phrase> phrases = loadPhrases(callbackQuery.getFrom().getId().toString());

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
                //row1.add(SETTINGS_ALERT_INTERVAL_COMMAND); //TODO
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
                    ArrayList<Phrase> phrases = loadPhrases(msg.getFrom().getId().toString());
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

                    if (!msg.getText().trim().contains(" ")){ //is one word?
                        String definition = Translator.getDefinitions(lang, msg.getText().trim());
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
                    button.setCallbackData(PHRASE_ADD_DATA+":"+data[1]);
                    row.add(button);
                    rows.add(row);
                    inlineKeyboardMarkup.setKeyboard(rows);
                    s.setReplyMarkup(inlineKeyboardMarkup);

                    s.setText("_"+translation+"_");
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


    private void savePhrase (Phrase phrase, String userId) throws DatabaseConnectionException {
        try {
            initializeFirebase(userId, null);

            // The app only has access as defined in the Security Rules
            DatabaseReference ref = FirebaseDatabase
                    .getInstance()
                    .getReference("users").child(userId).child(phrase.lang);

            // Generate a reference to a new location and add some data using push()
            DatabaseReference pushedRef = ref.push();

            // delete lang data as it's already presented in path
            phrase.lang = null;

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

    private ArrayList<Phrase> loadPhrases (String userId) throws DatabaseConnectionException {
        try {
            ArrayList<Phrase> results = new ArrayList<>();

            initializeFirebase(userId, null);

            // The app only has access as defined in the Security Rules
            DatabaseReference ref = FirebaseDatabase
                    .getInstance()
                    .getReference("users").child(userId);

            CountDownLatch done = new CountDownLatch(1);
            final AtomicBoolean isSucceed = new AtomicBoolean(false);
            ref.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    for (DataSnapshot lang : dataSnapshot.getChildren()) {
                        for (DataSnapshot phrase : lang.getChildren()) {
                            results.add(dataSnapshot2Phrase(phrase, lang.getKey()));
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

    private void removePhrase (String phraseId, String phraseLang, String userId) throws DatabaseConnectionException {
        try {
            initializeFirebase(userId, null);

            // The app only has access as defined in the Security Rules
            DatabaseReference ref = FirebaseDatabase
                    .getInstance()
                    .getReference("users").child(userId).child(phraseLang).child(phraseId);

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
        BotLogger.warn("test","now");
        try {
            FirebaseApp app = initializeFirebase(null, "alerts");

            // The app only has access as defined in the Security Rules
            DatabaseReference ref = FirebaseDatabase
                    .getInstance(app)
                    .getReference("users");
            BotLogger.warn("test","now2");

            CountDownLatch done = new CountDownLatch(1);
            ref.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    BotLogger.warn("test","now3");
                    for (DataSnapshot user : dataSnapshot.getChildren()) {
                        ArrayList<Phrase> results = new ArrayList<>();
                        int limit = Integer.valueOf(user.child("wordScope").getValue().toString());

                        // load all phrases
                        for (DataSnapshot lang : user.getChildren()) {
                            for (DataSnapshot phrase : lang.getChildren()) {
                                results.add(dataSnapshot2Phrase(phrase, lang.getKey()));
                            }
                        }

                        BotLogger.warn("test", String.valueOf(results.size()));

                        // delete disabled, sort by repeats and delete all over limit
                        results.removeIf((phrase -> !phrase.enabled));
                        results.sort(Comparator.comparingInt(phrase -> phrase.repeats));
                        results.removeIf((phrase -> results.indexOf(phrase) > limit));

                        BotLogger.warn("test", String.valueOf(results.size()));

                        // send message for every phrase and increase repeat amount in DB
                        results.forEach(phrase -> {
                            BotLogger.warn("test", String.valueOf(phrase.id));

                            SendMessage sendMessage = new SendMessage();
                            sendMessage.enableMarkdown(true);
                            sendMessage.setChatId(user.getKey());
                            String text = "Time to repeat new words:\n";
                            text += "• "+phrase.source+"\n";
                            if (phrase.definition != null){
                                text = phrase.definition + "\n\n";
                            }
                            text += "_"+phrase.translation+"_";
                            sendMessage.setText(text);
                            try {
                                execute(sendMessage);
                            } catch (TelegramApiException e) {
                                e.printStackTrace();
                            }
                            BotLogger.warn("test", "done");

                            user.child(phrase.lang).child(phrase.id).child("repeats").getRef().setValueAsync(phrase.repeats+1);
                            BotLogger.warn("test", "done2");
                        });
                    }

                    BotLogger.warn("test", "done all");
                    done.countDown();
                }

                @Override
                public void onCancelled(DatabaseError error) {
                    error.toException().printStackTrace();
                    done.countDown();
                }
            });
            done.await();
            BotLogger.warn("test", "done all2");
            app.delete();
        }catch (IOException | InterruptedException e){
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
                    stringBuilder.append(countryCode2EmojiFlag(langFrom.toUpperCase()))
                            .append(" → ")
                            .append(countryCode2EmojiFlag(langTo.toUpperCase()))
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
                buttonRemove.setCallbackData(PHRASE_REMOVE_DATA+":"+phrase.id+":"+phrase.lang);
                row.add(buttonRemove);
                rows.add(row);
            }
            inlineKeyboardMarkup.setKeyboard(rows);
            return inlineKeyboardMarkup;
        }
    }

    private Phrase dataSnapshot2Phrase (DataSnapshot dataSnapshot, String lang){
        String id = dataSnapshot.getKey();
        String source = dataSnapshot.child("source").getValue().toString();
        String translation = dataSnapshot.child("translation").getValue().toString();
        int repeats = Integer.valueOf(dataSnapshot.child("repeats").getValue().toString());
        int correctAnswers = Integer.valueOf(dataSnapshot.child("correctAnswers").getValue().toString());
        boolean enabled = Boolean.parseBoolean(dataSnapshot.child("enabled").getValue().toString());

        if (dataSnapshot.hasChild("definition")) {
            String definition = dataSnapshot.child("definition").getValue().toString();
            return new Phrase(id, source, translation, definition, lang, repeats, correctAnswers, enabled);
        }else {
            return new Phrase(id, source, translation, null, lang, repeats, correctAnswers, enabled);
        }
    }

    private FirebaseApp initializeFirebase (@Nullable String uid, @Nullable String name) throws IOException {
        // Fetch the service account key JSON file contents
        FileInputStream serviceAccount = new FileInputStream("vocabulary-bot-firebase-adminsdk-nopvk-fa58da275b.json");

        FirebaseOptions.Builder options = new FirebaseOptions.Builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                .setDatabaseUrl("https://vocabulary-bot.firebaseio.com/");

        if (uid != null){
            // Initialize the app with a custom auth variable, limiting the server's access
            Map<String, Object> auth = new HashMap<>();
            auth.put("uid", uid);
            options.setDatabaseAuthVariableOverride(auth);
        }

        if (name != null){
            return FirebaseApp.initializeApp(options.build(), name);
        }else {
            return FirebaseApp.initializeApp(options.build());
        }
    }

    private String countryCode2EmojiFlag (String country){
        int flagOffset = 0x1F1E6;
        int asciiOffset = 0x41;

        int firstChar = Character.codePointAt(country, 0) - asciiOffset + flagOffset;
        int secondChar = Character.codePointAt(country, 1) - asciiOffset + flagOffset;

        return new String(Character.toChars(firstChar)) + new String(Character.toChars(secondChar));
    }

    private class DatabaseConnectionException extends Exception{}
}
