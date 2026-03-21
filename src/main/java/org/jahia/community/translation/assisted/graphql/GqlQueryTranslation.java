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
            @GraphQLName("targetLanguage") @GraphQLDescription("Language to translate to") String targetLocale
    ) {
        if (logger.isErrorEnabled()) {
            logger.error(String.format("Translating %s from %s to %s", node.getPath(), sourceLocale, targetLocale));
        }
        try {
            TranslatorService translatorService = BundleUtils.getOsgiService(TranslatorService.class, null);
            logger.info("Translated translations from {} to {} with {}", sourceLocale, targetLocale, translatorService.getProviderKey());
            if (translatorService.isAvailable()) {
                return translatorService.suggestTranslationForNode(node.getNode(), sourceLocale, targetLocale);
            } else {
                return Collections.emptyList();
            }
        } catch (RepositoryException e) {
            throw new DataFetchingException("Error when suggesting translation", e);
        } catch (InterruptedException e) {
            throw new DataFetchingException("Translation interrupted", e);
        }
    }
}
