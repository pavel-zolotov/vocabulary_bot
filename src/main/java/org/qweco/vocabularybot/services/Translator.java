package org.qweco.vocabularybot.services;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONString;
import org.qweco.vocabularybot.BotConfig;
import org.telegram.telegrambots.logging.BotLogger;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;

import javax.net.ssl.HttpsURLConnection;

import static org.qweco.vocabularybot.BotConfig.YANDEX_API_KEY;

public class Translator {
    /*
    @return array of strings: first is translation and second is language
     */
    public static String[] translate(String outputLang, String input) throws IOException {
        String urlStr = "https://translate.yandex.net/api/v1.5/tr.json/translate?key="+YANDEX_API_KEY;
        URL urlObj = new URL(urlStr);
        HttpsURLConnection connection = (HttpsURLConnection)urlObj.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        DataOutputStream dataOutputStream = new DataOutputStream(connection.getOutputStream());
        dataOutputStream.writeBytes("text=" + URLEncoder.encode(input, "UTF-8") + "&lang=" + outputLang);

        // read the output from the server
        String response = IOUtils.toString(connection.getInputStream(), "UTF-8");
        JSONObject jsonResponse = new JSONObject(response);
        String[] result = new String[2];
        result[0] = jsonResponse.getJSONArray("text").getString(0); //translation
        result[1] = jsonResponse.getString("lang"); //lang
        return result;
    }

    public static ArrayList<String> getSupportedLanguages(String UiLang) throws IOException {
        String urlStr = "https://translate.yandex.net/api/v1.5/tr.json/getLangs?key="+YANDEX_API_KEY+"&ui="+UiLang;
        URL urlObj = new URL(urlStr);
        HttpsURLConnection connection = (HttpsURLConnection)urlObj.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);

        // read the output from the server
        String response = IOUtils.toString(connection.getInputStream(), "UTF-8");
        JSONObject jsonResponse = new JSONObject(response);
        ArrayList<String> result = new ArrayList<>();
        jsonResponse.getJSONObject("langs").keys().forEachRemaining(result::add);
        return result;
    }

    public static String getDefinitions(String inputLang, String input){
        try {
            URL url = new URL("https://od-api.oxforddictionaries.com:443/api/v1/entries/" + inputLang + "/" + input.toLowerCase());
            HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
            urlConnection.setRequestProperty("Accept","application/json");
            urlConnection.setRequestProperty("app_id", BotConfig.OXFORD_APP_ID);
            urlConnection.setRequestProperty("app_key", BotConfig.OXFORD_APP_KEY);

            StringBuilder builder = new StringBuilder();

            // read the output from the server
            String response = IOUtils.toString(urlConnection.getInputStream(), "UTF-8");
            JSONObject jsonResponse = new JSONObject(response);
            JSONArray lexicalEntries = jsonResponse.getJSONArray("results").getJSONObject(0).getJSONArray("lexicalEntries");
            for (int i = 0; i < lexicalEntries.length(); i++){
                JSONArray entries = lexicalEntries.getJSONObject(i).getJSONArray("entries");
                String lexicalCategory = lexicalEntries.getJSONObject(i).getString("lexicalCategory");
                builder.append("*"+lexicalCategory+"*\n");
                for (int i1 = 0; i1 < entries.length(); i1++){
                    JSONArray senses = entries.getJSONObject(i1).getJSONArray("senses");
                    for (int i2 = 0; i2 < senses.length(); i2++) {
                        if (senses.getJSONObject(i2).has("definitions")) {
                            String definition = senses.getJSONObject(i2).getJSONArray("definitions").getString(0);
                            builder.append("• " + definition + "\n");
                        }
                    }
                }
            }
            return builder.toString();
        }
        catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }
}
