import gql from 'graphql-tag';

const getMutationTranslateNode = gql`mutation TranslateNode($path:String!,$sourceLanguage:String!,$targetLanguage:String!) {
        jcr{
            mutateNode(pathOrId: $path) {
                translateNode(sourceLocale: $sourceLanguage, targetLocale: $targetLanguage){
                    message
                    successful
                }
            }
        }
    }`;

const suggestTranslationForLanguage = gql`query SuggestTranslationForLanguage($path:String!, $sourceLanguage:String!, $targetLanguage:String!) {
    jcr {
        nodeByPath(path: $path) {
            translationSuggestions(sourceLanguage: $sourceLanguage, targetLanguage: $targetLanguage) {
                fieldName
                translatedValue
            }
        }
    }
}`;
// Only the named properties are sent to the translation service, which bills by what it is sent.
const suggestTranslationForProperties = gql`query SuggestTranslationForProperties($path:String!, $sourceLanguage:String!, $targetLanguage:String!, $propertyNames:[String]) {
    jcr {
        nodeByPath(path: $path) {
            translationSuggestions(sourceLanguage: $sourceLanguage, targetLanguage: $targetLanguage, propertyNames: $propertyNames) {
                fieldName
                translatedValue
            }
        }
    }
}`;
export {getMutationTranslateNode, suggestTranslationForLanguage, suggestTranslationForProperties};
