package pub.identity.httpproxy;

import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;

public class SelectionActionIngressAvailabilityChanged implements SelectionAction {
    private Session session;

    public static void Register(Session session) throws ClosedChannelException {
        session.selectionKeyIngress =
                session.ingressChannel.register(session.selector, SelectionKey.OP_READ,
                        new SelectionActionIngressAvailabilityChanged(session));
    }

    public SelectionActionIngressAvailabilityChanged(Session session) {
        this.session = session;
    }

    @Override
    public void Act(SelectionKey selectionKey) {
        try {
            if (selectionKey.isReadable() && session.stillReadingConnectHeader) {
                new SelectionActionIngressHeaderDataAvailable(session).Act(selectionKey);
            } else if (selectionKey.isReadable()) {
                System.out.println("IngressReadable");
                // Start watching for writability to egress
                if(session.selectionKeyEgress != null)
                    session.selectionKeyEgress.interestOps(session.selectionKeyEgress.interestOps() | SelectionKey.OP_WRITE);
                // Read some from ingress into a buffer
                int r = session.ingressChannel.read(session.itoeBuffer);
                // Detect if we've reached EOF
                if(r<=0)
                    session.ingressReadClosed = true;
                // If the read buffer is full, stop watching for readability from ingress
                boolean bufferIsFull = !session.itoeBuffer.hasRemaining();
                if(session.ingressReadClosed || bufferIsFull)
                    session.selectionKeyIngress.interestOps(session.selectionKeyIngress.interestOps() & ~SelectionKey.OP_READ);
                System.out.println("IngressReadBytes("+r+")");
            }
            else if (selectionKey.isWritable()) {
                System.out.println("IngressWritable");
                 // Write some to ingress
                session.etoiBuffer.flip();
                int w = session.ingressChannel.write(session.etoiBuffer);
                session.etoiBuffer.compact();
                // If there's nothing left to write, stop watching for writability to ingress
                int bytesLeftInBuffer = session.etoiBuffer.position();
                if(bytesLeftInBuffer==0)
                    session.selectionKeyIngress.interestOps(session.selectionKeyIngress.interestOps() & ~SelectionKey.OP_WRITE);
                // Start watching for readability from egress
                if(session.selectionKeyEgress!= null && !session.egressReadClosed)
                    session.selectionKeyEgress.interestOps(session.selectionKeyEgress.interestOps() | SelectionKey.OP_READ);
                // If all bytes have been read and written, shutdown ingress
                if(session.egressReadClosed && bytesLeftInBuffer==0) {
                    session.ingressChannel.shutdownOutput();
                    session.ingressWriteClosed = true;
                }
                System.out.println("IngressWroteBytes("+w+")");
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
