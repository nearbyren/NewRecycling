package nearby.lib.netwrok.gson;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.util.Map;

/**
 * @description: -
 * @since: 1.0.0
 */
public class GsonParser {

    public static Gson optimize() {
        return new GsonBuilder()
                // 遇到Map类型时，使用我们自己的Adapter
                .registerTypeAdapter(
                        new TypeToken<Map<String, Object>>() {
                        }.getType(),
                        new MapFixTypeAdapter())
                .create();
    }
}
