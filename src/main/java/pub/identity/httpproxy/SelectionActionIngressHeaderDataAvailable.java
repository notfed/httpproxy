package pub.identity.httpproxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class SelectionActionIngressHeaderDataAvailable implements SelectionAction {

    private Session session;

    public SelectionActionIngressHeaderDataAvailable(Session session) {
        this.session = session;
    }

    @Override
    public void Act(SelectionKey selectionKey) {
        System.out.println("HeaderBytesAvailable");
        try {
            int r = readSomeFromIngress();
            System.out.println("HeaderBytesRead("+r+")");
            Map<String, String> headers = parseHeaders();
            if (headers != null) {
                clearBuffer();
                session.stillReadingConnectHeader = false;
                if (receivedConnectAction(headers)) {
                    // If we're low on capacity, refuse the connection
                    if(session.proxyServer.curConnections>=session.proxyServer.maxConnections) {
                        System.out.println("ConnectionRefusedDueToCapacity");
                        session.ingressChannel.close();
                        return;
                    }
                    System.out.println("ConnectHeaderReadSuccessfully");
                    beginConnectingToEgress();
                    System.out.println("ConnectionToEgressStarted");
                } else if (receivedGetHealthCheckAction(headers)) {
                    System.out.println("HealthCheckRequested");
                    returnHealthCheck(selectionKey);
                } else {
                    throw new RuntimeException("Unsupported action received: " + headers.get(":ACTION"));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            session.close();
        }
    }

    private boolean receivedGetHealthCheckAction(Map<String, String> headers) {
        return
            session.proxyServer.expectedHealthCheckAction10.equals(headers.getOrDefault(":ACTION",null))
            || session.proxyServer.expectedHealthCheckAction11.equals(headers.getOrDefault(":ACTION",null));
    }

    private void returnHealthCheck(SelectionKey selectionKey) {
        // Calculate how many free connections there are
        int freeConnections = session.proxyServer.maxConnections - session.proxyServer.curConnections;
        // Respond with freeConnections
        String responseBody = "{ freeConnections: " + freeConnections + " }\r\n";
        String responseOk = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/plain; charset=UTF-8\r\n" +
                "Content-Length: " + responseBody.length() + "\r\n" +
                "\r\n" +
                responseBody;
        String responseFail = "HTTP/1.1 204 No Content\r\n" +
                "\r\n";
        if(freeConnections>0)
            session.etoiBuffer.put(ByteBuffer.wrap(responseOk.getBytes()));
        else
            session.etoiBuffer.put(ByteBuffer.wrap(responseFail.getBytes()));
        // Watch for writability to ingress
        session.selectionKeyIngress.interestOps(session.selectionKeyIngress.interestOps()|SelectionKey.OP_WRITE);
        // Close connection after it back
        session.ingressReadClosed = true;
        session.egressReadClosed = true;
        session.egressWriteClosed = true;

    }

    private int readSomeFromIngress() throws IOException {
        return session.ingressChannel.read(session.itoeBuffer);
    }

    private Map<String, String> parseHeaders() {
        ByteBuffer bufferCopy = session.itoeBuffer.asReadOnlyBuffer();
        bufferCopy.flip();
        String request = StandardCharsets.UTF_8.decode(bufferCopy).toString();
        Map<String, String> headers = HttpHeaderParser.ParseHeaders(request);
        return headers;
    }

    private void clearBuffer() {
        session.itoeBuffer.clear(); // TODO: Could we lose data?
    }

    private boolean receivedConnectAction(Map<String,String> headers) {
        String maybeConnectHeader = headers.getOrDefault(":ACTION", null);
        return
                session.proxyServer.expectedConnectAction10.equals(maybeConnectHeader)
                || session.proxyServer.expectedConnectAction11.equals(maybeConnectHeader);
    }

    private void beginConnectingToEgress() throws IOException {
        // Begin connecting to egress
        session.egressChannel = SocketChannel.open();
        session.egressChannel.configureBlocking(false);
        session.egressChannel.connect(new InetSocketAddress(session.proxyServer.targetHostname, session.proxyServer.targetPort));

        // Temporarily stop watching for reads from ingress
        session.selectionKeyIngress.interestOps(0);

        // Register an action to handle when the connection completes
        SelectionActionEgressAvailabilityChanged newAction = new SelectionActionEgressAvailabilityChanged(session);
        session.selectionKeyEgress = session.egressChannel.register(session.selector, SelectionKey.OP_CONNECT, newAction);
    }
}
