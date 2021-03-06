/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.models.sessions.infinispan.events;

import org.jboss.logging.Logger;
import org.keycloak.cluster.ClusterEvent;
import org.keycloak.cluster.ClusterListener;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserSessionProvider;
import org.keycloak.models.sessions.infinispan.InfinispanUserSessionProvider;
import org.keycloak.models.sessions.infinispan.InfinispanUserSessionProviderFactory;
import org.keycloak.models.utils.KeycloakModelUtils;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public abstract class AbstractUserSessionClusterListener<SE extends SessionClusterEvent> implements ClusterListener {

    private static final Logger log = Logger.getLogger(AbstractUserSessionClusterListener.class);

    private final KeycloakSessionFactory sessionFactory;

    public AbstractUserSessionClusterListener(KeycloakSessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }


    @Override
    public void eventReceived(ClusterEvent event) {
        KeycloakModelUtils.runJobInTransaction(sessionFactory, (KeycloakSession session) -> {
            InfinispanUserSessionProvider provider = (InfinispanUserSessionProvider) session.getProvider(UserSessionProvider.class, InfinispanUserSessionProviderFactory.PROVIDER_ID);
            SE sessionEvent = (SE) event;

            String realmId = sessionEvent.getRealmId();

            if (log.isDebugEnabled()) {
                log.debugf("Received user session event '%s'", sessionEvent.toString());
            }

            eventReceived(session, provider, sessionEvent);

        });
    }

    protected abstract void eventReceived(KeycloakSession session, InfinispanUserSessionProvider provider, SE sessionEvent);

}
