/*
 * Copyright 2016 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.security.elytron;

import static org.jboss.as.security.elytron.Capabilities.KEY_MANAGER_RUNTIME_CAPABILITY;
import static org.jboss.as.security.elytron.Capabilities.KEY_STORE_RUNTIME_CAPABILITY;
import static org.jboss.as.security.elytron.Capabilities.SECURITY_REALM_RUNTIME_CAPABILITY;
import static org.jboss.as.security.elytron.Capabilities.TRUST_MANAGER_RUNTIME_CAPABILITY;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.security.Constants;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * This class defines methods used to obtain {@link ResourceDefinition} instances for the various components of the elytron
 * integration.
 *
 * @author <a href="mailto:sguilhen@redhat.com">Stefan Guilhen</a>
 */
public class ElytronIntegrationResourceDefinitions {

    public static final SimpleAttributeDefinition LEGACY_JAAS_CONFIG =
            new SimpleAttributeDefinitionBuilder(Constants.LEGACY_JAAS_CONFIG, ModelType.STRING, false)
                    .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                    .setValidator(new StringLengthValidator(1))
                    .setAllowExpression(false)
                    .setAccessConstraints(SensitiveTargetAccessConstraintDefinition.SECURITY_DOMAIN_REF)
                    .build();

    public static final SimpleAttributeDefinition LEGACY_JSSE_CONFIG =
            new SimpleAttributeDefinitionBuilder(Constants.LEGACY_JSSE_CONFIG, ModelType.STRING, false)
                    .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                    .setValidator(new StringLengthValidator(1))
                    .setAllowExpression(false)
                    .setAccessConstraints(SensitiveTargetAccessConstraintDefinition.SECURITY_DOMAIN_REF)
                    .build();

    public static final SimpleAttributeDefinition APPLY_ROLE_MAPPERS =
            new SimpleAttributeDefinitionBuilder(Constants.APPLY_ROLE_MAPPERS, ModelType.BOOLEAN, true)
                    .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                    .setDefaultValue(ModelNode.TRUE)
                    .setAllowExpression(true)
                    .build();

    /**
     * Defines a resource that represents an Elytron-compatible realm that can be exported by the legacy security subsystem.
     * The constructed {@code SecurityRealm} wraps a legacy {@code SecurityDomainContext} and delegates authentication
     * decisions to that context.
     *
     * To export the realm the resource uses a {@code BasicAddHandler} implementation that registers the security-realm
     * capability and implements a {@code org.jboss.as.security.elytron.BasicService.ValueSupplier} that uses the injected
     * {@code SecurityDomainContext} to create and return an instance of {@code SecurityDomainContextRealm}.
     */
    public static ResourceDefinition getElytronRealmResourceDefinition() {

        final AttributeDefinition[] attributes = new AttributeDefinition[] {LEGACY_JAAS_CONFIG, APPLY_ROLE_MAPPERS};
        final AbstractAddStepHandler addHandler = new BasicAddHandler(attributes, SECURITY_REALM_RUNTIME_CAPABILITY);

        return new BasicResourceDefinition(Constants.ELYTRON_REALM, addHandler, attributes, SECURITY_REALM_RUNTIME_CAPABILITY);
    }

    /**
     * Defines a resource that represents an Elytron-compatible key store that can be exported by a JSSE-enabled domain
     * in the legacy security subsystem.
     *
     * To export the key store the resource uses a {@code BasicAddHandler} implementation that registers the elytron key-store
     * capability and implements a {@code org.jboss.as.security.elytron.BasicService.ValueSupplier} that uses the injected
     * {@code SecurityDomainContext} to obtain a {@code JSSESecurityDomain}. If such domain is found, its configured key
     * store is obtained and returned.
     *
     * The {@code ValueSupplier} implementation throws an exception if the referenced legacy domain is not a JSSE-enabled
     * domain or if the domain doesn't contain a key store configuration.
     */
    public static ResourceDefinition getElytronKeyStoreResourceDefinition() {
        final AttributeDefinition[] attributes = new AttributeDefinition[] {LEGACY_JSSE_CONFIG};
        final AbstractAddStepHandler addHandler = new BasicAddHandler(attributes, KEY_STORE_RUNTIME_CAPABILITY);

        return new BasicResourceDefinition(Constants.ELYTRON_KEY_STORE, addHandler, attributes, KEY_STORE_RUNTIME_CAPABILITY);
    }

    /**
     * Defines a resource that represents an Elytron-compatible trust store that will be exported by a JSSE-enabled domain
     * in the legacy security subsystem.
     *
     * To export the trust store the resource uses a {@code BasicAddHandler} implementation that registers the elytron key-store
     * capability and implements a {@code org.jboss.as.security.elytron.BasicService.ValueSupplier} that uses the injected
     * {@code SecurityDomainContext} to obtain a {@code JSSESecurityDomain}. If such domain is found, its configured trust
     * store is obtained and returned.
     *
     * NOTE 1: In the Elytron subsystem, both key stores and trust stores are registered using the same capability. This
     * means that the name of the trust store must be unique across all configured trust stores and key stores. If a trust
     * store resource is registered with the same name of a key store resource, an error will occur.
     *
     * The {@code ValueSupplier} implementation throws an exception if the referenced legacy domain is not a JSSE-enabled
     * domain or if the domain doesn't contain a trust store configuration.
     *
     * NOTE 2: The {@code PicketBox} implementation of a {@code JSSESecurityDomain} returns a reference to the key store if
     * a trust store was not configured. So extra care must be taken when that implementation is used (default) as the code
     * will silently export the key store as a trust store instead of throwing an exception to alert about a missing trust
     * store configuration in the legacy JSSE-enabled domain.
     */
    public static ResourceDefinition getElytronTrustStoreResourceDefinition() {
        final AttributeDefinition[] attributes = new AttributeDefinition[] {LEGACY_JSSE_CONFIG};
        final AbstractAddStepHandler addHandler = new BasicAddHandler(attributes, KEY_STORE_RUNTIME_CAPABILITY);

        return new BasicResourceDefinition(Constants.ELYTRON_TRUST_STORE, addHandler, attributes, KEY_STORE_RUNTIME_CAPABILITY);
    }

    /**
     * Defines a resource that represents Elytron-compatible key managers that can be exported by a JSSE-enabled domain
     * in the legacy security subsystem.
     *
     * To export the key managers the resource uses a {@code BasicAddHandler} implementation that registers the elytron
     * key-managers capability and implements a {@code org.jboss.as.security.elytron.BasicService.ValueSupplier} that uses
     * the injected {@code SecurityDomainContext} to obtain a {@code JSSESecurityDomain}. If such domain is found, its
     * configured key manager array is obtained and returned.
     *
     * The {@code ValueSupplier} implementation throws an exception if the referenced legacy domain is not a JSSE-enabled
     * domain or if the domain doesn't contain a key store configuration that can be used to build the key managers.
     */
    public static ResourceDefinition getElytronKeyManagersResourceDefinition() {
        final AttributeDefinition[] attributes = new AttributeDefinition[] {LEGACY_JSSE_CONFIG};
        final AbstractAddStepHandler addHandler = new BasicAddHandler(attributes, KEY_MANAGER_RUNTIME_CAPABILITY);

        return new BasicResourceDefinition(Constants.ELYTRON_KEY_MANAGER, addHandler, attributes, KEY_MANAGER_RUNTIME_CAPABILITY);
    }

    /**
     * Defines a resource that represents Elytron-compatible trust managers that can be exported by a JSSE-enabled domain
     * in the legacy security subsystem.
     *
     * To export the trust managers the resource uses a {@code BasicAddHandler} implementation that registers the elytron
     * trust-managers capability and implements a {@code org.jboss.as.security.elytron.BasicService.ValueSupplier} that uses
     * the injected {@code SecurityDomainContext} to obtain a {@code JSSESecurityDomain}. If such domain is found, its
     * configured trust manager array is obtained and returned.
     *
     * The {@code ValueSupplier} implementation throws an exception if the referenced legacy domain is not a JSSE-enabled
     * domain or if the domain doesn't contain a trust store configuration that can be used to build the trust managers.
     *
     * NOTE: The {@code PicketBox} implementation of a {@code JSSESecurityDomain} returns a reference to the key store if
     * a trust store was not configured. This means that the trust managers that it builds will use the configured key store
     * instead of throwing an exception to alert about a missing trust store configuration. So extra care must be taken
     * to ensure that the exported trust managers are being built using the correct trust stores.
     */
    public static ResourceDefinition getElytronTrustManagersResourceDefinition() {
        final AttributeDefinition[] attributes = new AttributeDefinition[] {LEGACY_JSSE_CONFIG};
        final AbstractAddStepHandler addHandler = new BasicAddHandler(attributes, TRUST_MANAGER_RUNTIME_CAPABILITY);

        return new BasicResourceDefinition(Constants.ELYTRON_TRUST_MANAGER, addHandler, attributes, TRUST_MANAGER_RUNTIME_CAPABILITY);
    }

    static String asStringIfDefined(OperationContext context, SimpleAttributeDefinition attributeDefinition, ModelNode model) throws OperationFailedException {
        ModelNode value = attributeDefinition.resolveModelAttribute(context, model);
        if (value.isDefined()) {
            return value.asString();
        }
        return null;
    }
}
