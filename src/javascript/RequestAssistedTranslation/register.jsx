import React from "react";
import {registry} from "@jahia/ui-extender";

import {
    RequestAssistedTranslationComponent
} from "./RequestAssistedTranslationComponent";
import DeeplIcon from "./DeeplIcon";

export default () => {
    registry.add('action', 'requestTranslationDeeplForAllLanguages', {
        buttonIcon: <DeeplIcon/>,
        buttonLabel: 'translation-deepl:label.actionAllProperties',
        targets: ['translate/header/3dots:5.5'],
        component: RequestAssistedTranslationComponent
    });
};
