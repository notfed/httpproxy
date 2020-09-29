package pub.identity.httpproxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.Iterator;

public class ProxyServer {

    final String expectedConnectAction10;
    final String expectedConnectAction11;
    final String expectedHealthCheckAction10;
    final String expectedHealthCheckAction11;
    static final String responseOk = "HTTP/1.1 200 OK" + "\r\n" + "\r\n";

    final String sourceHostname;
    final int sourcePort;
    final String targetHostname;
    final int targetPort;

    final int maxConnections;
    int curConnections;

    public ProxyServer(String sourceHostname, int sourcePort, String targetHostname, int targetPort, int maxConnections) {
        this.sourceHostname = sourceHostname;
        this.sourcePort = sourcePort;
        this.targetHostname = targetHostname;
        this.targetPort = targetPort;

        this.maxConnections = maxConnections;
        this.curConnections = 0;

        this.expectedConnectAction10 = "CONNECT "+targetHostname+":"+targetPort+" HTTP/1.0";
        this.expectedConnectAction11 = "CONNECT "+targetHostname+":"+targetPort+" HTTP/1.1";
        this.expectedHealthCheckAction10 = "GET /health HTTP/1.0";
        this.expectedHealthCheckAction11 = "GET /health HTTP/1.1";
    }

    public void Run() throws IOException {
        try (Selector selector = Selector.open();
             ServerSocketChannel proxyServerChannel = ServerSocketChannel.open();
        ) {
            // Listen on sourceHostname:sourcePort
            proxyServerChannel.socket().bind(new InetSocketAddress(sourceHostname,sourcePort), maxConnections);
            proxyServerChannel.configureBlocking(false);
            System.out.println("Listening for connections on " + sourceHostname+":"+sourcePort + "...");

            // Register a handler to accept all new connections
            SelectionActionNewConnectionAvailable.Register(this, proxyServerChannel, selector);

            while(true) {
                // Wait for selection event(s)
                selector.select();

                // Loop through all ready event(s)
                Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
                while(keyIterator.hasNext()) {
                    SelectionKey selectionKey = keyIterator.next();

                    SelectionAction action = (SelectionAction) selectionKey.attachment();
                    action.Act(selectionKey);

                    keyIterator.remove();
                }
            }
        }
    }
}
