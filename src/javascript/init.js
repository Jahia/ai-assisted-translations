import {registry} from '@jahia/ui-extender';
import register from './RequestAssistedTranslation/register';

export default function () {
    registry.add('callback', 'requestTranslationDeepl', {
        targets: ['jahiaApp-init:50'],
        callback: register
    });
}

console.debug('%c DeepL translation is activated', 'color: #3c8cba');
