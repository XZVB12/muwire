package com.muwire.core.messenger

import com.muwire.core.Event

class MessageLoadedEvent extends Event {
    MWMessage message
    int folder
    boolean unread
}
