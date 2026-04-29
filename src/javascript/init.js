import {registry} from '@jahia/ui-extender';
import register from './RequestAssistedTranslation/register';

export default function () {
    registry.add('callback', 'requestTranslationAiAssisted', {
        targets: ['jahiaApp-init:50'],
        callback: register
    });
}

console.debug('%c AI-assisted translation is activated', 'color: #3c8cba');
