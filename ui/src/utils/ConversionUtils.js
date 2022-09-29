export function convertReferencesToTrees(initialAdditionalFiles) {
  const additionalFiles = JSON.parse(JSON.stringify(initialAdditionalFiles));
  const parents = additionalFiles.filter((ref) => {
    return !additionalFiles.some(
      (r) => r.children && r.children.length !== 0 && r.children.some((c) => c === ref.id)
    );
  });
  return replaceChildrenIdByObject(parents, additionalFiles);
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