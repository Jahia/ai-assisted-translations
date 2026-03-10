package org.jahia.community.translation.assisted.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;

public class TranslatedField {

    private String fieldName;
    private String translatedValue;

    public TranslatedField(String fieldName, String translatedValue) {
        this.fieldName = fieldName;
        this.translatedValue = translatedValue;
    }

    @GraphQLField
    @GraphQLDescription("Name of the translated field")
    public String getFieldName() {
        return fieldName;
    }

    @GraphQLField
    @GraphQLDescription("Translated value of the field")
    public String getTranslatedValue() {
        return translatedValue;
    }
}
