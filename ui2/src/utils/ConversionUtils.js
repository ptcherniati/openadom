export function convertReferencesToTrees(initialReference, locale) {
    const references = JSON.parse(JSON.stringify(initialReference));
    const parents = references.filter((ref) => {
        return !references.some(
            (r) => r.children && r.children.length !== 0 && r.children.some((c) => c === ref.id)
        );
    });
    return replaceChildrenIdByObject(parents, references, locale);
}

function replaceChildrenIdByObject(references, initialRef, locale) {
    references.forEach((ref) => {
        if (ref.children && ref.children.length !== 0) {
            const children = ref.children.map((c) => {
                const index = initialRef.findIndex((r) => r.id === c);
                const [child] = initialRef.splice(index, 1);
                return child;
            });
            ref.children = replaceChildrenIdByObject(children, initialRef, locale);
        } else {
            if (ref && ref.internationalizationName) {
                return {...ref, localName: ref.internationalizationName[locale] || ref.name };
            }
            return ref;
        };
    });
    return references.map((ref) => {
        if (ref && ref.internationalizationName) {
            return {...ref, localName: ref.internationalizationName[locale] || ref.name };
        }
        return ref;
    });
}