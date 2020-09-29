package pub.identity.httpproxy;

import java.io.IOException;

public class Application {
    public static void main(String[] args) throws IOException {
        String sourceHostname = args.length>=1 ? args[0] : "127.0.0.1";
        int sourcePort        = args.length>=2 ? Integer.valueOf(args[1]) : 9000;
        String targetHostname = args.length>=3 ? args[2] : "example.com";
        int targetPort        = args.length>=4 ? Integer.valueOf(args[3]) : 443;
        int maxConnections    = args.length>=5 ? Integer.valueOf(args[4]) : 2000;
        ProxyServer proxyServer = new ProxyServer(sourceHostname, sourcePort, targetHostname, targetPort, maxConnections);
        proxyServer.Run();
    }
}
