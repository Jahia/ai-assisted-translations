// A suggestion for a multiple field arrives as one entry per value, named `<property>___<index>___`.
const INDEXED = /^(.+)___(\d+)___$/;

/**
 * The suggested value of one property, out of the suggestions for a whole node.
 *
 * Returns undefined when the service had nothing for it, which a caller must not confuse with an
 * empty translation: undefined leaves the row as it is.
 */
export const pickFieldTranslation = (suggestions, propertyName, multiple) => {
    if (!multiple) {
        return (suggestions || []).find(suggestion => suggestion.fieldName === propertyName)?.translatedValue;
    }

    const values = [];
    (suggestions || []).forEach(suggestion => {
        const match = INDEXED.exec(suggestion.fieldName);
        if (match && match[1] === propertyName) {
            values[Number(match[2])] = suggestion.translatedValue;
        }
    });

    return values.length > 0 ? values : undefined;
};
