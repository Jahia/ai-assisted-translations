import React, {useEffect, useMemo, useState} from 'react';
import {Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle} from '@material-ui/core';
import {Button, Checkbox, Input, Typography} from '@jahia/moonstone';
import {useTranslation} from 'react-i18next';
import styles from './RequestAssistedTranslation.scss';
import {useApolloClient, useMutation, useQuery} from '@apollo/client';
import PropTypes from 'prop-types';
import {
    getMutationTranslateNode,
    getMutationTranslateProperty,
    getQueryTranslationLocksAndPermissions,
    suggestTranslationForLanguage
} from './RequestAssistedTranslation.gql';
import WarningAlert from './WarningAlert';

export const RequestAssistedTranslation = ({
                                            path,
                                            language,
                                            setI18nContext,
                                            siteLanguages,
                                            field,
                                            fieldValue,
                                            fields,
                                            formik,
                                            isOpen,
                                            isNew,
                                            onExited,
                                            onClose
                                        }) => {
    const {t} = useTranslation('translation-deepl');
    const [selected, setSelected] = useState([]);
    const [filter, setFilter] = useState('');
    const [errorState, setErrorState] = useState('');
    const [warningModalShown, setWarningModalShown] = useState(false);
    const isSingleField = !fields;

    const allLanguages = useMemo(() => siteLanguages.filter(l => l.language !== language), [siteLanguages, language]);

    const {data, error} = useQuery(getQueryTranslationLocksAndPermissions(allLanguages, path), {
        errorPolicy: 'ignore', variables: {path}
    });

    if (error) {
        console.log(error);
    }

    const [translateNode] = useMutation(getMutationTranslateNode());

    const [translateProperty] = useMutation(getMutationTranslateProperty());

    const client = useApolloClient();

    const suggestTranslation = async (targetLanguage) => {
        const {data} = await client.query({
            query: suggestTranslationForLanguage,
            variables: {path, sourceLanguage: language, targetLanguage}
        });
        return data.jcr.nodeByPath.translationSuggestions;
    }

    const handleClickDialog = () => {
        suggestTranslation(selected[0]).then(data => {
            data.forEach(suggestion => {
                const key = suggestion.fieldName;
                const value = suggestion.translatedValue;
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
        }).then(onClose());
    };

    const handleClickWarningModal = () => {
        selected.reduce((acc, lang) => {
            console.log('testXX2:'+lang+", "+language);
            translateNode({
                variables: {
                    path: path,
                    sourceLocale: language,
                    targetLocale: lang,
                }
            });
        }, {});
        onClose();
        setWarningModalShown(false);
        onClose();
    };

    const available = useMemo(() => {
        const disabledLanguages = data ? allLanguages.filter(l => !data.jcr.nodeByPath[`perm_${l.language}`] || (data.jcr[`lock_${l.language}`] && data.jcr[`lock_${l.language}`].lockInfo.details.length > 0)) : allLanguages;
        return allLanguages.filter(l => !disabledLanguages.includes(l)).map(l => l.language);
    }, [data, allLanguages]);

    useEffect(() => {
        setSelected(available);
    }, [available]);

    const filtered = allLanguages.filter(l => !filter || l.language.includes(filter) || l.displayName.includes(filter));
    const filteredAndAvailable = filtered.map(l => l.language).filter(l => available.includes(l));
    const selectedDisplayNames = filtered.filter(l => selected.includes(l.language)).map(l => l.displayName).join(', ');
    const currentDisplayName = siteLanguages.find(l => l.language === language)?.displayName;

    const title = isSingleField ? t('translation-deepl:label.dialogTitle', {propertyName: field.displayName}) : t('translation-deepl:label.dialogTitleAllProperties');
    const description = isSingleField ? t('translation-deepl:label.dialogDescription') : t('translation-deepl:label.dialogDescriptionAllProperties');
    const errorDescription = isSingleField ? t('translation-deepl:label.errorContent', {property: field.displayName}) : t('translation-deepl:label.errorContentAllProperties');

    return (<>
        <Dialog fullWidth
                open={isOpen}
                aria-labelledby="form-dialog-title"
                data-sel-role="translate-language-dialog"
                onExited={onExited}
                onClose={onClose}
        >
            <DialogTitle>
                {title}
            </DialogTitle>
            <DialogContent>
                <DialogContentText component="div">
                    <div className={styles.subheading}>
                        <Typography>{description}</Typography>
                    </div>
                    <div className={styles.actions}>
                        <Button size="default"
                                label={t('translation-deepl:label.addAll')}
                                data-sel-role="add-all-button"
                                isDisabled={filteredAndAvailable.every(v => selected.includes(v))}
                                onClick={() => data && setSelected(filteredAndAvailable)}/>
                        <Button size="default"
                                label={t('translation-deepl:label.removeAll')}
                                data-sel-role="remove-all-button"
                                isDisabled={selected.length === 0}
                                onClick={() => data && setSelected([])}/>
                        <div className="flexFluid"/>
                        <Typography>{t('translation-deepl:label.languagesSelected', {count: selected.length})}</Typography>
                    </div>
                    <div className={styles.actions}>
                        <Input variant="search"
                               data-sel-role="language-filter"
                               placeholder={t('translation-deepl:label.filterLanguages')}
                               value={filter}
                               onChange={e => {
                                   setFilter(e.target.value);
                               }}
                               onClear={() => setFilter('')}/>
                    </div>
                    <div className={styles.languages}>
                        {filtered.length > 0 ? filtered.map(l => (<label key={l.language} className={styles.item}>
                            <Checkbox checked={selected.includes(l.language)}
                                      data-sel-role="translate-language-button"
                                      isDisabled={!available.includes(l.language)}
                                      name="lang"
                                      value={l.language}
                                      aria-label={l.displayName}
                                      onChange={() => setSelected((selected.includes(l.language)) ? selected.filter(s => l.language !== s) : [...selected, l.language])}
                            />
                            {l.displayName} {data && !available.includes(l.language) && (' - ' + t('translation-deepl:label.lock'))}
                        </label>)) : (<div className={styles.emptylanguages}>
                            <Typography>{t('translation-deepl:label.noResults')}</Typography></div>)}
                    </div>
                </DialogContentText>
            </DialogContent>
            <DialogActions>
                <Button size="big"
                        label={t('translation-deepl:label.cancel')}
                        data-sel-role="cancel-button"
                        onClick={onClose}/>
                <Button size="big"
                        isDisabled={selected.length === 0}
                        color="accent"
                        data-sel-role="translate-button"
                        label={t('translation-deepl:label.translate')}
                        onClick={handleClickDialog}
                />
            </DialogActions>
        </Dialog>

        <Dialog
            maxWidth="lg"
            open={Boolean(errorState)}
            aria-labelledby="alert-dialog-title"
            aria-describedby="alert-dialog-description"
            onClose={() => setErrorState()}
        >
            <DialogTitle id="alert-dialog-title">{t('translation-deepl:label.errorTitle')}</DialogTitle>
            <DialogContent>
                <DialogContentText id="alert-dialog-description">{errorDescription}</DialogContentText>
            </DialogContent>
            <DialogActions>
                <Button label={t('translation-deepl:label.cancel')}
                        color="accent"
                        size="big"
                        onClick={() => setErrorState()}
                />
            </DialogActions>
        </Dialog>
        <WarningAlert
            languages={selectedDisplayNames}
            currentLanguage={currentDisplayName}
            isOpen={warningModalShown}
            onApply={handleClickWarningModal}
            onClose={() => {
                setWarningModalShown(false);
            }}
        />
    </>);
}


RequestAssistedTranslation.propTypes = {
    path: PropTypes.string.isRequired,
    language: PropTypes.string.isRequired,
    siteLanguages: PropTypes.array.isRequired,
    setI18nContext: PropTypes.func.isRequired,
    isNew: PropTypes.bool,
    field: PropTypes.object,
    fieldValue: PropTypes.string,
    fields: PropTypes.object,
    formik: PropTypes.object,
    isOpen: PropTypes.bool,
    onExited: PropTypes.func,
    onClose: PropTypes.func
};
