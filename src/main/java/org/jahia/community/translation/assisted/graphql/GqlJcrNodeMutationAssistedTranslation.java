package org.jahia.community.translation.assisted.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import org.jahia.community.translation.assisted.service.AssistedTranslationResponse;
import org.jahia.community.translation.assisted.service.TranslatorService;
import org.jahia.modules.graphql.provider.dxm.DataFetchingException;
import org.jahia.modules.graphql.provider.dxm.node.GqlJcrNodeMutation;
import org.jahia.osgi.BundleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;

@GraphQLTypeExtension(GqlJcrNodeMutation.class)
@GraphQLDescription("Entry point of the mutation for the DeepL GraphQL API")
public class GqlJcrNodeMutationAssistedTranslation {
    private static final Logger logger = LoggerFactory.getLogger(GqlJcrNodeMutationAssistedTranslation.class);
    private final GqlJcrNodeMutation nodeMutation;

    public GqlJcrNodeMutationAssistedTranslation(GqlJcrNodeMutation nodeMutation) {
        this.nodeMutation = nodeMutation;
    }

    @GraphQLField
    @GraphQLDescription("Translate node")
    public AssistedTranslationResponse translateNode(
            @GraphQLName("sourceLocale") @GraphQLDescription("Locale to translate from") String sourceLocale,
            @GraphQLName("targetLocale") @GraphQLDescription("Locale to translate to") String targetLocale
    ) throws InterruptedException {
        if (logger.isDebugEnabled()) {
            logger.debug(String.format("Translating %s from %s to %s", nodeMutation.getNode().getPath(), sourceLocale, targetLocale));
        }

        TranslatorService translatorService = BundleUtils.getOsgiService(TranslatorService.class, null);
        if (translatorService == null) {
            logger.warn("No TranslatorService available – translation service is not configured");
            throw new DataFetchingException("No translation service available. Please check the module configuration.");
        }

        try {
            return translatorService.translateNode(nodeMutation.getNode().getNode(), sourceLocale, targetLocale);
        } catch (RepositoryException e) {
            if (logger.isErrorEnabled()) {
                logger.error("Error when translating {} from {} to {}", nodeMutation.getNode().getPath(), sourceLocale, targetLocale);
            }
        }
        return null;
    }

    @GraphQLField
    @GraphQLDescription("Translate property")
    public AssistedTranslationResponse translateProperty(
            @GraphQLName("propertyName") @GraphQLDescription("Property name to translate") String propertyName,
            @GraphQLName("sourceLocale") @GraphQLDescription("Locale to translate from") String sourceLocale,
            @GraphQLName("targetLocale") @GraphQLDescription("Locale to translate to") String targetLocale

    ) throws InterruptedException {
        if (logger.isDebugEnabled()) {
            logger.debug(String.format("Translating %s, property %s, from %s to %s", nodeMutation.getNode().getPath(), propertyName, sourceLocale, targetLocale));
        }

        TranslatorService translatorService = BundleUtils.getOsgiService(TranslatorService.class, null);
        if (translatorService == null) {
            logger.warn("No TranslatorService available – translation service is not configured");
            throw new DataFetchingException("No translation service available. Please check the module configuration.");
        }

        try {
            return translatorService.translateProperty(nodeMutation.getNode().getNode(), propertyName, sourceLocale, targetLocale);
        } catch (RepositoryException e) {
            if (logger.isErrorEnabled()) {
                logger.error("Error when translating  {} of node {} from {} to {}", propertyName, nodeMutation.getNode().getPath(), sourceLocale, targetLocale);
            }
        }
        return null;
    }
}
