import React, {useContext} from 'react';
import PropTypes from 'prop-types';
import {useTranslation} from 'react-i18next';
import {ComponentRendererContext} from '@jahia/ui-extender';
import {RequestAssistedTranslation} from './RequestAssistedTranslation';
import {useContentEditorContext, useContentEditorSectionContext, useContentEditorConfigContext} from '@jahia/jcontent';
import {useNodeChecks} from '@jahia/data-helper';
import {useFormikContext} from 'formik';

export const RequestAssistedTranslationComponent = ({
    render: Render,
    loading: Loading,
    ...others
}) => {
    const editorContext = useContentEditorContext();
    const editorSectionContext = useContentEditorSectionContext();
    const formikContext = useFormikContext();
    const componentRenderer = useContext(ComponentRendererContext);
    const editorConfigContext = useContentEditorConfigContext();

    console.debug('RequestTranslationAiAssistedForAllLanguagesActionComponent {editorContext, editorSectionContext, formikContext, editorConfigContext}', {editorContext, editorSectionContext, formikContext, editorConfigContext});
    // Load namespace
    useTranslation('ai-assisted-translations');

    const res = useNodeChecks(
        {path: editorContext.nodeData.path},
        {
            requireModuleInstalledOnSite: ['ai-assisted-translations']
        }
    );

    if (res.loading) {
        return (Loading && <Loading {...others}/>) || false;
    }

    if (!res.checksResult || editorContext.siteInfo.languages.length <= 1) {
        return false;
    }

    const sourceLanguage = editorConfigContext?.sideBySideContext?.lang || editorContext.nodeData?.translationLanguages?.[0];
    const enabled = !editorContext.nodeData?.lockedAndCannotBeEdited && sourceLanguage !== undefined;

    return (
        <Render
            {...others}
            isVisible
            enabled={enabled}
            onClick={() => {
                componentRenderer.render('requestTranslationAiAssistedForAllLanguages', RequestAssistedTranslation, {
                    path: editorContext.nodeData.path,
                    sourceLanguage: sourceLanguage,
                    targetLanguage: editorContext.lang,
                    siteLanguages: editorContext.siteInfo.languages,
                    availableSourceLanguages: editorContext.nodeData?.translationLanguages,
                    showDropdown: editorConfigContext?.sideBySideContext?.lang === undefined,
                    isOpen: true,
                    formik: formikContext,
                    onClose: () => {
                        componentRenderer.setProperties('requestTranslationAiAssistedForAllLanguages', {isOpen: false});
                    },
                    onExited: () => {
                        componentRenderer.destroy('requestTranslationAiAssistedForAllLanguages');
                    }
                });
            }}
        />
    );
};

RequestAssistedTranslationComponent.propTypes = {
    formik: PropTypes.object.isRequired,
    editorContext: PropTypes.object.isRequired,
    render: PropTypes.func.isRequired,
    loading: PropTypes.func
};
