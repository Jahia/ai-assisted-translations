import React, {useContext, useMemo} from 'react';
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

    console.debug('RequestTranslationDeeplForAllLanguagesActionComponent {editorContext, editorSectionContext, formikContext, editorConfigContext}', {editorContext, editorSectionContext, formikContext, editorConfigContext});
    // Load namespace
    useTranslation('translation-deepl');

    const res = useNodeChecks(
        {path: editorContext.nodeData.path},
        {
            requireModuleInstalledOnSite: ['translation-deepl']
        }
    );

    const fields = useMemo(() => {
        const fieldNames = editorSectionContext.sections.flatMap(section =>
            section.fieldSets.flatMap(fieldset =>
                fieldset.fields
                    .filter(field =>
                        field?.i18n === true &&
                        field?.readOnly === false &&
                        field?.name !== undefined
                    )
                    .map(field => field.name)
            )
        );
        return Object.fromEntries(
            fieldNames.map(name => [name, formikContext.values[name] ?? ''])
        );
    }, [editorSectionContext.sections, formikContext.values]);

    if (res.loading) {
        return (Loading && <Loading {...others}/>) || false;
    }

    if (!res.checksResult || editorContext.siteInfo.languages.length <= 1) {
        return false;
    }

    const enabled = !editorContext.nodeData?.lockedAndCannotBeEdited;

    return (
        <Render
            {...others}
            isVisible
            enabled={enabled}
            onClick={() => {
                componentRenderer.render('requestTranslationDeeplForAllLanguages', RequestAssistedTranslation, {
                    path: editorContext.nodeData.path,
                    language: editorConfigContext.sideBySideContext.lang,
                    siteLanguages: editorContext.siteInfo.languages.filter(lang => lang.language === editorContext.lang),
                    isOpen: true,
                    isNew: editorContext?.nodeData?.newName !== undefined,
                    setI18nContext: editorContext.setI18nContext,
                    fields,
                    formik: formikContext,
                    onClose: () => {
                        componentRenderer.setProperties('requestTranslationDeeplForAllLanguages', {isOpen: false});
                    },
                    onExited: () => {
                        componentRenderer.destroy('requestTranslationDeeplForAllLanguages');
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
