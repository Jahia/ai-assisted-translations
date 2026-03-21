package org.jahia.community.translation.assisted.service;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;

public interface AssistedTranslationResponse {

    @GraphQLField
    @GraphQLDescription("Get the status of the response")
    boolean isSuccessful();

    void setSuccessful(boolean state);

    @GraphQLField
    @GraphQLDescription("Get the DeepL response message")
    String getMessage();

    void addMessage(String text);

    AssistedTranslationResponse merge(AssistedTranslationResponse other);
}
