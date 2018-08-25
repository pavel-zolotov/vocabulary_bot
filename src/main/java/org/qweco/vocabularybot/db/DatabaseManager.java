package org.qweco.vocabularybot.db;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.qweco.vocabularybot.Phrase;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

public class DatabaseManager {
    public static void savePhrase (Phrase phrase, String userId) throws DatabaseConnectionException {
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

    public static ArrayList<Phrase> loadPhrases (String userId) throws DatabaseConnectionException {
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

    public static void removePhrase (String phraseId, String phraseLang, String userId) throws DatabaseConnectionException {
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

    public static HashMap<String, ArrayList<Phrase>> loadAlerts() throws DatabaseConnectionException {
        try {
            FirebaseApp app = initializeFirebase(null, "alerts");

            // The app only has access as defined in the Security Rules
            DatabaseReference ref = FirebaseDatabase
                    .getInstance(app)
                    .getReference("users");

            HashMap<String, ArrayList<Phrase>> usersAlerts = new HashMap<>();

            CountDownLatch done = new CountDownLatch(1);
            final AtomicBoolean isSucceed = new AtomicBoolean(false);
            ref.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    for (DataSnapshot user : dataSnapshot.getChildren()) {
                        ArrayList<Phrase> results = new ArrayList<>();

                        // load the limit
                        final int limit;
                        if (user.hasChild("wordScope")){
                            limit = Integer.valueOf(user.child("wordScope").getValue().toString());
                        }else{
                            limit = 3;
                        }

                        // load all phrases
                        for (DataSnapshot lang : user.getChildren()) {
                            for (DataSnapshot phrase : lang.getChildren()) {
                                results.add(dataSnapshot2Phrase(phrase, lang.getKey()));
                            }
                        }

                        // delete disabled, sort by repeats and delete all over limit
                        results.removeIf((phrase -> !phrase.enabled));
                        results.sort(Comparator.comparingInt(phrase -> phrase.repeats));
                        results.removeIf((phrase -> results.indexOf(phrase) >= limit));

                        // send message for every phrase and increase repeat amount in DB
                        results.forEach(phrase -> {
                            user.child(phrase.lang).child(phrase.id).child("repeats").getRef().setValueAsync(phrase.repeats+1);
                        });

                        usersAlerts.put(user.getKey(), results);
                    }

                    isSucceed.set(true);
                    done.countDown();
                }

                @Override
                public void onCancelled(DatabaseError error) {
                    if (!error.getMessage().contains("Can't access the chat") && !error.getMessage().contains("Bot was blocked by the user")) {
                        error.toException().printStackTrace();
                    }
                    done.countDown();
                }
            });
            done.await();
            app.delete();
            if (!isSucceed.get()){
                throw new DatabaseConnectionException();
            }
            return usersAlerts;
        }catch (IOException | InterruptedException e){
            e.printStackTrace();
            return null;
        }
    }

    public static void learnPhrase (String phraseId, String phraseLang, String userId) throws DatabaseConnectionException {
        try {
            initializeFirebase(userId, null);

            // The app only has access as defined in the Security Rules
            DatabaseReference ref = FirebaseDatabase
                    .getInstance()
                    .getReference("users").child(userId).child(phraseLang).child(phraseId).child("enabled");

            CountDownLatch done = new CountDownLatch(1);
            final AtomicBoolean isSucceed = new AtomicBoolean(false);
            ref.setValue(false, (error, ref1) -> {
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

    public static void setUserTargetLang (String lang, String userId) throws DatabaseConnectionException{
        try {
            initializeFirebase(userId, null);

            // The app only has access as defined in the Security Rules
            DatabaseReference ref = FirebaseDatabase
                    .getInstance()
                    .getReference("users").child(userId).child("targetLang");

            CountDownLatch done = new CountDownLatch(1);
            final AtomicBoolean isSucceed = new AtomicBoolean(false);
            ref.setValue(lang, (error, ref1) -> {
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

    public static String getUserTargetLang (String userId) throws DatabaseConnectionException{
        try {
            initializeFirebase(userId, null);

            // The app only has access as defined in the Security Rules
            DatabaseReference ref = FirebaseDatabase
                    .getInstance()
                    .getReference("users").child(userId).child("targetLang");

            CountDownLatch done = new CountDownLatch(1);
            final AtomicBoolean isSucceed = new AtomicBoolean(false);
            final String[] lang = {""}; //FIXME please, it's so dirty.
            ref.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    if (dataSnapshot.exists()) {
                        lang[0] = dataSnapshot.getValue().toString(); //FIXME Oh, my eyes... Forgive me, I had no escape
                    }
                    isSucceed.set(true);
                    done.countDown();
                }

                @Override
                public void onCancelled(DatabaseError error) {
                    if (!error.getMessage().contains("Can't access the chat") && !error.getMessage().contains("Bot was blocked by the user")) {
                        error.toException().printStackTrace();
                    }
                    done.countDown();
                }
            });

            done.await();
            FirebaseApp.getInstance().delete();
            if (!isSucceed.get()){
                throw new DatabaseConnectionException();
            }
            return lang[0];
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void setUserWordScope (int words, String userId) throws DatabaseConnectionException{
        try {
            initializeFirebase(userId, null);

            // The app only has access as defined in the Security Rules
            DatabaseReference ref = FirebaseDatabase
                    .getInstance()
                    .getReference("users").child(userId).child("wordScope");

            CountDownLatch done = new CountDownLatch(1);
            final AtomicBoolean isSucceed = new AtomicBoolean(false);
            ref.setValue(words, (error, ref1) -> {
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

    private static FirebaseApp initializeFirebase (@Nullable String uid, @Nullable String name) throws IOException {
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

    private static Phrase dataSnapshot2Phrase (DataSnapshot dataSnapshot, String lang){
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

    public static class DatabaseConnectionException extends Exception{}
}
