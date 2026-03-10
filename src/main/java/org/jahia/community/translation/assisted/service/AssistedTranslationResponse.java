package org.jahia.community.translation.assisted.service;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;

public interface AssistedTranslationResponse {

    @GraphQLField
    @GraphQLDescription("Get the status of the response")
    public boolean isSuccessful();

    public void setSuccessful(boolean state);

    @GraphQLField
    @GraphQLDescription("Get the DeepL response message")
    public String getMessage();

    public void addMessage(String text);

    public AssistedTranslationResponse merge(AssistedTranslationResponse other);
}
