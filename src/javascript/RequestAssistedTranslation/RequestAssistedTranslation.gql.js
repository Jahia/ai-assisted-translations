import gql from 'graphql-tag';

const getQueryTranslationLocksAndPermissions = (allLanguages, path) => {
    const locks = allLanguages.map(l => `lock_${l.language}:nodeByPath(path: "${path}/j:translation_${l.language}") {lockInfo {details {type}}}`);
    const perms = allLanguages.map(l => `perm_${l.language}:hasPermission(permissionName: "jcr:modifyProperties_default_${l.language}")`);

    return gql`query GetTranslationLocksAndPermissions($path:String!) {
        jcr {
            nodeByPath(path: $path) {
                uuid
                workspace
                path
                lockInfo {
                    details {
                        language
                        owner
                        type
                    }
                }
                ${perms}
            }
            ${locks}
        }
    }`;
}

const getMutationTranslateNode = gql`mutation translateNode($path:String!,$sourceLanguage:String!,$targetLanguage:String!) {
        jcr{
            mutateNode(pathOrId: $path) {
                translateNode(sourceLocale: $sourceLanguage, targetLocale: $targetLanguage){
                    message
                    successful
                }
            }
        }
    }`;

const getMutationTranslateProperty = gql`mutation translateProperty($path:String!,$propertyName:String!,$sourceLocale:String!,$targetLocale:String!) {
        jcr{
            mutateNode(pathOrId: $path) {
                translateProperty(propertyName: $propertyName, sourceLocale: $sourceLocale, targetLocale: $targetLocale){
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
export {getQueryTranslationLocksAndPermissions, getMutationTranslateNode, getMutationTranslateProperty, suggestTranslationForLanguage};
