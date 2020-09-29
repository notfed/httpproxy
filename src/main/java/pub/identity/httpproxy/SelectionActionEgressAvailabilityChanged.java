package pub.identity.httpproxy;

import java.nio.channels.SelectionKey;

public class SelectionActionEgressAvailabilityChanged implements SelectionAction {
    private Session session;

    public SelectionActionEgressAvailabilityChanged(Session session) {
        this.session = session;
    }

    @Override
    public void Act(SelectionKey selectionKey) {
        try {
            if (selectionKey.isConnectable()) {
                System.out.println("EgressConnectionCompleted");
                // Finish connecting to egress
                boolean connectionSuccessful = session.egressChannel.finishConnect();
                if (!connectionSuccessful)
                    throw new RuntimeException("Failed to connect to egress");

                // Send a "200 OK" header back to the client
                session.etoiBuffer.put(ProxyServer.responseOk.getBytes());

                // Start watching for readability for both ingress and egress
                session.selectionKeyEgress.interestOps(SelectionKey.OP_READ);
                session.selectionKeyIngress.interestOps(SelectionKey.OP_READ|SelectionKey.OP_WRITE);
                System.out.println("ConnectionToEgressCompleted");
            } else if (selectionKey.isReadable()) {
                System.out.println("EgressReadable");
                // Start watching for writability to ingress
                session.selectionKeyIngress.interestOps(session.selectionKeyIngress.interestOps() | SelectionKey.OP_WRITE);
                // Read some from egress into a buffer
                int r = session.egressChannel.read(session.etoiBuffer);
                // Detect if we've reached EOF
                if(r<=0)
                    session.egressReadClosed = true;
                // If the read buffer is full, stop watching for readability from egress
                boolean bufferIsFull = !session.etoiBuffer.hasRemaining();
                if(session.egressReadClosed || bufferIsFull)
                    session.selectionKeyEgress.interestOps(session.selectionKeyEgress.interestOps() & ~SelectionKey.OP_READ);
                System.out.println("EgressReadBytes("+r+")");
            } else if (selectionKey.isWritable()) {
                System.out.println("EgressWritable");
                // Write some to egress
                session.itoeBuffer.flip();
                int w = session.egressChannel.write(session.itoeBuffer);
                session.itoeBuffer.compact();
                // If there's nothing left to write, stop watching for writability to egress
                int bytesLeftInBuffer = session.itoeBuffer.position();
                if(bytesLeftInBuffer==0)
                    session.selectionKeyEgress.interestOps(session.selectionKeyEgress.interestOps() & ~SelectionKey.OP_WRITE);
                // Start watching for readability from ingress
                if(!session.ingressReadClosed)
                    session.selectionKeyIngress.interestOps(session.selectionKeyIngress.interestOps() | SelectionKey.OP_READ);
                // If all bytes have been read and written, shutdown egress
                if(session.ingressReadClosed && bytesLeftInBuffer==0) {
                    session.egressChannel.shutdownOutput();
                    session.egressWriteClosed = true;
                }
                System.out.println("EgressWroteBytes("+w+")");
                maybeCloseSession();
            }
        } catch(Exception e) {
            e.printStackTrace();
            session.close();
        }
    }

    private void maybeCloseSession() {
        if(session.egressReadClosed && session.egressWriteClosed && session.ingressReadClosed && session.ingressWriteClosed) {
            session.close();
        }
    }
}
