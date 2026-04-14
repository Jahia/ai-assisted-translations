import React, {useState} from 'react';
import {Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle} from '@material-ui/core';
import {Button, Dropdown, Loader, Typography} from '@jahia/moonstone';
import {useTranslation} from 'react-i18next';
import {useApolloClient} from '@apollo/client';
import PropTypes from 'prop-types';
import {getMutationTranslateNode, suggestTranslationForLanguage} from './RequestAssistedTranslation.gql';
import styles from './RequestAssistedTranslation.scss';
import {registry} from '@jahia/ui-extender';

const triggerRefetch = (name, queryParams) => {
    const refetch = registry.get('refetcher', name);
    if (!refetch?.refetch) {
        return;
    }

    if (queryParams) {
        refetch.refetch(queryParams);
    } else {
        refetch.refetch();
    }
};

const triggerRefetchAll = () => {
    registry.find({type: 'refetcher'}).forEach(refetch => triggerRefetch(refetch.key));
};

const getInitialState = (siteLanguages, sourceLanguage) => {
    let languageObject = siteLanguages.find(siteLanguage => siteLanguage.language === sourceLanguage);
    console.debug('Found language matching', languageObject, siteLanguages, sourceLanguage);
    return languageObject;
};

// eslint-disable-next-line max-params
const handleSuggestionCall = (suggestTranslation, formik, setErrorState, setIsLoading, onClose, setErrorMessage) => {
    suggestTranslation().then(data => {
        // First we transform data as some fields are multivalued and in this case the fieldname contains the index at the end fieldname___index___, we need to group the values per firld name amnd use an array for multivalued ones
        const fields = {};
        data.forEach(suggestion => {
            const key = suggestion.fieldName;
            // Detect if it is a multiple field
            const value = suggestion.translatedValue;
            if (key.match(/___\d+___$/)) {
                const fieldName = key.replace(/___\d+___$/, '');
                // Keep the original index
                const index = key.match(/___\d+___$/)[0].replaceAll('___', '');
                if (!fields[fieldName]) {
                    fields[fieldName] = [];
                }

                fields[fieldName][index] = value;
            } else {
                fields[key] = value;
            }
        });

        Object.keys(fields).forEach(key => {
            const value = fields[key];
            // Find the field corresponding to the key, inside the dom a div with data-sel-content-editor-field="fieldName" is generated, we can use this to find the field and set the value in formik
            // Getting the field with data-sel-content-editor-field="fieldName" and data-sel-i18n="true"
            const initialField = document.querySelector(`[data-sel-content-editor-field$="_${key}"][data-sel-i18n="true"][data-sel-content-editor-field-readonly="false"]`)?.dataset.selContentEditorField;

            // <div class="src-javascript-ContentEditor-editorTabs-EditPanelContent-FormBuilder-Field-Field__formControl--MdZFr" data-first-field="false" data-sel-content-editor-field="jdnt:company_headline" data-sel-content-editor-field-type="RichText" data-sel-content-editor-field-readonly="false" data-sel-content-editor-field-ismultiple="false" data-sel-i18n="true"></div>
            if (initialField) {
                // Set to empty string if value is null or undefined to ensure field is cleared
                // This is necessary to avoid issues with formik not updating the field if the value is null
                // or undefined, as it will not trigger a change event
                formik.setFieldValue(initialField, value || '');
            }
        });
        setIsLoading(false);
        onClose();
    }).catch(err => {
        console.error(err);
        setErrorState('translation_error');
        setErrorMessage(err.message);
    });
};

// eslint-disable-next-line max-params
const handleTreeTranslationCall = (translateTreeMutation, setErrorState, setIsLoading, onClose, client, setErrorMessage) => {
    translateTreeMutation().then(() => {
        client.reFetchObservableQueries();
        triggerRefetchAll();
        setIsLoading(false);
        onClose();
    }).catch(err => {
        console.error(err);
        setErrorState('translation_error');
        setErrorMessage(err.message);
    });
};

const LoaderOverlay = () => {
    return (
        <div className="flexFluid flexCol_center alignCenter" style={{backgroundColor: 'var(--color-light)'}}>
            <Loader size="big"/>
        </div>
    );
};

export const RequestAssistedTranslation = ({
    path,
    sourceLanguage,
    targetLanguage,
    siteLanguages,
    availableSourceLanguages,
    showDropdown,
    formik,
    isTranslateTree,
    isOpen,
    onExited,
    onClose
}) => {
    const {t} = useTranslation('ai-assisted-translations');
    const [selected, setSelected] = useState(getInitialState(siteLanguages, sourceLanguage));
    const [errorState, setErrorState] = useState(false);
    const [errorMessage, setErrorMessage] = useState('');
    const [isLoading, setIsLoading] = useState(false);

    const client = useApolloClient();

    const suggestTranslation = async () => {
        const {data} = await client.query({
            query: suggestTranslationForLanguage,
            variables: {path, sourceLanguage: selected.language, targetLanguage}
        });
        return data.jcr.nodeByPath.translationSuggestions;
    };

    const translateTreeMutation = async () => {
        const {data} = await client.mutate({
            mutation: getMutationTranslateNode,
            variables: {path, sourceLanguage: selected.language, targetLanguage}
        });
        return data.jcr.mutateNode.translateNode;
    };

    const handleClickDialog = () => {
        setIsLoading(true);
        if (formik !== undefined) {
            handleSuggestionCall(suggestTranslation, formik, setErrorState, setIsLoading, onClose, setErrorMessage);
        } else if (isTranslateTree === true) {
            handleTreeTranslationCall(translateTreeMutation, setErrorState, setIsLoading, onClose, client, setErrorMessage());
        }
    };

    let sourceLanguageObject = siteLanguages.find(siteLanguage => siteLanguage.language === sourceLanguage);
    let targetLanguageObject = siteLanguages.find(siteLanguage => siteLanguage.language === targetLanguage);
    let translationLanguages = {
        sourceLanguage: sourceLanguageObject.displayName,
        sourceLanguageUI: sourceLanguageObject.uiLanguageDisplayName,
        targetLanguage: targetLanguageObject.displayName,
        targetLanguageUI: targetLanguageObject.uiLanguageDisplayName
    };
    return (
        <>
            <Dialog fullWidth
                    open={isOpen}
                    aria-labelledby="form-dialog-title"
                    data-sel-role="translate-language-dialog"
                    onExited={onExited}
                    onClose={isLoading ? undefined : onClose}
            >
                <DialogTitle id="dialog-language-title">
                    <Typography isUpperCase variant="heading" weight="bold">
                        {t('ai-assisted-translations:label.dialogTitleAllProperties', translationLanguages)}
                    </Typography>
                </DialogTitle>
                <DialogContent style={{overflowY: 'hidden'}}>
                    {showDropdown &&
                        <>
                            <Typography variant="subheading">
                                {t('ai-assisted-translations:label.translateFrom')}
                            </Typography>
                            <Dropdown
                                className={styles.language}
                                value={selected.language}
                                size="medium"
                                isDisabled={isLoading}
                                data-sel-role="from-language-selector"
                                data={availableSourceLanguages.flatMap(element => {
                                    let optionLanguage = siteLanguages.find(siteLanguage => siteLanguage.language === (isTranslateTree ? element.language : element));
                                    // Get rid of the language in the dropdown if the language is not available in siteLanguages, as it means that the language is not available for translation
                                    if (!optionLanguage) {
                                        return [];
                                    }

                                    return [{
                                        value: optionLanguage.language,
                                        label: `${optionLanguage.displayName} (${optionLanguage.uiLanguageDisplayName})`
                                    }];
                                })}
                                onChange={(e, item) => setSelected(getInitialState(siteLanguages, item.value))}
                            />
                        </>}
                    {!showDropdown &&
                        <Typography variant="subheading">
                            {/* eslint-disable-next-line react/no-danger */}
                            <span dangerouslySetInnerHTML={{__html: t('ai-assisted-translations:label.dialogDescriptionAllProperties', translationLanguages)}}/>
                        </Typography>}
                    {isLoading &&
                        <LoaderOverlay/>}
                </DialogContent>
                <DialogActions>
                    <Button size="big"
                            label={t('ai-assisted-translations:label.cancel')}
                            data-sel-role="cancel-button"
                            disabled={isLoading}
                            onClick={onClose}/>
                    <Button size="big"
                            color="accent"
                            data-sel-role="translate-button"
                            label={t('ai-assisted-translations:label.translate')}
                            disabled={isLoading}
                            onClick={handleClickDialog}
                    />
                </DialogActions>
            </Dialog>

            <Dialog
                maxWidth="lg"
                open={Boolean(errorState)}
                aria-labelledby="alert-dialog-title"
                aria-describedby="alert-dialog-description"
                onClose={() => {
                    setErrorState(false);
                    setIsLoading(false);
                    onClose();
                }}
            >
                <DialogTitle id="alert-dialog-title">{t('ai-assisted-translations:label.errorTitle')}</DialogTitle>
                <DialogContent>
                    <DialogContentText
                        id="alert-dialog-description"
                    >{errorMessage === undefined ? t('ai-assisted-translations:label.errorContentAllProperties') : errorMessage}
                    </DialogContentText>
                </DialogContent>
                <DialogActions>
                    <Button label={t('ai-assisted-translations:label.cancel')}
                            color="accent"
                            size="big"
                            onClick={() => {
                                setErrorState(false);
                                setIsLoading(false);
                                onClose();
                            }}
                    />
                </DialogActions>
            </Dialog>
        </>
    );
};

RequestAssistedTranslation.propTypes = {
    path: PropTypes.string.isRequired,
    sourceLanguage: PropTypes.string.isRequired,
    targetLanguage: PropTypes.string.isRequired,
    siteLanguages: PropTypes.array.isRequired,
    availableSourceLanguages: PropTypes.array,
    showDropdown: PropTypes.bool,
    formik: PropTypes.object,
    isTranslateTree: PropTypes.bool,
    isOpen: PropTypes.bool,
    onExited: PropTypes.func,
    onClose: PropTypes.func
};
