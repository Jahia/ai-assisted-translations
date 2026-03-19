import React from "react";
import {registry} from "@jahia/ui-extender";
import {RequestAssistedTranslationComponent} from "./RequestAssistedTranslationComponent";
import DeeplIcon from "./DeeplIcon";
import {RequestAssistedTranslationForTreeComponent} from "./RequestAssistedTranslationForTreeComponent";

export default () => {
    registry.add('action', 'requestTranslationAiAssistedForOneContent', {
        buttonIcon: <DeeplIcon/>,
        buttonLabel: 'ai-assisted-translations:label.actionAllProperties',
        targets: ['translate/header/3dots:5.5','content-editor/header/3dots:5.5'],
        component: RequestAssistedTranslationComponent
    });

    // Register a component to translate a page or a jmix:mainResource
    registry.add('action', 'requestTranslationAiAssistedForOnePage', {
        buttonIcon: <DeeplIcon/>,
        buttonLabel: 'ai-assisted-translations:label.actionAllProperties',
        targets: ['headerPrimaryActions:15'],
        component: RequestAssistedTranslationForTreeComponent
    });
};
