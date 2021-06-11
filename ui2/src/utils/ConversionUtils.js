export function convertReferencesToTrees(initialReference) {
  const references = JSON.parse(JSON.stringify(initialReference));
  const parents = references.filter((ref) => {
    return !references.some(
      (r) => r.children && r.children.length !== 0 && r.children.some((c) => c === ref.id)
    );
  });
  return replaceChildrenIdByObject(parents, references);
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
      return ref;
    }
  });
  return references;
}
