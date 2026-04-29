package org.jahia.community.translation.assisted.graphql;

import org.jahia.modules.graphql.provider.dxm.DXGraphQLExtensionsProvider;
import org.osgi.service.component.annotations.Component;

import java.util.Arrays;
import java.util.Collection;

@Component(service = DXGraphQLExtensionsProvider.class, immediate = true)
public class DXGraphQLExtensionAssistedTranslationProvider implements DXGraphQLExtensionsProvider {

    @Override
    public Collection<Class<?>> getExtensions() {
        return Arrays.asList(GqlJcrNodeMutationAssistedTranslation.class, GqlQueryTranslation.class);
    }
}
