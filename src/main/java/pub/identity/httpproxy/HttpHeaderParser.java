package pub.identity.httpproxy;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public class HttpHeaderParser {
    public static Map<String, String> ParseHeaders(String request) {
        if(!request.contains("\r\n\r\n"))
            return null;
        String headerText = request.split("\r\n\r\n",2)[0];
        String[] allHeaders = Arrays.stream(headerText.split("\r\n"))
                .toArray(String[]::new);
        String connectHeader = allHeaders[0];
        Map<String,String> result = Arrays.stream(allHeaders)
                .skip(1)
                .map(line -> line.split(": ", 2))
                .collect(Collectors.toMap(kv -> kv[0], kv -> kv[1]));
        result.put(":ACTION", connectHeader);
        return result;
    }
}
