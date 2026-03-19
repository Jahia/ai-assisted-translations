import React, {useContext} from 'react';
import PropTypes from 'prop-types';
import {RequestAssistedTranslation} from './RequestAssistedTranslation';
import {useNodeChecks, useNodeInfo, useSiteInfo} from '@jahia/data-helper';
import {useSelector} from 'react-redux';
import {ComponentRendererContext} from "@jahia/ui-extender";

export const RequestAssistedTranslationForTreeComponent = ({
                                                               path,
                                                               render: Render,
                                                               loading: Loading,
                                                               ...others
                                                           }) => {
    const {language, site, uiLanguage} = useSelector(state => ({language: state.language, site: state.site, uiLanguage: state.uilang}));
    const {siteInfo, loading} = useSiteInfo({siteKey: site, displayLanguage: language, uiLanguage});
    const componentRenderer = useContext(ComponentRendererContext);
    const {node, nodeLoading: nodeLoading} = useNodeInfo({path: path, language: language}, {getDisplayName: true});
    const res = useNodeChecks(
        {path: path},
        {
            requireModuleInstalledOnSite: ['ai-assisted-translations']
        }
    );

    if (loading || !siteInfo || nodeLoading || !node) {
        return null;
    }

    if (res.loading) {
        return (Loading && <Loading {...others}/>) || false;
    }

    if (!res.checksResult) {
        return false;
    }
    const enabled = node.hasWritePermission && !node.lockedAndCannotBeEdited;

    return (
        <Render
            {...others}
            isVisible
            enabled={enabled}
            onClick={() => {

                componentRenderer.render('requestTranslationAiAssistedForAllLanguages', RequestAssistedTranslation, {
                    path: path,
                    sourceLanguage: siteInfo.defaultLanguage,
                    targetLanguage: language,
                    siteLanguages: siteInfo.languages,
                    availableSourceLanguages: siteInfo.languages,
                    showDropdown: true,
                    isOpen: true,
                    translateTree: true,
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

RequestAssistedTranslationForTreeComponent.propTypes = {
    path: PropTypes.string,
    render: PropTypes.func.isRequired,
    loading: PropTypes.func
};
