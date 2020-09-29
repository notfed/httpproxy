package pub.identity.httpproxy;

import java.nio.channels.SelectionKey;

public interface SelectionAction {
    void Act(SelectionKey selectionKey);
}
