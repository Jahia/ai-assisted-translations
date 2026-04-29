package org.jahia.community.translation.assisted.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;

import java.util.List;

public class TranslatedField {

    private final String fieldName;
    private String translatedValue;
    private List<String> translatedValues;

    public TranslatedField(String fieldName, String translatedValue) {
        this.fieldName = fieldName;
        this.translatedValue = translatedValue;
    }

    public TranslatedField(String fieldName, List<String> translatedValues) {
        this.fieldName = fieldName;
        this.translatedValues = translatedValues;
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

    public List<String> getTranslatedValues() {
        return translatedValues;
    }

    public void addTranslatedValue(String translatedValue, int index) {
        if (translatedValues == null) {
            translatedValues = new java.util.ArrayList<>();
        }
        // Ensure the list is large enough to hold the value at the specified index
        while (translatedValues.size() <= index) {
            translatedValues.add(null);
        }
        translatedValues.set(index, translatedValue);
    }
}
