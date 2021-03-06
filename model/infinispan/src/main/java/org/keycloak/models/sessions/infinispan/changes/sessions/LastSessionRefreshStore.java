/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.keycloak.models.sessions.infinispan.changes.sessions;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.logging.Logger;
import org.keycloak.cluster.ClusterProvider;
import org.keycloak.common.util.Time;
import org.keycloak.models.KeycloakSession;

/**
 * Tracks the queue of lastSessionRefreshes, which were updated on this host. Those will be sent to the second DC in bulk, so second DC can update
 * lastSessionRefreshes on it's side. Message is sent either periodically or if there are lots of stored lastSessionRefreshes.
 *
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class LastSessionRefreshStore {

    protected static final Logger logger = Logger.getLogger(LastSessionRefreshStore.class);

    private final int maxIntervalBetweenMessagesSeconds;
    private final int maxCount;
    private final String eventKey;

    private volatile Map<String, SessionData> lastSessionRefreshes = new ConcurrentHashMap<>();

    private volatile int lastRun = Time.currentTime();


    protected LastSessionRefreshStore(int maxIntervalBetweenMessagesSeconds, int maxCount, String eventKey) {
        this.maxIntervalBetweenMessagesSeconds = maxIntervalBetweenMessagesSeconds;
        this.maxCount = maxCount;
        this.eventKey = eventKey;
    }


    public void putLastSessionRefresh(KeycloakSession kcSession, String sessionId, String realmId, int lastSessionRefresh) {
        lastSessionRefreshes.put(sessionId, new SessionData(realmId, lastSessionRefresh));

        // Assume that lastSessionRefresh is same or close to current time
        checkSendingMessage(kcSession, lastSessionRefresh);
    }


    void checkSendingMessage(KeycloakSession kcSession, int currentTime) {
        if (lastSessionRefreshes.size() >= maxCount || lastRun + maxIntervalBetweenMessagesSeconds <= currentTime) {
            Map<String, SessionData> refreshesToSend = prepareSendingMessage(currentTime);

            // Sending message doesn't need to be synchronized
            if (refreshesToSend != null) {
                sendMessage(kcSession, refreshesToSend);
            }
        }
    }


    // synchronized manipulation with internal object instances. Will return map if message should be sent. Otherwise return null
    private synchronized Map<String, SessionData> prepareSendingMessage(int currentTime) {
        if (lastSessionRefreshes.size() >= maxCount || lastRun + maxIntervalBetweenMessagesSeconds <= currentTime) {
            // Create new map instance, so that new writers will use that one
            Map<String, SessionData> copiedRefreshesToSend = lastSessionRefreshes;
            lastSessionRefreshes = new ConcurrentHashMap<>();
            lastRun = currentTime;

            return copiedRefreshesToSend;
        } else {
            return null;
        }
    }


    protected void sendMessage(KeycloakSession kcSession, Map<String, SessionData> refreshesToSend) {
        LastSessionRefreshEvent event = new LastSessionRefreshEvent(refreshesToSend);

        if (logger.isDebugEnabled()) {
            logger.debugf("Sending lastSessionRefreshes: %s", event.getLastSessionRefreshes().toString());
        }

        // Don't notify local DC about the lastSessionRefreshes. They were processed here already
        ClusterProvider cluster = kcSession.getProvider(ClusterProvider.class);
        cluster.notify(eventKey, event, true, ClusterProvider.DCNotify.ALL_BUT_LOCAL_DC);
    }

}
