import React, {useState} from 'react';
import PropTypes from 'prop-types';
import {useTranslation} from 'react-i18next';
import {useApolloClient} from '@apollo/client';
import {useNodeChecks} from '@jahia/data-helper';
import {suggestTranslationForProperties} from './RequestAssistedTranslation.gql';
import {pickFieldTranslation} from './pickFieldTranslation';

/**
 * Translates one field into one language, from a row of the "Edit in all languages" dialog of the
 * edit-field-all-languages module (target `edit-field-all-languages/row`).
 *
 * Only this row's property is sent to the translation service, which bills by what it is sent. It
 * translates what is stored in the source language, not what is being typed in the dialog.
 */
export const RequestAssistedTranslationForFieldRowComponent = ({
    render: Render,
    loading: Loading,
    field,
    path,
    sourceLanguage,
    targetLanguage,
    setTargetValue,
    ...others
}) => {
    const {t} = useTranslation('ai-assisted-translations');
    const client = useApolloClient();
    const [isTranslating, setIsTranslating] = useState(false);

    const res = useNodeChecks({path}, {requireModuleInstalledOnSite: ['ai-assisted-translations']});

    if (res.loading) {
        return (Loading && <Loading {...others}/>) || false;
    }

    if (!res.checksResult) {
        return false;
    }

    const translate = async () => {
        setIsTranslating(true);
        try {
            const {data} = await client.query({
                query: suggestTranslationForProperties,
                variables: {path, sourceLanguage, targetLanguage, propertyNames: [field.propertyName]},
                fetchPolicy: 'network-only'
            });
            const value = pickFieldTranslation(data?.jcr?.nodeByPath?.translationSuggestions, field.propertyName, field.multiple);
            if (value !== undefined) {
                setTargetValue(value);
            }
        } catch (error) {
            console.error(error);
        } finally {
            setIsTranslating(false);
        }
    };

    return (
        <Render
            {...others}
            isVisible
            enabled={!isTranslating}
            dataSelRole={`ai-translate-field-${targetLanguage}`}
            buttonLabel={t('label.action')}
            onClick={translate}
        />
    );
};

RequestAssistedTranslationForFieldRowComponent.propTypes = {
    render: PropTypes.func.isRequired,
    loading: PropTypes.func,
    field: PropTypes.shape({
        propertyName: PropTypes.string.isRequired,
        multiple: PropTypes.bool
    }).isRequired,
    path: PropTypes.string.isRequired,
    sourceLanguage: PropTypes.string.isRequired,
    targetLanguage: PropTypes.string.isRequired,
    setTargetValue: PropTypes.func.isRequired
};
