package pub.identity.httpproxy;

import java.nio.ByteBuffer;
import java.nio.channels.*;

public class Session implements AutoCloseable {
    public ProxyServer proxyServer;

    public Selector selector;

    public SocketChannel ingressChannel;
    public SocketChannel egressChannel;

    public ByteBuffer itoeBuffer;
    public ByteBuffer etoiBuffer;

    public SelectionKey selectionKeyIngress;
    public SelectionKey selectionKeyEgress;

    public boolean ingressReadClosed;
    public boolean egressReadClosed;
    public boolean ingressWriteClosed;
    public boolean egressWriteClosed;

    public boolean stillReadingConnectHeader = true;
    public boolean stillWritingResponseConnectHeader = true;

    private boolean closed = false;

    public Session(ProxyServer proxyServer, Selector selector, SocketChannel ingressChannel) throws ClosedChannelException {
        this.proxyServer = proxyServer;
        this.selector = selector;
        this.ingressChannel = ingressChannel;
        itoeBuffer = ByteBuffer.allocate(4096);
        etoiBuffer = ByteBuffer.allocate(4096);
        proxyServer.curConnections++;
        System.out.println("SessionStarted");
    }

    @Override
    public void close() {
        if(!closed) {
            closed = true;
            System.out.println("SessionClosed");
            this.proxyServer.curConnections--;
            try { if(ingressChannel!=null) ingressChannel.close(); } catch(Exception e) { }
            try { if(egressChannel !=null) egressChannel.close();  } catch(Exception e) { }
            try { if(selectionKeyIngress!=null) selectionKeyIngress.cancel(); } catch(Exception e) { }
            try { if(selectionKeyEgress !=null) selectionKeyEgress.cancel();  } catch(Exception e) { }
        }
    }
}
