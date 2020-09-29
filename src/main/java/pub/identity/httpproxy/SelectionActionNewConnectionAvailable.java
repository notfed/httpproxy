package pub.identity.httpproxy;

import java.nio.channels.*;

public class SelectionActionNewConnectionAvailable implements SelectionAction {
    private ServerSocketChannel serverSocketChannel;
    private Selector selector;
    private ProxyServer proxyServer;

    public SelectionActionNewConnectionAvailable(ProxyServer proxyServer, ServerSocketChannel serverSocketChannel, Selector selector) {
        this.proxyServer = proxyServer;
        this.serverSocketChannel = serverSocketChannel;
        this.selector = selector;
    }

    public static void Register(ProxyServer proxyServer, ServerSocketChannel serverSocketChannel, Selector selector) throws ClosedChannelException {
        SelectionActionNewConnectionAvailable action = new SelectionActionNewConnectionAvailable(proxyServer, serverSocketChannel, selector);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT, action);
    }

    @Override
    public void Act(SelectionKey selectionKey) {
        System.out.println("NewConnectionAvailable");
        SocketChannel ingressConnection = null;
        try {
            // Accept the new connection
            ingressConnection = serverSocketChannel.accept();
            if (ingressConnection == null)
                return;
            ingressConnection.configureBlocking(false);

            // Allocate a new session this connection
            Session session = new Session(proxyServer, selector, ingressConnection);

            // Register an action to read the HTTP headers whenever this connection is readable
            SelectionActionIngressAvailabilityChanged.Register(session);
        } catch (Exception e) {
            // If we failed to accept the connection, log and move on
            if(ingressConnection!=null)
                try { ingressConnection.close(); } catch(Exception ex) { }
            e.printStackTrace();
        }
    }
}
