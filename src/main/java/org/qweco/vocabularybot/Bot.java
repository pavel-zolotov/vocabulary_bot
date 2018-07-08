package org.qweco.vocabularybot;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.TelegramBotsApi;
import org.telegram.telegrambots.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.api.methods.send.SendMessage;
import org.telegram.telegrambots.api.objects.CallbackQuery;
import org.telegram.telegrambots.api.objects.Message;
import org.telegram.telegrambots.api.objects.Update;
import org.telegram.telegrambots.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.exceptions.TelegramApiException;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Bot extends TelegramLongPollingBot {
    final private static String PHRASE_ADD_COMMAND = "➕ Add to vocabulary";
    private Phrase lastPhrase;

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
            /*if (update.hasCallbackQuery()) {
                handleIncomingCallbackQuery(update.getCallbackQuery());
            } else if (update.hasMessage() && update.getMessage().hasText() && update.getMessage().isUserMessage()) {
                handleIncomingMessage(update.getMessage());
            }*/
            if (update.hasMessage() && update.getMessage().hasText()){
                handleIncomingMessage(update.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /*private void handleIncomingCallbackQuery(CallbackQuery callbackQuery) {
        try {
            if (callbackQuery.getData().equals(PHRASE_ADD_DATA)) {
                AnswerCallbackQuery answerCallbackQuery = new AnswerCallbackQuery();
                answerCallbackQuery.setCallbackQueryId(callbackQuery.getId());

                execute(answerCallbackQuery);

                savePhrase(callbackQuery.getFrom().getId());
            }
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }*/

    private void handleIncomingMessage(Message msg) {
        SendMessage s = new SendMessage();
        s.setChatId(msg.getChatId());

        try {
            if (msg.getText().equals(PHRASE_ADD_COMMAND)){
                savePhrase(msg.getFrom().getId());
                s.setText("✔ Done");
                s.setReplyMarkup(new ReplyKeyboardMarkup().setSelective(false).setKeyboard(new ArrayList<>()));
            }else {
                String translation = Translator.translate("ru", msg.getText());
                lastPhrase = new Phrase(msg.getText(), translation);
                s.setText(translation);

                //quick action keyboard actions
                ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
                replyKeyboardMarkup.setSelective(true);
                replyKeyboardMarkup.setResizeKeyboard(true);
                replyKeyboardMarkup.setOneTimeKeyboard(true);

                List<KeyboardRow> keyboard = new ArrayList<>();
                KeyboardRow row = new KeyboardRow();
                row.add(PHRASE_ADD_COMMAND); //add to dictionary
                //row.add(""); //edit translation
                keyboard.add(row);
                replyKeyboardMarkup.setKeyboard(keyboard);
                s.setReplyMarkup(replyKeyboardMarkup);
            }

            try {
                execute(s);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void savePhrase (int userId){
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
            pushedRef.setValue(lastPhrase, (DatabaseError error, DatabaseReference reference) -> {
                if (error != null){
                    error.toException().printStackTrace();
                }
                FirebaseApp.getInstance().delete();
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private ArrayList<String> loadPhrases (int userId){
        try {
            ArrayList<String> results = new ArrayList<>();

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
                        String source = phrase.child("source").getValue().toString();
                        String translation = phrase.child("translation").getValue().toString();
                        results.add("*"+source+"*\n_"+translation+"_"); //telegram markdown
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

    private void setPeriodicReminder (){

    }
}
