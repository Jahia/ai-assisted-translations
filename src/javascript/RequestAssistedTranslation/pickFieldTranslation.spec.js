import {pickFieldTranslation} from './pickFieldTranslation';

describe('picking one field out of the suggestions for a node', () => {
    const suggestions = [
        {fieldName: 'title', translatedValue: 'Bonjour'},
        {fieldName: 'text', translatedValue: 'Salut'},
        {fieldName: 'tags___1___', translatedValue: 'deux'},
        {fieldName: 'tags___0___', translatedValue: 'un'}
    ];

    it('returns the value of a single field', () => {
        expect(pickFieldTranslation(suggestions, 'text', false)).toBe('Salut');
    });

    it('returns undefined when there is no suggestion, so the row is left alone', () => {
        expect(pickFieldTranslation(suggestions, 'missing', false)).toBeUndefined();
        expect(pickFieldTranslation(undefined, 'text', false)).toBeUndefined();
    });

    it('does not mistake an empty translation for no translation', () => {
        expect(pickFieldTranslation([{fieldName: 'text', translatedValue: ''}], 'text', false)).toBe('');
    });

    it('rebuilds a multiple field in index order', () => {
        expect(pickFieldTranslation(suggestions, 'tags', true)).toEqual(['un', 'deux']);
    });

    it('does not pick another field that only starts with the same name', () => {
        expect(pickFieldTranslation([{fieldName: 'tagsExtra___0___', translatedValue: 'x'}], 'tags', true)).toBeUndefined();
    });
});
