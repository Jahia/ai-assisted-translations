package org.jahia.community.translation.assisted.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import org.jahia.community.translation.assisted.service.TranslatorService;
import org.jahia.modules.graphql.provider.dxm.DataFetchingException;
import org.jahia.modules.graphql.provider.dxm.node.GqlJcrNode;
import org.jahia.osgi.BundleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.Collections;
import java.util.List;


@GraphQLTypeExtension(GqlJcrNode.class)
@GraphQLDescription("Entry point of the query for the Translation GraphQL API")
public class GqlQueryTranslation {
    private static final Logger logger = LoggerFactory.getLogger(GqlQueryTranslation.class);
    private final GqlJcrNode node;

    public GqlQueryTranslation(GqlJcrNode node) {
        this.node = node;
    }

    @GraphQLField
    @GraphQLDescription("Translate node")
    public List<TranslatedField> translationSuggestions(
            @GraphQLName("sourceLanguage") @GraphQLDescription("Language to translate from") String sourceLocale,
            @GraphQLName("targetLanguage") @GraphQLDescription("Language to translate to") String targetLocale,
            @GraphQLName("propertyNames") @GraphQLDescription("Only translate these properties, as JCR names. Nothing else is sent to the translation service. All the translatable properties of the node when omitted.") List<String> propertyNames
    ) {
        try {
            TranslatorService translatorService = BundleUtils.getOsgiService(TranslatorService.class, null);
            if (translatorService == null) {
                logger.warn("No TranslatorService available – translation service is not configured");
                return Collections.emptyList();
            }
            if (translatorService.isAvailable()) {
                if (logger.isDebugEnabled()) {
                    logger.debug(String.format("Translating %s from %s to %s", node.getPath(), sourceLocale, targetLocale));
                }
                logger.info("Translating from {} to {} with provider {}", sourceLocale, targetLocale, translatorService.getProviderKey());
                return translatorService.suggestTranslationForNode(node.getNode(), sourceLocale, targetLocale, propertyNames);
            } else {
                logger.warn("TranslatorService with provider key {} is not available", translatorService.getProviderKey());
                return Collections.emptyList();
            }
        } catch (RepositoryException e) {
            throw new DataFetchingException("Error when suggesting translation", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DataFetchingException("Translation interrupted", e);
        }
    }
}
