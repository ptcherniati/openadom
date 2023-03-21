export function convertReferencesToTrees(initialValue) {
  const value = JSON.parse(JSON.stringify(initialValue));
  const parents = value.filter((ref) => {
    return !value.some(
      (r) => r.children && r.children.length !== 0 && r.children.some((c) => c === ref.id)
    );
  });
  return replaceChildrenIdByObject(parents, value);
}

function replaceChildrenIdByObject(references, initialRef) {
  references.forEach((ref) => {
    if (ref.children && ref.children.length !== 0) {
      const children = ref.children.map((c) => {
        const index = initialRef.findIndex((r) => r.id === c);
        const [child] = initialRef.splice(index, 1);
        return child;
      });
      ref.children = replaceChildrenIdByObject(children, initialRef);
    } else {
      if (ref && ref.internationalizationName) {
        return { ...ref, localName: ref.refNameLocal || ref.name };
      }
      return ref;
    }
  });
  return references.map((ref) => {
    if (ref && ref.refNameLocal) {
      return { ...ref, localName: ref.refNameLocal || ref.name };
    }
    return ref;
  });
}
