package org.qweco.vocabularybot.services;

import org.apache.commons.io.IOUtils;
import org.json.JSONObject;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;

import javax.net.ssl.HttpsURLConnection;

import static org.qweco.vocabularybot.BotConfig.YANDEX_API_KEY;

public class Translator {
    public static String translate(String outputLang, String input) throws IOException {
        String urlStr = "https://translate.yandex.net/api/v1.5/tr.json/translate?key="+YANDEX_API_KEY;
        URL urlObj = new URL(urlStr);
        HttpsURLConnection connection = (HttpsURLConnection)urlObj.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        DataOutputStream dataOutputStream = new DataOutputStream(connection.getOutputStream());
        dataOutputStream.writeBytes("text=" + URLEncoder.encode(input, "UTF-8") + "&lang=" + outputLang);

        String response = IOUtils.toString(connection.getInputStream(), "UTF-8");
        JSONObject jsonResponse = new JSONObject(response);
        return jsonResponse.getJSONArray("text").getString(0);
    }
}
