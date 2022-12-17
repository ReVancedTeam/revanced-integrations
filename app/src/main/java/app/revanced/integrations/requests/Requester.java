package app.revanced.integrations.requests;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class Requester {
    private Requester() {
    }

    public static HttpURLConnection getConnectionFromRoute(String apiUrl, Route route, String... params) throws IOException {
        String url = apiUrl + route.compile(params).getCompiledRoute();
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod(route.getMethod().name());
        // TODO: change the user agent string
        connection.setRequestProperty("User-agent", System.getProperty("http.agent") + ";vanced");

        return connection;
    }

    /**
     * Parse the {@link HttpURLConnection} and optionally disconnect.
     *
     * @param disconnect should be true, <b>only if other requests to the server are unlikely in the near future</b>
     */
    public static String parseJson(HttpURLConnection connection, boolean disconnect) throws IOException {
        String result = parseInputStreamAndClose(connection.getInputStream(), true);
        if (disconnect) {
            connection.disconnect();
        }
        return result;
    }

    /**
     * Parse, and then close the {@link InputStream}
     *
     * @param stripNewLineCharacters if newline (\n) characters should be stripped from the InputStream
     */
    public static String parseInputStreamAndClose(InputStream inputStream, boolean stripNewLineCharacters) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
                if (!stripNewLineCharacters)
                    jsonBuilder.append("\n");
            }
            return jsonBuilder.toString();
        }
    }

    /**
     * Parse the {@link HttpURLConnection} and optionally disconnect.
     *
     * @param disconnect should be true, <b>only if other requests to the server are unlikely in the near future</b>
     */
    public static String parseErrorJson(HttpURLConnection connection, boolean disconnect) throws IOException {
        String result = parseInputStreamAndClose(connection.getErrorStream(), false);
        if (disconnect) {
            connection.disconnect();
        }
        return result;
    }

    /**
     * Parse the {@link HttpURLConnection} and optionally disconnect.
     *
     * @param disconnect should be true, <b>only if other requests to the server are unlikely in the near future</b>
     */
    public static JSONObject getJSONObject(HttpURLConnection connection, boolean disconnect) throws JSONException, IOException {
        return new JSONObject(parseJson(connection, disconnect));
    }

    /**
     * Parse the {@link HttpURLConnection} and optionally disconnect.
     *
     * @param disconnect should be true, <b>only if other requests to the server are unlikely in the near future</b>
     */
    public static JSONArray getJSONArray(HttpURLConnection connection, boolean disconnect) throws JSONException, IOException  {
        return new JSONArray(parseJson(connection, disconnect));
    }

}