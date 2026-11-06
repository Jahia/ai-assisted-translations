import React from 'react';
import {registry} from '@jahia/ui-extender';
import {RequestAssistedTranslationComponent} from './RequestAssistedTranslationComponent';
import DeeplIcon from './DeeplIcon';
import {RequestAssistedTranslationForTreeComponent} from './RequestAssistedTranslationForTreeComponent';
import {RequestAssistedTranslationForFieldRowComponent} from './RequestAssistedTranslationForFieldRowComponent';

// Moonstone's Button clones its icon with a size of its own ("default"), which would override any size
// set on the element. A wrapper takes that prop and ignores it, so the row icon stays as large as the
// 24px copy icon next to it.
const RowDeeplIcon = () => <DeeplIcon size={24}/>;

export default () => {
    registry.add('action', 'requestTranslationAiAssistedForOneContent', {
        buttonIcon: <DeeplIcon/>,
        buttonLabel: 'ai-assisted-translations:label.actionAllProperties',
        targets: ['translate/header/3dots:5.5', 'content-editor/header/3dots:5.5'],
        component: RequestAssistedTranslationComponent
    });

    // Register a component to translate a page or a jmix:mainResource
    registry.add('action', 'requestTranslationAiAssistedForOnePage', {
        buttonIcon: <DeeplIcon/>,
        buttonLabel: 'ai-assisted-translations:label.actionAllProperties',
        targets: ['headerPrimaryActions:15'],
        component: RequestAssistedTranslationForTreeComponent
    });

    // One field in one language, from a row of the "Edit in all languages" dialog of the
    // edit-field-all-languages module. The target is only a string: nothing is drawn when that module
    // is not installed.
    registry.add('action', 'requestTranslationAiAssistedForOneFieldRow', {
        buttonIcon: <RowDeeplIcon/>,
        buttonLabel: 'ai-assisted-translations:label.action',
        targets: ['edit-field-all-languages/row:10'],
        component: RequestAssistedTranslationForFieldRowComponent
    });
};
